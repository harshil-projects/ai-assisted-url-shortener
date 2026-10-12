package dev.harshil.shortener.api;

import dev.harshil.shortener.domain.ClickAttribution;
import dev.harshil.shortener.domain.ClickRecorded;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.domain.LinkExpiredException;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.service.LinkService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public redirect at {@code GET /{code}} (design 3.6, ADR 0004).
 *
 * <p>One indexed lookup, then a 302 to the URL that was validated when the link was created; the
 * request never influences the target. {@code Cache-Control: no-store} is set here explicitly so
 * the intent is visible in code and tests, and because a cached redirect would hide deletions and
 * lose clicks. The click is published as an immutable event before the response is built and the
 * request is not read again afterwards; the asynchronous recorder owns the rest. {@code HEAD} is
 * mapped separately so it resolves and reports the same status without counting a click.
 *
 * <p>An expired link is refused by the service with {@link LinkExpiredException}; it is counted
 * here as {@code outcome=expired}, no click is published, and the handler answers 410 with {@code
 * Cache-Control: no-store} (design 3.6, brownfield). {@code HEAD} mirrors the 410.
 *
 * <p>Attribution (R-03): the host of the {@code Referer} header and the {@code utm_*} query
 * parameters of the short link request are reduced to a {@link ClickAttribution} here, on the
 * request thread, and travel inside the event. Query parameters are recorded, never forwarded: the
 * {@code Location} is the stored URL exactly.
 *
 * <p>The path pattern admits only strings that could be codes, so browser noise such as {@code
 * /favicon.ico} is answered by the framework's 404 without a database call.
 */
@RestController
public class RedirectController {

  /** Metric counting resolve outcomes for redirect requests, GET and HEAD alike. */
  static final String REDIRECTS_METRIC = "shortener.redirects";

  static final String OUTCOME_TAG = "outcome";
  static final String OUTCOME_HIT = "hit";
  static final String OUTCOME_MISS = "miss";
  static final String OUTCOME_EXPIRED = "expired";
  static final String CODE_PATH = "/{code:[A-Za-z0-9_-]{4,32}}";

  private final LinkService links;
  private final ApplicationEventPublisher events;
  private final Clock clock;
  private final Counter hits;
  private final Counter misses;
  private final Counter expired;

  /**
   * Creates the controller.
   *
   * @param links link lookups
   * @param events publisher that hands clicks to the recorder
   * @param clock application clock for the click instant
   * @param registry metrics registry for {@code shortener.redirects}
   */
  public RedirectController(
      LinkService links, ApplicationEventPublisher events, Clock clock, MeterRegistry registry) {
    this.links = links;
    this.events = events;
    this.clock = clock;
    this.hits = registry.counter(REDIRECTS_METRIC, OUTCOME_TAG, OUTCOME_HIT);
    this.misses = registry.counter(REDIRECTS_METRIC, OUTCOME_TAG, OUTCOME_MISS);
    this.expired = registry.counter(REDIRECTS_METRIC, OUTCOME_TAG, OUTCOME_EXPIRED);
  }

  /**
   * Redirects to the stored URL and records the click with its attribution.
   *
   * @param code the short code
   * @param request the current request; only its {@code Referer} header and parameter map are read
   * @return 302 with {@code Location} and {@code Cache-Control: no-store}
   */
  @Operation(
      summary = "Follow a short link",
      security = {})
  @ApiResponse(
      responseCode = "302",
      description = "Redirect to the stored URL; Cache-Control is no-store",
      content = @Content)
  @ResponseStatus(HttpStatus.FOUND)
  @GetMapping(CODE_PATH)
  public ResponseEntity<Void> redirect(@PathVariable String code, HttpServletRequest request) {
    Link link = resolveCounted(code);
    ClickAttribution attribution =
        ClickAttribution.from(request.getHeader(HttpHeaders.REFERER), request.getParameterMap());
    events.publishEvent(
        new ClickRecorded(
            link.getId(), clock.instant(), MDC.get(ProblemDetails.REQUEST_ID), attribution));
    return redirectTo(link);
  }

  /**
   * Answers a {@code HEAD} probe with the same status and headers as the redirect, without counting
   * a click.
   *
   * @param code the short code
   * @return 302 with {@code Location} and {@code Cache-Control: no-store}
   */
  @Operation(
      summary = "Probe a short link without recording a click",
      security = {})
  @ApiResponse(responseCode = "302", description = "Same as GET", content = @Content)
  @ResponseStatus(HttpStatus.FOUND)
  @RequestMapping(path = CODE_PATH, method = RequestMethod.HEAD)
  public ResponseEntity<Void> probe(@PathVariable String code) {
    return redirectTo(resolveCounted(code));
  }

  private Link resolveCounted(String code) {
    try {
      Link link = links.resolve(code);
      hits.increment();
      return link;
    } catch (LinkNotFoundException e) {
      misses.increment();
      throw e;
    } catch (LinkExpiredException e) {
      expired.increment();
      throw e;
    }
  }

  private static ResponseEntity<Void> redirectTo(Link link) {
    return ResponseEntity.status(HttpStatus.FOUND)
        .location(URI.create(link.getOriginalUrl()))
        .cacheControl(CacheControl.noStore())
        .build();
  }
}
