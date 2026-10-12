package dev.harshil.shortener.service;

import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.repository.LinkRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs exactly one allocation attempt in its own transaction (ADR 0003, design 3.6).
 *
 * <p>This is a separate bean from {@code LinkService} on purpose: Spring's transaction proxy only
 * wraps calls that cross a bean boundary, so putting the retry loop and the insert in one class
 * would run every attempt inside one transaction, and the first constraint violation would mark it
 * rollback-only and poison the retries. Here each call commits or rolls back on its own, and the
 * {@code DataIntegrityViolationException} escapes the proxy to the caller, which decides whether to
 * retry (random code) or report a conflict (custom alias).
 */
@Service
public class LinkAllocator {

  private final LinkRepository links;

  /**
   * Creates the allocator.
   *
   * @param links link persistence
   */
  public LinkAllocator(LinkRepository links) {
    this.links = links;
  }

  /**
   * Inserts one link and commits.
   *
   * @param code the code to claim
   * @param url the canonical target URL
   * @param now the creation instant
   * @param expiresAt the expiry instant, already validated by the caller, or {@code null} for a
   *     link that never expires
   * @return the persisted link with its identifier
   * @throws org.springframework.dao.DataIntegrityViolationException when {@code code} is already
   *     stored; the message names the {@code uk_links_code} constraint
   */
  @Transactional
  public Link insert(String code, String url, Instant now, Instant expiresAt) {
    return links.saveAndFlush(new Link(code, url, now, expiresAt));
  }
}
