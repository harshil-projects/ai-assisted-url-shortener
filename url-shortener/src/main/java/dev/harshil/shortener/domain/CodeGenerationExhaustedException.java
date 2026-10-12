package dev.harshil.shortener.domain;

/**
 * Raised when every allocation attempt for a random code collided with an existing one. With a 62^7
 * code space this is astronomically unlikely unless the table is nearly full or the random source
 * is broken, so it is treated as a server fault: 500 {@code code-generation-exhausted} (design
 * 3.4), and the client may simply retry.
 */
public final class CodeGenerationExhaustedException extends RuntimeException {

  private final int attempts;

  /**
   * Creates the exception.
   *
   * @param attempts how many codes were tried before giving up
   */
  public CodeGenerationExhaustedException(int attempts) {
    super("Could not allocate a unique code after " + attempts + " attempts");
    this.attempts = attempts;
  }

  public int getAttempts() {
    return attempts;
  }
}
