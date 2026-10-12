package dev.harshil.shortener.domain;

/**
 * Prepares user-controlled text for a log statement (CWE-117, design 3.7).
 *
 * <p>A client can put a carriage return or line feed in a path, header or JSON field and forge a
 * second log line, or hide a real one behind a terminal control sequence. This utility removes
 * every ISO control character, which covers CR, LF, tab, escape and the C1 range, and caps the
 * length so a multi-kilobyte value cannot flood the log. It is the only route by which
 * request-derived values reach a logger in this code base.
 */
public final class LogSanitizer {

  /** Longest value a single log line keeps; enough for a code or a host, not for a payload. */
  static final int MAX_LENGTH = 200;

  private LogSanitizer() {}

  /**
   * Returns the value with control characters removed and at most {@link #MAX_LENGTH} characters.
   *
   * @param value any text, possibly from a request; {@code null} is rendered as the literal {@code
   *     "null"} so a log line never throws
   * @return log-safe text, never {@code null}
   */
  public static String sanitize(String value) {
    if (value == null) {
      return "null";
    }
    StringBuilder safe = new StringBuilder(Math.min(value.length(), MAX_LENGTH));
    for (int i = 0; i < value.length() && safe.length() < MAX_LENGTH; i++) {
      char current = value.charAt(i);
      if (!Character.isISOControl(current)) {
        safe.append(current);
      }
    }
    return safe.toString();
  }
}
