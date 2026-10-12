package dev.harshil.shortener.service;

import dev.harshil.shortener.api.dto.CreateLinkRequest;
import dev.harshil.shortener.domain.AliasTakenException;
import dev.harshil.shortener.domain.CodeGenerationExhaustedException;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.domain.LogSanitizer;
import dev.harshil.shortener.domain.ReservedWords;
import dev.harshil.shortener.domain.UrlValidationException;
import dev.harshil.shortener.repository.LinkRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates, resolves and deletes links (design 3.6).
 *
 * <p>{@link #create} is deliberately not transactional: validation and the retry loop run outside
 * any transaction and each insert attempt is delegated to {@link LinkAllocator}, so a collision
 * costs one small rolled-back transaction rather than poisoning the request. The database's unique
 * constraint is the only arbiter of code uniqueness; there is no in-memory locking (design 3.10).
 */
@Service
public class LinkService {

  /** Random allocation attempts before the request is reported as a server fault. */
  static final int MAX_ATTEMPTS = 5;

  static final String ALIAS_FIELD = "customAlias";
  static final String MSG_ALIAS_RESERVED = "Alias is reserved";
  static final String MSG_ALIAS_SHAPE =
      "Alias must be 4 to 32 characters of letters, digits, underscore or hyphen";
  static final String LINKS_CREATED_METRIC = "shortener.links.created";

  private static final Logger log = LoggerFactory.getLogger(LinkService.class);
  private static final String CODE_CONSTRAINT = "uk_links_code";
  private static final Pattern ALIAS = Pattern.compile(CreateLinkRequest.ALIAS_PATTERN);

  private final LinkRepository links;
  private final LinkAllocator allocator;
  private final CodeGenerator generator;
  private final UrlValidator urlValidator;
  private final Clock clock;
  private final Counter linksCreated;

  /**
   * Creates the service.
   *
   * @param links link persistence
   * @param allocator one-transaction-per-attempt inserter
   * @param generator random code source
   * @param urlValidator URL rules
   * @param clock application clock
   * @param registry metrics registry for {@code shortener.links.created}
   */
  public LinkService(
      LinkRepository links,
      LinkAllocator allocator,
      CodeGenerator generator,
      UrlValidator urlValidator,
      Clock clock,
      MeterRegistry registry) {
    this.links = links;
    this.allocator = allocator;
    this.generator = generator;
    this.urlValidator = urlValidator;
    this.clock = clock;
    this.linksCreated = registry.counter(LINKS_CREATED_METRIC);
  }

  /**
   * Validates the request and stores a new link.
   *
   * @param request the client's URL and optional alias, already past Bean Validation
   * @return the stored link
   * @throws UrlValidationException when the URL breaks a rule or the alias is reserved
   * @throws AliasTakenException when the alias is already a stored code
   * @throws CodeGenerationExhaustedException when {@value #MAX_ATTEMPTS} random codes collided
   */
  public Link create(CreateLinkRequest request) {
    String url = urlValidator.validate(request.url());
    Instant now = clock.instant();
    Link link =
        request.hasCustomAlias()
            ? allocateAlias(request.customAlias(), url, now)
            : allocateRandom(url, now);
    linksCreated.increment();
    log.info(
        "Created link code={} host={}",
        LogSanitizer.sanitize(link.getCode()),
        LogSanitizer.sanitize(URI.create(url).getHost()));
    return link;
  }

  /**
   * Finds the link a redirect should follow.
   *
   * @param code the code from the path
   * @return the link
   * @throws LinkNotFoundException when no link has that code
   */
  public Link resolve(String code) {
    return links.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
  }

  /**
   * Finds a link for the management API. Today this is the same lookup as {@link #resolve}; the two
   * names exist so the redirect path can later diverge (for example, to report expiry) without
   * changing how metadata is read.
   *
   * @param code the code from the path
   * @return the link
   * @throws LinkNotFoundException when no link has that code
   */
  public Link get(String code) {
    return resolve(code);
  }

  /**
   * Hard-deletes a link; the database cascade removes its click events (ADR 0005).
   *
   * @param code the code from the path
   * @throws LinkNotFoundException when nothing was deleted
   */
  @Transactional
  public void delete(String code) {
    if (links.deleteByCode(code) == 0) {
      throw new LinkNotFoundException(code);
    }
  }

  private Link allocateAlias(String alias, String url, Instant now) {
    if (!ALIAS.matcher(alias).matches()) {
      throw UrlValidationException.of(ALIAS_FIELD, MSG_ALIAS_SHAPE);
    }
    if (ReservedWords.isReserved(alias)) {
      throw UrlValidationException.of(ALIAS_FIELD, MSG_ALIAS_RESERVED);
    }
    if (links.existsByCode(alias)) {
      throw new AliasTakenException(alias);
    }
    try {
      return allocator.insert(alias, url, now);
    } catch (DataIntegrityViolationException e) {
      if (isCodeCollision(e)) {
        throw new AliasTakenException(alias, e);
      }
      throw e;
    }
  }

  private Link allocateRandom(String url, Instant now) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      String code = generator.next();
      try {
        return allocator.insert(code, url, now);
      } catch (DataIntegrityViolationException e) {
        if (!isCodeCollision(e)) {
          throw e;
        }
        log.warn("Random code collided on attempt {} of {}", attempt, MAX_ATTEMPTS);
      }
    }
    throw new CodeGenerationExhaustedException(MAX_ATTEMPTS);
  }

  /**
   * Only a violation of the code's unique constraint is a collision; any other integrity failure is
   * a genuine fault and must not be retried or disguised as a conflict.
   */
  private static boolean isCodeCollision(DataIntegrityViolationException e) {
    return mentionsCodeConstraint(e.getMessage())
        || mentionsCodeConstraint(e.getMostSpecificCause().getMessage());
  }

  private static boolean mentionsCodeConstraint(String message) {
    return message != null && message.toLowerCase(Locale.ROOT).contains(CODE_CONSTRAINT);
  }
}
