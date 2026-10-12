package dev.harshil.shortener.api;

import dev.harshil.shortener.domain.AliasTakenException;
import dev.harshil.shortener.domain.CodeGenerationExhaustedException;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.domain.LogSanitizer;
import dev.harshil.shortener.domain.UrlValidationException;
import dev.harshil.shortener.domain.UrlValidationException.FieldError;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * The single place that turns an exception into an RFC 9457 body for controller requests (design
 * 3.4). Each domain exception maps to exactly one problem type; framework exceptions are mapped by
 * overriding the {@link ResponseEntityExceptionHandler} hooks so the body shape, including {@code
 * requestId} and {@code timestamp}, is identical to the one the filters write through {@code
 * ProblemWriter}. Details are fixed sentences chosen here: a parser or driver message never reaches
 * a client, and the full exception is logged server-side with the request id instead.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  static final String ERRORS = "errors";
  static final String VALIDATION_TITLE = "Request validation failed";
  static final String VALIDATION_DETAIL = "One or more fields are invalid";
  static final String UNREADABLE = "Request body is missing or is not valid JSON";
  static final String NO_RESOURCE = "No resource at this path";
  static final String UNEXPECTED = "Unexpected error";
  static final String RETRY_AFTER_SECONDS = "5";

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /**
   * URL and alias rules: 400 with the field list.
   *
   * @param ex the violations
   * @return the problem response
   */
  @ExceptionHandler(UrlValidationException.class)
  public ResponseEntity<ProblemDetail> handleUrlValidation(UrlValidationException ex) {
    return respond(validationFailed(ex.getErrors()));
  }

  /**
   * Unknown code: 404.
   *
   * @param ex the lookup failure
   * @return the problem response
   */
  @ExceptionHandler(LinkNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleLinkNotFound(LinkNotFoundException ex) {
    return respond(
        ProblemDetails.of(
            HttpStatus.NOT_FOUND, ProblemTypes.LINK_NOT_FOUND, "Link not found", ex.getMessage()));
  }

  /**
   * Alias already stored, including a lost race on the unique constraint: 409.
   *
   * @param ex the conflict
   * @return the problem response
   */
  @ExceptionHandler(AliasTakenException.class)
  public ResponseEntity<ProblemDetail> handleAliasTaken(AliasTakenException ex) {
    return respond(
        ProblemDetails.of(
            HttpStatus.CONFLICT,
            ProblemTypes.ALIAS_TAKEN,
            "Alias already in use",
            ex.getMessage()));
  }

  /**
   * Five random collisions in a row: 500 with a dedicated type so operators can alert on it.
   *
   * @param ex the exhaustion
   * @return the problem response
   */
  @ExceptionHandler(CodeGenerationExhaustedException.class)
  public ResponseEntity<ProblemDetail> handleCodeGenerationExhausted(
      CodeGenerationExhaustedException ex) {
    log.error(
        "Code generation exhausted after {} attempts; requestId={}", ex.getAttempts(), requestId());
    return respond(
        ProblemDetails.of(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ProblemTypes.CODE_GENERATION_EXHAUSTED,
            "Could not allocate a unique code",
            "No unique code could be allocated; retry the request"));
  }

  /**
   * Database unreachable: 503 with {@code Retry-After} so well-behaved clients back off.
   *
   * @param ex the connection failure
   * @return the problem response
   */
  @ExceptionHandler({
    DataAccessResourceFailureException.class,
    CannotGetJdbcConnectionException.class
  })
  public ResponseEntity<ProblemDetail> handleDatabaseUnavailable(DataAccessException ex) {
    log.error("Database unavailable; requestId={}", requestId(), ex);
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS);
    return respond(
        ProblemDetails.of(
            HttpStatus.SERVICE_UNAVAILABLE,
            ProblemTypes.SERVICE_UNAVAILABLE,
            "Service temporarily unavailable",
            "The database is not reachable; retry in a few seconds"),
        headers);
  }

  /**
   * Anything not mapped above: 500 {@code about:blank} with a fixed detail. The exception, with its
   * stack trace, goes to the log together with the request id so the two can be correlated.
   *
   * @param ex the unexpected failure
   * @return the problem response
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
    log.error("Unhandled exception; requestId={}", requestId(), ex);
    return respond(
        ProblemDetails.of(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ProblemTypes.ABOUT_BLANK,
            "Internal error",
            UNEXPECTED));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<FieldError> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .sorted(Comparator.comparing(error -> error.getField()))
            .map(error -> new FieldError(error.getField(), messageOf(error.getDefaultMessage())))
            .toList();
    return handleExceptionInternal(ex, validationFailed(errors), headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetails.of(status, ProblemTypes.VALIDATION_FAILED, VALIDATION_TITLE, UNREADABLE);
    problem.setProperty(ERRORS, List.of());
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetails.of(
            status,
            ProblemTypes.UNSUPPORTED_MEDIA_TYPE,
            "Unsupported media type",
            "Request body must be application/json");
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
      HttpRequestMethodNotSupportedException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        ProblemDetails.of(
            status,
            ProblemTypes.METHOD_NOT_ALLOWED,
            "Method not allowed",
            "The HTTP method is not supported at this path");
    return handleExceptionInternal(ex, problem, headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleNoResourceFoundException(
      NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return handleExceptionInternal(ex, noResource(status), headers, status, request);
  }

  @Override
  protected ResponseEntity<Object> handleNoHandlerFoundException(
      NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    return handleExceptionInternal(ex, noResource(status), headers, status, request);
  }

  /**
   * Final hop for every framework exception. Bodies built here already carry the two standard
   * members; bodies built by the framework for exceptions not overridden above are rebuilt through
   * {@link ProblemDetails#of} so they get them too, and the content type is always {@code
   * application/problem+json}.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail problem =
        body instanceof ProblemDetail detail ? detail : frameworkBody(ex, status);
    HttpHeaders problemHeaders = HttpHeaders.copyOf(headers);
    problemHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    return super.handleExceptionInternal(
        ex, standardised(problem, status), problemHeaders, status, request);
  }

  private static ProblemDetail frameworkBody(Exception ex, HttpStatusCode status) {
    return ex instanceof ErrorResponse response
        ? response.getBody()
        : ProblemDetail.forStatus(status);
  }

  /** Returns the body unchanged when it already has both members, otherwise a rebuilt copy. */
  private static ProblemDetail standardised(ProblemDetail problem, HttpStatusCode status) {
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

  private static ProblemDetail noResource(HttpStatusCode status) {
    return ProblemDetails.of(status, ProblemTypes.LINK_NOT_FOUND, "Link not found", NO_RESOURCE);
  }

  private static ProblemDetail validationFailed(List<FieldError> errors) {
    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.BAD_REQUEST,
            ProblemTypes.VALIDATION_FAILED,
            VALIDATION_TITLE,
            VALIDATION_DETAIL);
    problem.setProperty(ERRORS, errors);
    return problem;
  }

  private static String messageOf(@Nullable String defaultMessage) {
    return defaultMessage == null ? "is invalid" : defaultMessage;
  }

  private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
    return respond(problem, new HttpHeaders());
  }

  private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem, HttpHeaders headers) {
    return ResponseEntity.status(problem.getStatus())
        .headers(headers)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private static String requestId() {
    return LogSanitizer.sanitize(MDC.get(ProblemDetails.REQUEST_ID));
  }
}
