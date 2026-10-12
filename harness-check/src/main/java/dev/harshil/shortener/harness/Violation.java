package dev.harshil.shortener.harness;

/**
 * One harness-check finding: the rule that was violated, the subject it concerns, and why.
 *
 * @param rule the rule id, for example {@code HC-01}
 * @param subject the offending task id, file path, or commit
 * @param message a human-readable explanation of the violation
 */
public record Violation(String rule, String subject, String message) {

  @Override
  public String toString() {
    return rule + " [" + subject + "] " + message;
  }
}
