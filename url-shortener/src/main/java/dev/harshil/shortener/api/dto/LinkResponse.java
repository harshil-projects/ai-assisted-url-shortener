package dev.harshil.shortener.api.dto;

import dev.harshil.shortener.domain.Link;
import java.time.Instant;

/**
 * Representation of a link returned by create and metadata requests (design 3.3).
 *
 * @param code the short code
 * @param shortUrl the absolute short URL a client can hand out, built from {@code app.base-url} or
 *     the current request
 * @param url the canonical target URL
 * @param createdAt when the link was created, UTC
 * @param expiresAt when the link stops redirecting, UTC, or {@code null} when it never expires
 * @param expired whether the link had expired at the moment this response was built
 */
public record LinkResponse(
    String code,
    String shortUrl,
    String url,
    Instant createdAt,
    Instant expiresAt,
    Boolean expired) {

  /**
   * Builds the response for a stored link.
   *
   * @param link the entity
   * @param shortUrl the absolute short URL for {@code link.getCode()}
   * @param now the current instant from the application clock, used to compute {@code expired}
   * @return the response body
   */
  public static LinkResponse of(Link link, String shortUrl, Instant now) {
    return new LinkResponse(
        link.getCode(),
        shortUrl,
        link.getOriginalUrl(),
        link.getCreatedAt(),
        link.getExpiresAt(),
        link.isExpired(now));
  }
}
