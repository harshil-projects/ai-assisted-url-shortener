package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.api.dto.CreateLinkRequest;
import dev.harshil.shortener.domain.AliasTakenException;
import dev.harshil.shortener.domain.CodeGenerationExhaustedException;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.domain.UrlValidationException;
import java.net.URI;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class ApiExceptionHandlerTest {

  private static final String REQUEST_ID = "req-42";

  private final ApiExceptionHandler handler = new ApiExceptionHandler();
  private final WebRequest request =
      new ServletWebRequest(new MockHttpServletRequest("GET", "/api/v1/links/abcdefg"));

  @BeforeEach
  void putRequestIdInMdc() {
    MDC.put(ProblemDetails.REQUEST_ID, REQUEST_ID);
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void handleLinkNotFound_mapsTo404LinkNotFoundWithStandardMembersAndProblemContentType() {
    ResponseEntity<ProblemDetail> response =
        handler.handleLinkNotFound(new LinkNotFoundException("abcdefg"));

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertStandard(response.getBody(), ProblemTypes.LINK_NOT_FOUND, "Link not found");
    assertThat(response.getBody().getDetail()).isEqualTo("No link with code abcdefg");
  }

  @Test
  void handleAliasTaken_mapsTo409AliasTaken() {
    ResponseEntity<ProblemDetail> response =
        handler.handleAliasTaken(new AliasTakenException("my-alias"));

    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertStandard(response.getBody(), ProblemTypes.ALIAS_TAKEN, "Alias already in use");
    assertThat(response.getBody().getDetail()).contains("my-alias");
  }

  @Test
  void handleCodeGenerationExhausted_mapsTo500CodeGenerationExhausted() {
    ResponseEntity<ProblemDetail> response =
        handler.handleCodeGenerationExhausted(new CodeGenerationExhaustedException(5));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertStandard(
        response.getBody(),
        ProblemTypes.CODE_GENERATION_EXHAUSTED,
        "Could not allocate a unique code");
  }

  @Test
  void handleUrlValidation_mapsTo400ValidationFailedWithTheFieldErrors() {
    UrlValidationException ex = UrlValidationException.of("url", "URL is required");

    ResponseEntity<ProblemDetail> response = handler.handleUrlValidation(ex);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertStandard(
        response.getBody(), ProblemTypes.VALIDATION_FAILED, ApiExceptionHandler.VALIDATION_TITLE);
    assertThat(response.getBody().getProperties())
        .containsEntry(ApiExceptionHandler.ERRORS, ex.getErrors());
  }

  @ParameterizedTest
  @MethodSource("databaseFailures")
  void handleDatabaseUnavailable_mapsTo503ServiceUnavailableWithRetryAfterFive(
      DataAccessException failure) {
    ResponseEntity<ProblemDetail> response = handler.handleDatabaseUnavailable(failure);

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("5");
    assertStandard(
        response.getBody(), ProblemTypes.SERVICE_UNAVAILABLE, "Service temporarily unavailable");
    assertThat(response.getBody().getDetail()).doesNotContain("connection refused");
  }

  static Stream<Arguments> databaseFailures() {
    return Stream.of(
        Arguments.of(new DataAccessResourceFailureException("connection refused")),
        Arguments.of(new CannotGetJdbcConnectionException("connection refused")));
  }

  @Test
  void handleUnexpected_mapsTo500AboutBlankWithFixedDetailAndNoExceptionText() {
    ResponseEntity<ProblemDetail> response =
        handler.handleUnexpected(new IllegalStateException("secret internal state"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertStandard(response.getBody(), ProblemTypes.ABOUT_BLANK, "Internal error");
    assertThat(response.getBody().getDetail()).isEqualTo(ApiExceptionHandler.UNEXPECTED);
    assertThat(response.getBody().toString()).doesNotContain("secret internal state");
  }

  @Test
  void handleException_mediaTypeNotSupported_mapsTo415UnsupportedMediaType() throws Exception {
    HttpMediaTypeNotSupportedException ex =
        new HttpMediaTypeNotSupportedException(
            MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

    ResponseEntity<Object> response = handler.handleException(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(415);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertStandard(
        problem(response), ProblemTypes.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type");
  }

  @Test
  void handleException_methodNotSupported_mapsTo405AndKeepsTheAllowHeader() throws Exception {
    HttpRequestMethodNotSupportedException ex =
        new HttpRequestMethodNotSupportedException("PATCH", List.of("GET", "DELETE"));

    ResponseEntity<Object> response = handler.handleException(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(405);
    assertThat(response.getHeaders().getAllow())
        .containsExactlyInAnyOrder(HttpMethod.GET, HttpMethod.DELETE);
    assertStandard(problem(response), ProblemTypes.METHOD_NOT_ALLOWED, "Method not allowed");
  }

  @Test
  void handleException_noResourceFound_mapsTo404LinkNotFoundWithFixedDetail() throws Exception {
    NoResourceFoundException ex = new NoResourceFoundException(HttpMethod.GET, "favicon.ico", "");

    ResponseEntity<Object> response = handler.handleException(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertStandard(problem(response), ProblemTypes.LINK_NOT_FOUND, "Link not found");
    assertThat(problem(response).getDetail()).isEqualTo(ApiExceptionHandler.NO_RESOURCE);
  }

  @Test
  void handleException_noHandlerFound_mapsTo404LinkNotFoundWithFixedDetail() throws Exception {
    NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/nothing", new HttpHeaders());

    ResponseEntity<Object> response = handler.handleException(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(404);
    assertThat(problem(response).getDetail()).isEqualTo(ApiExceptionHandler.NO_RESOURCE);
  }

  @Test
  void handleException_messageNotReadable_mapsTo400WithSafeDetailAndEmptyErrors() throws Exception {
    HttpMessageNotReadableException ex =
        new HttpMessageNotReadableException(
            "JSON parse error: Unexpected end-of-input", new MockHttpInputMessage(new byte[0]));

    ResponseEntity<Object> response = handler.handleException(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertStandard(
        problem(response), ProblemTypes.VALIDATION_FAILED, ApiExceptionHandler.VALIDATION_TITLE);
    assertThat(problem(response).getDetail()).isEqualTo(ApiExceptionHandler.UNREADABLE);
    assertThat(problem(response).getProperties())
        .containsEntry(ApiExceptionHandler.ERRORS, List.of());
  }

  @Test
  void handleException_methodArgumentNotValid_mapsTo400WithFieldErrorsSortedByField()
      throws Exception {
    BeanPropertyBindingResult binding =
        new BeanPropertyBindingResult(new CreateLinkRequest("", "ab"), "createLinkRequest");
    binding.addError(new FieldError("createLinkRequest", "url", "URL is required"));
    binding.addError(new FieldError("createLinkRequest", "customAlias", "Alias is too short"));
    MethodParameter parameter =
        new MethodParameter(LinkController.class.getMethod("create", CreateLinkRequest.class), 0);

    ResponseEntity<Object> response =
        handler.handleException(new MethodArgumentNotValidException(parameter, binding), request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertStandard(
        problem(response), ProblemTypes.VALIDATION_FAILED, ApiExceptionHandler.VALIDATION_TITLE);
    assertThat(problem(response).getProperties())
        .containsEntry(
            ApiExceptionHandler.ERRORS,
            List.of(
                new UrlValidationException.FieldError("customAlias", "Alias is too short"),
                new UrlValidationException.FieldError("url", "URL is required")));
  }

  @Test
  void handleException_frameworkExceptionWithoutOverride_stillGetsRequestIdAndTimestamp()
      throws Exception {
    MissingServletRequestParameterException ex =
        new MissingServletRequestParameterException("q", "String");

    ResponseEntity<Object> response = handler.handleException(ex, request);

    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertStandard(problem(response), ProblemTypes.ABOUT_BLANK, "Bad Request");
    assertThat(problem(response).getDetail()).isEqualTo(ex.getBody().getDetail());
  }

  private static ProblemDetail problem(ResponseEntity<Object> response) {
    assertThat(response.getBody()).isInstanceOf(ProblemDetail.class);
    return (ProblemDetail) response.getBody();
  }

  private static void assertStandard(ProblemDetail problem, URI type, String title) {
    assertThat(problem).isNotNull();
    assertThat(problem.getType()).isEqualTo(type);
    assertThat(problem.getTitle()).isEqualTo(title);
    assertThat(problem.getProperties())
        .containsEntry(ProblemDetails.REQUEST_ID, REQUEST_ID)
        .containsKey(ProblemDetails.TIMESTAMP);
  }
}
