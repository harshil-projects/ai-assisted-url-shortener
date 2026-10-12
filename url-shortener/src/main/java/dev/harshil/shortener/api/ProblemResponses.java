package dev.harshil.shortener.api;

import dev.harshil.shortener.domain.UrlValidationException.FieldError;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;

/**
 * Builds and re-shapes the RFC 9457 bodies the controller advice returns (design 3.4, refactor
 * T-104). {@link ApiExceptionHandler} keeps the mapping from exception to problem type; this class
 * owns everything that is about the body itself: wrapping a problem in a {@code
 * application/problem+json} response, the two shared bodies for validation and unknown paths, and
 * the standardisation of bodies the framework built so they carry {@code requestId} and {@code
 * timestamp} like every other error.
 */
final class ProblemResponses {

  static final String ERRORS = "errors";
  static final String VALIDATION_TITLE = "Request validation failed";
  static final String VALIDATION_DETAIL = "One or more fields are invalid";
  static final String NO_RESOURCE = "No resource at this path";

  private ProblemResponses() {}

  /** Wraps a problem in a response with its own status and the problem content type. */
  static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
    return respond(problem, new HttpHeaders());
  }

  /**
   * Same as {@link #respond(ProblemDetail)} with extra headers, for example {@code Retry-After}.
   */
  static ResponseEntity<ProblemDetail> respond(ProblemDetail problem, HttpHeaders headers) {
    return ResponseEntity.status(problem.getStatus())
        .headers(headers)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  /** The 400 body shared by Bean Validation and the URL rules; {@code errors} lists the fields. */
  static ProblemDetail validationFailed(List<FieldError> errors) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.BAD_REQUEST,
            ProblemTypes.VALIDATION_FAILED,
            VALIDATION_TITLE,
            VALIDATION_DETAIL);
    problem.setProperty(ERRORS, errors);
    return problem;
  }

  /**
   * The 404 body for a path no controller serves, deliberately the same type as an unknown code.
   */
  static ProblemDetail noResource(HttpStatusCode status) {
    return ProblemDetails.of(status, ProblemTypes.LINK_NOT_FOUND, "Link not found", NO_RESOURCE);
  }

  /**
   * The body the framework attached to an exception that was not mapped explicitly, or a bare
   * problem for the status when the exception carries none.
   */
  static ProblemDetail frameworkBody(Exception ex, HttpStatusCode status) {
    return ex instanceof ErrorResponse response
        ? response.getBody()
        : ProblemDetail.forStatus(status);
  }

  /** Returns the body unchanged when it already has both members, otherwise a rebuilt copy. */
  static ProblemDetail standardised(ProblemDetail problem, HttpStatusCode status) {
    Map<String, Object> properties = problem.getProperties();
    if (properties != null
        && properties.containsKey(ProblemDetails.REQUEST_ID)
        && properties.containsKey(ProblemDetails.TIMESTAMP)) {
      return problem;
    }
    URI type = problem.getType() == null ? ProblemTypes.ABOUT_BLANK : problem.getType();
    ProblemDetail standard =
        ProblemDetails.of(status, type, problem.getTitle(), problem.getDetail());
    standard.setInstance(problem.getInstance());
    if (properties != null) {
      properties.forEach(standard::setProperty);
    }
    return standard;
  }
}
