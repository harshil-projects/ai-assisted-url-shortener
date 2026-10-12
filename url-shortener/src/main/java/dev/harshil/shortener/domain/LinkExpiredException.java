package dev.harshil.shortener.domain;

import java.time.Instant;

/**
 * Raised when a redirect is asked for a link whose {@code expiresAt} has passed. Mapped to the 410
 * {@code link-expired} problem (design 3.4) by the API layer. Only the redirect path throws it: the
 * management API keeps returning expired links so their metadata and statistics stay readable
 * (design 3.6).
 */
public final class LinkExpiredException extends RuntimeException {

  private final String code;
  private final Instant expiresAt;

  /**
   * Creates the exception for a link that was found but has expired.
   *
   * @param code the code the client presented; it has already passed the path pattern, and it is
   *     sanitised again here so the message stays safe for any log line
   * @param expiresAt the instant the link expired
   */
  public LinkExpiredException(String code, Instant expiresAt) {
    super("Link " + LogSanitizer.sanitize(code) + " expired at " + expiresAt);
    this.code = code;
    this.expiresAt = expiresAt;
  }

  public String getCode() {
    return code;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
