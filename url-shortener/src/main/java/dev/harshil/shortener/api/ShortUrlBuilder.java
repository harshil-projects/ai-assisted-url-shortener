package dev.harshil.shortener.api;

import dev.harshil.shortener.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Builds the absolute short URL for a code (design 3.3).
 *
 * <p>Behind a reverse proxy or on a public domain the operator sets {@code app.base-url}; when it
 * is empty, which is the dev default, the URL is derived from the current request's scheme, host
 * and port, so a reviewer who starts the service on another port sees values that actually work.
 * Forwarded headers are deliberately not consulted ({@code server.forward-headers-strategy=NONE}):
 * trusting them is a deployment decision documented in the API reference.
 */
@Component
public final class ShortUrlBuilder {

  private final AppProperties properties;

  /**
   * Creates the builder.
   *
   * @param properties application settings providing the optional base URL
   */
  public ShortUrlBuilder(AppProperties properties) {
    this.properties = properties;
  }

  /**
   * Returns the absolute short URL for a code.
   *
   * @param code a stored code, already restricted to {@code [A-Za-z0-9_-]} so no encoding is needed
   * @return for example {@code https://sho.rt/abc1234}
   */
  public String shortUrl(String code) {
    if (properties.hasBaseUrl()) {
      return withoutTrailingSlash(properties.baseUrl().strip()) + "/" + code;
    }
    return ServletUriComponentsBuilder.fromCurrentContextPath()
        .path("/{code}")
        .buildAndExpand(code)
        .toUriString();
  }

  private static String withoutTrailingSlash(String baseUrl) {
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
