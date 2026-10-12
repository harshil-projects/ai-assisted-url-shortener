package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.harshil.shortener.api.dto.CreateLinkRequest;
import dev.harshil.shortener.config.SecurityConfig;
import dev.harshil.shortener.domain.AliasTakenException;
import dev.harshil.shortener.domain.CodeGenerationExhaustedException;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.domain.UrlValidationException;
import dev.harshil.shortener.service.LinkService;
import dev.harshil.shortener.support.TestProfile;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;

/**
 * Web slice for the management endpoints: request mapping, validation, security and the problem
 * bodies produced through the shared handler. The service is mocked; the real security chain runs.
 */
@WebMvcTest(LinkController.class)
@Import({SecurityConfig.class, ShortUrlBuilder.class})
@TestProfile
class LinkControllerTest {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String API_KEY = "test-api-key";
  private static final String LINKS = "/api/v1/links";
  private static final Instant CREATED = Instant.parse("2026-10-12T10:00:00Z");
  private static final Link LINK = new Link("abcdefg", "https://example.com/page", CREATED);

  @Autowired private MockMvcTester mvc;
  @MockitoBean private LinkService linkService;

  @Test
  void create_validBodyWithKey_returns201WithLocationAndLinkBody() {
    when(linkService.create(new CreateLinkRequest("https://example.com/page", null)))
        .thenReturn(LINK);

    assertThat(post("{\"url\":\"https://example.com/page\"}"))
        .hasStatus(HttpStatus.CREATED)
        .hasHeader(HttpHeaders.LOCATION, LINKS + "/abcdefg")
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .bodyJson()
        .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("abcdefg"))
        .hasPathSatisfying("$.url", url -> url.assertThat().isEqualTo("https://example.com/page"))
        .hasPathSatisfying(
            "$.shortUrl", shortUrl -> shortUrl.assertThat().isEqualTo("http://localhost/abcdefg"))
        .hasPathSatisfying("$.createdAt", at -> at.assertThat().isEqualTo("2026-10-12T10:00:00Z"));
  }

  @Test
  void create_blankUrl_returns400ValidationFailedWithUrlFieldError() {
    assertThat(post("{\"url\":\"   \"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/validation-failed"))
        .hasPathSatisfying("$.errors[0].field", field -> field.assertThat().isEqualTo("url"))
        .hasPathSatisfying(
            "$.errors[0].message", message -> message.assertThat().isEqualTo("URL is required"))
        .hasPathSatisfying("$.timestamp", timestamp -> timestamp.assertThat().isNotNull());
    verifyNoInteractions(linkService);
  }

  @Test
  void create_missingUrlMember_returns400WithUrlFieldErrorNotAGenericParseFailure() {
    assertThat(post("{\"customAlias\":\"my-alias\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .hasPathSatisfying("$.errors[0].field", field -> field.assertThat().isEqualTo("url"));
  }

  @Test
  void create_aliasFailingThePattern_returns400WithCustomAliasFieldError() {
    assertThat(post("{\"url\":\"https://example.com/x\",\"customAlias\":\"ab\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .hasPathSatisfying(
            "$.errors[0].field", field -> field.assertThat().isEqualTo("customAlias"));
    verifyNoInteractions(linkService);
  }

  @Test
  void create_malformedJson_returns400ValidationFailedWithoutTheParserMessage() {
    assertThat(post("{\"url\": "))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/validation-failed"))
        .hasPathSatisfying(
            "$.detail", detail -> detail.assertThat().isEqualTo(ApiExceptionHandler.UNREADABLE));
  }

  @Test
  void create_wrongContentType_returns415UnsupportedMediaTypeProblem() {
    assertThat(
            mvc.post()
                .uri(LINKS)
                .header(API_KEY_HEADER, API_KEY)
                .contentType(MediaType.TEXT_PLAIN)
                .content("url=https://example.com"))
        .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/unsupported-media-type"))
        .hasPathSatisfying("$.timestamp", timestamp -> timestamp.assertThat().isNotNull());
  }

  @Test
  void create_withoutApiKey_returns401AndNeverReachesTheService() {
    assertThat(
            mvc.post()
                .uri(LINKS)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://example.com/page\"}"))
        .hasStatus(HttpStatus.UNAUTHORIZED)
        .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey realm=\"url-shortener\"");
    verifyNoInteractions(linkService);
  }

  @Test
  void create_serviceReportsReservedAlias_returns400OnCustomAlias() {
    when(linkService.create(any()))
        .thenThrow(UrlValidationException.of("customAlias", "Alias is reserved"));

    assertThat(post("{\"url\":\"https://example.com/x\",\"customAlias\":\"admin\"}"))
        .hasStatus(HttpStatus.BAD_REQUEST)
        .bodyJson()
        .hasPathSatisfying(
            "$.errors[0].field", field -> field.assertThat().isEqualTo("customAlias"))
        .hasPathSatisfying(
            "$.errors[0].message", message -> message.assertThat().isEqualTo("Alias is reserved"));
  }

  @Test
  void create_serviceReportsAliasTaken_returns409AliasTakenProblem() {
    when(linkService.create(any())).thenThrow(new AliasTakenException("taken"));

    assertThat(post("{\"url\":\"https://example.com/x\",\"customAlias\":\"taken\"}"))
        .hasStatus(HttpStatus.CONFLICT)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying("$.type", type -> type.assertThat().isEqualTo("/problems/alias-taken"))
        .hasPathSatisfying("$.status", status -> status.assertThat().isEqualTo(409));
  }

  @Test
  void create_serviceExhaustsCodes_returns500CodeGenerationExhaustedProblem() {
    when(linkService.create(any())).thenThrow(new CodeGenerationExhaustedException(5));

    assertThat(post("{\"url\":\"https://example.com/x\"}"))
        .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/code-generation-exhausted"));
  }

  @Test
  void create_databaseUnavailable_returns503WithRetryAfterFive() {
    when(linkService.create(any()))
        .thenThrow(new DataAccessResourceFailureException("connection refused"));

    assertThat(post("{\"url\":\"https://example.com/x\"}"))
        .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
        .hasHeader(HttpHeaders.RETRY_AFTER, "5")
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/service-unavailable"));
  }

  @Test
  void create_unexpectedFailure_returns500AboutBlankWithoutLeakingTheMessage() {
    when(linkService.create(any())).thenThrow(new IllegalStateException("secret internal state"));

    assertThat(post("{\"url\":\"https://example.com/x\"}"))
        .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying("$.type", type -> type.assertThat().isEqualTo("about:blank"))
        .hasPathSatisfying("$.detail", detail -> detail.assertThat().isEqualTo("Unexpected error"))
        .doesNotHavePath("$.trace");
  }

  @Test
  void get_knownCode_returns200WithTheLink() {
    when(linkService.get("abcdefg")).thenReturn(LINK);

    assertThat(mvc.get().uri(LINKS + "/abcdefg").header(API_KEY_HEADER, API_KEY))
        .hasStatusOk()
        .bodyJson()
        .hasPathSatisfying("$.code", code -> code.assertThat().isEqualTo("abcdefg"));
  }

  @Test
  void get_unknownCode_returns404LinkNotFoundProblem() {
    when(linkService.get("missing")).thenThrow(new LinkNotFoundException("missing"));

    assertThat(mvc.get().uri(LINKS + "/missing").header(API_KEY_HEADER, API_KEY))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/link-not-found"));
  }

  @Test
  void get_codeFailingThePathPattern_returns404WithoutCallingTheService() {
    assertThat(mvc.get().uri(LINKS + "/ab").header(API_KEY_HEADER, API_KEY))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/link-not-found"))
        .hasPathSatisfying(
            "$.detail", detail -> detail.assertThat().isEqualTo("No resource at this path"));
    verifyNoInteractions(linkService);
  }

  @Test
  void delete_knownCode_returns204WithoutBody() {
    assertThat(mvc.delete().uri(LINKS + "/abcdefg").header(API_KEY_HEADER, API_KEY))
        .hasStatus(HttpStatus.NO_CONTENT)
        .body()
        .isEmpty();
  }

  @Test
  void delete_unknownCode_returns404LinkNotFoundProblem() {
    doThrow(new LinkNotFoundException("missing")).when(linkService).delete("missing");

    assertThat(mvc.delete().uri(LINKS + "/missing").header(API_KEY_HEADER, API_KEY))
        .hasStatus(HttpStatus.NOT_FOUND)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/link-not-found"));
  }

  @Test
  void patch_onLinkResource_returns405MethodNotAllowedProblem() {
    assertThat(mvc.patch().uri(LINKS + "/abcdefg").header(API_KEY_HEADER, API_KEY))
        .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/method-not-allowed"));
  }

  private MockMvcRequestBuilder post(String json) {
    return mvc.post()
        .uri(LINKS)
        .header(API_KEY_HEADER, API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json);
  }
}
