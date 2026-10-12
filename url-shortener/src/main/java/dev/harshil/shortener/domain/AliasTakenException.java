package dev.harshil.shortener.domain;

/**
 * Raised when a requested custom alias is already a stored code, whether the fast-path existence
 * check saw it or two requests raced and the unique constraint decided (ADR 0003). Mapped to the
 * 409 {@code alias-taken} problem (design 3.4).
 */
public final class AliasTakenException extends RuntimeException {

  private final String alias;

  /**
   * Creates the exception when the existence check found the alias.
   *
   * @param alias the alias the client asked for; it has already passed the alias pattern
   */
  public AliasTakenException(String alias) {
    this(alias, null);
  }

  /**
   * Creates the exception when the database constraint rejected the insert, keeping the original
   * failure as the cause for diagnostics.
   *
   * @param alias the alias the client asked for; it has already passed the alias pattern
   * @param cause the constraint violation, or {@code null}
   */
  public AliasTakenException(String alias, Throwable cause) {
    super("Alias already in use: " + LogSanitizer.sanitize(alias), cause);
    this.alias = alias;
  }

  public String getAlias() {
    return alias;
  }
}
