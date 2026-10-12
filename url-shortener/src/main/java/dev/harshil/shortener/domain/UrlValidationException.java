package dev.harshil.shortener.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Raised when a value in a create request breaks a business rule that Bean Validation cannot
 * express: the URL rules of {@code UrlValidator} and the reserved-alias rule. It carries the same
 * {@code (field, message)} pairs the API returns for Bean Validation failures, so both paths render
 * as one {@code validation-failed} problem with an {@code errors} list (design 3.4).
 *
 * <p>Messages are fixed, human-readable sentences chosen by the validator; they never echo the
 * rejected value, so the exception is safe to log and to return to clients.
 */
public final class UrlValidationException extends RuntimeException {

  private final List<FieldError> errors;

  /**
   * Creates the exception from one or more field errors.
   *
   * @param errors the violations, at least one; copied defensively
   */
  public UrlValidationException(List<FieldError> errors) {
    super(summarise(errors));
    this.errors = List.copyOf(errors);
  }

  /**
   * Convenience for the common single-violation case.
   *
   * @param field the request field at fault, for example {@code url} or {@code customAlias}
   * @param message the fixed, safe explanation
   * @return the exception
   */
  public static UrlValidationException of(String field, String message) {
    return new UrlValidationException(List.of(new FieldError(field, message)));
  }

  /** Returns the violations in the order they were detected; the list is immutable. */
  public List<FieldError> getErrors() {
    return errors;
  }

  private static String summarise(List<FieldError> errors) {
    return errors.stream()
        .map(error -> error.field() + ": " + error.message())
        .collect(Collectors.joining("; ", "Validation failed: ", ""));
  }

  /**
   * One violated rule.
   *
   * @param field the request field at fault
   * @param message the fixed, safe explanation
   */
  public record FieldError(String field, String message) {}
}
