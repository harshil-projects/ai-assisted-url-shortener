package dev.harshil.shortener.api.dto;

import dev.harshil.shortener.service.UrlValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Body of {@code POST /api/v1/links} (design 3.3).
 *
 * <p>Bean Validation covers presence, length and the alias shape, so a malformed request is
 * rejected before any service code runs; the URL rules that need parsing live in {@link
 * UrlValidator}, and the reserved-alias and future-expiry rules in {@code LinkService}, because
 * they produce domain-specific messages and the expiry check needs the application clock.
 * Components are reference types so an absent member is a clear validation error rather than a
 * generic parse failure (design 4.3).
 *
 * @param url the target URL, absolute {@code http} or {@code https}, at most 2048 characters
 * @param customAlias optional caller-chosen code, 4 to 32 characters of {@code [A-Za-z0-9_-]};
 *     {@code null} asks the service to generate one
 * @param expiresAt optional absolute expiry as an RFC 3339 timestamp with an offset; Jackson parses
 *     it into an {@code Instant}, so a zoneless string fails parsing and is answered with the
 *     generic 400 for an unreadable body; {@code null} means the link never expires
 */
public record CreateLinkRequest(
    @NotBlank(message = "URL is required")
        @Size(max = UrlValidator.MAX_LENGTH, message = "URL must be at most 2048 characters")
        String url,
    @Pattern(
            regexp = CreateLinkRequest.ALIAS_PATTERN,
            message = "Alias must be 4 to 32 characters of letters, digits, underscore or hyphen")
        String customAlias,
    Instant expiresAt) {

  /** Shape of an acceptable alias; also the path pattern every code endpoint uses. */
  public static final String ALIAS_PATTERN = "^[A-Za-z0-9_-]{4,32}$";

  /**
   * Tells whether the caller chose an alias.
   *
   * @return true when {@code customAlias} is present and non-empty
   */
  public boolean hasCustomAlias() {
    return customAlias != null && !customAlias.isEmpty();
  }
}
