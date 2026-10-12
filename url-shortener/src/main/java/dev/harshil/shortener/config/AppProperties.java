package dev.harshil.shortener.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Application settings bound from the {@code app.*} properties.
 *
 * <p>Validation runs at startup, so a production deployment without {@code APP_API_KEY} fails fast
 * instead of running with an empty key (ADR 0007).
 *
 * @param apiKey the shared API key clients must present in {@code X-API-Key}
 * @param baseUrl optional public base URL used to build {@code shortUrl}; empty means "use the
 *     current request's scheme, host and port"
 * @param ratelimit create-endpoint rate limit settings
 * @param request request size settings
 * @param security security toggles
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(
    @NotBlank String apiKey,
    String baseUrl,
    @Valid @NotNull RateLimit ratelimit,
    @Valid @NotNull Request request,
    @Valid @NotNull Security security) {

  /** Returns true when a public base URL has been configured. */
  public boolean hasBaseUrl() {
    return baseUrl != null && !baseUrl.isBlank();
  }

  /**
   * Rate limiting settings.
   *
   * @param create the limit applied to {@code POST /api/v1/links}
   */
  public record RateLimit(@Valid @NotNull Create create) {

    /**
     * Token bucket for one client address.
     *
     * @param capacity tokens available per period
     * @param period refill period
     */
    public record Create(@Positive int capacity, @NotNull Duration period) {}
  }

  /**
   * Request body limits.
   *
   * @param maxBytes largest body accepted on {@code /api/v1/**}
   */
  public record Request(@Positive long maxBytes) {}

  /**
   * Security toggles.
   *
   * @param actuatorRequiresApiKey whether info, metrics and prometheus endpoints need the API key
   */
  public record Security(boolean actuatorRequiresApiKey) {}
}
