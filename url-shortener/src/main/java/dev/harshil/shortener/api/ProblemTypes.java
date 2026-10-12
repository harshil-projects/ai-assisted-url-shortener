package dev.harshil.shortener.api;

import java.net.URI;

/**
 * Problem type identifiers for RFC 9457 error bodies (design section 3.4).
 *
 * <p>Types are URI references relative to the service origin. They identify the problem class and
 * are not meant to be dereferenced; the catalogue lives in docs/reference/api.md.
 */
public final class ProblemTypes {

  public static final URI VALIDATION_FAILED = URI.create("/problems/validation-failed");
  public static final URI UNAUTHORIZED = URI.create("/problems/unauthorized");
  public static final URI LINK_NOT_FOUND = URI.create("/problems/link-not-found");
  public static final URI METHOD_NOT_ALLOWED = URI.create("/problems/method-not-allowed");
  public static final URI ALIAS_TAKEN = URI.create("/problems/alias-taken");
  public static final URI LINK_EXPIRED = URI.create("/problems/link-expired");
  public static final URI PAYLOAD_TOO_LARGE = URI.create("/problems/payload-too-large");
  public static final URI UNSUPPORTED_MEDIA_TYPE = URI.create("/problems/unsupported-media-type");
  public static final URI RATE_LIMITED = URI.create("/problems/rate-limited");
  public static final URI CODE_GENERATION_EXHAUSTED =
      URI.create("/problems/code-generation-exhausted");
  public static final URI SERVICE_UNAVAILABLE = URI.create("/problems/service-unavailable");

  /** Default type for unexpected errors; RFC 9457 reserves {@code about:blank}. */
  public static final URI ABOUT_BLANK = URI.create("about:blank");

  private ProblemTypes() {}
}
