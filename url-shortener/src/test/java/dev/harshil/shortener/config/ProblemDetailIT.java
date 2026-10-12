package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.support.TestApiKey;
import dev.harshil.shortener.support.TestProfile;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The three filter-produced error bodies (401 from the entry point, 413 from {@link
 * RequestSizeFilter}, 429 from {@link RateLimitFilter}) have one identical shape (design 3.4).
 *
 * <p>Runs in a dedicated context with a create capacity of one so the 429 is reached with a single
 * extra request. The 413 request is refused before the limiter runs and the 401 request is a GET,
 * so neither consumes the only token.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "app.ratelimit.create.capacity=1")
@AutoConfigureRestTestClient
@TestProfile
class ProblemDetailIT {

  private static final List<String> CANONICAL_MEMBERS =
      List.of("type", "title", "status", "detail", "requestId", "timestamp");
  private static final int MAX_BODY_BYTES = 16_384;
  private static final String CREATE_BODY = "{\"url\":\"https://example.com/docs\"}";

  @Autowired private RestTestClient client;

  private final JsonMapper json = JsonMapper.builder().build();

  @Test
  void unauthorized_body_hasTheCanonicalShape() {
    EntityExchangeResult<byte[]> result =
        client
            .get()
            .uri("/api/v1/links/abcdefg")
            .exchange()
            .expectStatus()
            .isUnauthorized()
            .expectBody()
            .returnResult();

    assertCanonicalShape(result, HttpStatus.UNAUTHORIZED, "/problems/unauthorized");
  }

  @Test
  void payloadTooLarge_body_hasTheCanonicalShape() {
    EntityExchangeResult<byte[]> result =
        client
            .post()
            .uri("/api/v1/links")
            .header(TestApiKey.HEADER, TestApiKey.VALUE)
            .contentType(MediaType.APPLICATION_JSON)
            .body("x".repeat(MAX_BODY_BYTES + 1))
            .exchange()
            .expectStatus()
            .isEqualTo(HttpStatus.CONTENT_TOO_LARGE)
            .expectBody()
            .returnResult();

    assertCanonicalShape(result, HttpStatus.CONTENT_TOO_LARGE, "/problems/payload-too-large");
  }

  @Test
  void rateLimited_body_hasTheCanonicalShape() {
    create()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()));

    EntityExchangeResult<byte[]> result =
        create().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS).expectBody().returnResult();

    assertCanonicalShape(result, HttpStatus.TOO_MANY_REQUESTS, "/problems/rate-limited");
  }

  private RestTestClient.ResponseSpec create() {
    return client
        .post()
        .uri("/api/v1/links")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(CREATE_BODY)
        .exchange();
  }

  private void assertCanonicalShape(
      EntityExchangeResult<byte[]> result, HttpStatus status, String type) {
    assertThat(result.getResponseHeaders().getContentType())
        .isNotNull()
        .satisfies(
            contentType ->
                assertThat(contentType.isCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .isTrue());
    Map<String, Object> body =
        json.readValue(result.getResponseBody(), new TypeReference<Map<String, Object>>() {});

    assertThat(body.keySet()).containsExactlyElementsOf(CANONICAL_MEMBERS);
    assertThat(body)
        .containsEntry("type", type)
        .containsEntry("status", status.value())
        .containsEntry("requestId", result.getResponseHeaders().getFirst(RequestIdFilter.HEADER));
    assertThat(body.get("title")).asString().isNotBlank();
    assertThat(body.get("detail")).asString().isNotBlank();
    assertThat(Instant.parse(body.get("timestamp").toString())).isBeforeOrEqualTo(Instant.now());
  }
}
