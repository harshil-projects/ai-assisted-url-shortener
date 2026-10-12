package dev.harshil.shortener.domain;

/**
 * Raised when a code does not identify a stored link. Mapped to the 404 {@code link-not-found}
 * problem (design 3.4) by the API layer; the service layer throws it so callers never have to
 * handle an empty {@code Optional} and the redirect, metadata, stats and delete paths share one
 * outcome.
 */
public final class LinkNotFoundException extends RuntimeException {

  private final String code;

  /**
   * Creates the exception for a code that was looked up and not found.
   *
   * @param code the code the client presented; it has already passed the path pattern, and it is
   *     sanitised again here so the message stays safe for any log line
   */
  public LinkNotFoundException(String code) {
    super("No link with code " + LogSanitizer.sanitize(code));
    this.code = code;
  }

  public String getCode() {
    return code;
  }
}
