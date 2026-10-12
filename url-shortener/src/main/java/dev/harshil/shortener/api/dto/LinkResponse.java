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
 */
public record LinkResponse(String code, String shortUrl, String url, Instant createdAt) {

  /**
   * Builds the response for a stored link.
   *
   * @param link the entity
   * @param shortUrl the absolute short URL for {@code link.getCode()}
   * @return the response body
   */
  public static LinkResponse of(Link link, String shortUrl) {
    return new LinkResponse(link.getCode(), shortUrl, link.getOriginalUrl(), link.getCreatedAt());
  }
}
