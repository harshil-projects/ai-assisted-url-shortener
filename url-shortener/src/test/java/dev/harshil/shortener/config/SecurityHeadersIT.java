package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.support.TestApiKey;
import dev.harshil.shortener.support.TestProfile;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The hardening headers every response must carry (design 3.3, NFR-05), the correlation id contract
 * of {@link RequestIdFilter}, and the two exceptions: the Swagger-only Content-Security-Policy and
 * the public OpenAPI document.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class SecurityHeadersIT {

  private static final String SWAGGER_CSP =
      "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
          + "frame-ancestors 'none'; object-src 'none'";
  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired private RestTestClient client;

  static Stream<String> malformedRequestIds() {
    return Stream.of("has space", "a".repeat(65));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/abcdefg", "/api/v1/links/abcdefg"})
  void anyResponse_carriesHardeningHeadersAndRequestId(String path) {
    client
        .get()
        .uri(path)
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .exchange()
        .expectHeader()
        .valueEquals("X-Content-Type-Options", "nosniff")
        .expectHeader()
        .valueEquals("X-Frame-Options", "DENY")
        .expectHeader()
        .valueEquals("Referrer-Policy", "no-referrer")
        .expectHeader()
        .value(HttpHeaders.CACHE_CONTROL, value -> assertThat(value).contains("no-store"))
        .expectHeader()
        .valueMatches(RequestIdFilter.HEADER, UUID_PATTERN)
        .expectHeader()
        .doesNotExist("Content-Security-Policy");
  }

  @Test
  void requestId_wellFormedClientValue_isEchoed() {
    client
        .get()
        .uri("/actuator/health")
        .header(RequestIdFilter.HEADER, "trace-0001")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals(RequestIdFilter.HEADER, "trace-0001");
  }

  @ParameterizedTest
  @MethodSource("malformedRequestIds")
  void requestId_malformedClientValue_isReplaced(String clientValue) {
    client
        .get()
        .uri("/actuator/health")
        .header(RequestIdFilter.HEADER, clientValue)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .value(
            RequestIdFilter.HEADER,
            value -> assertThat(value).isNotEqualTo(clientValue).matches(UUID_PATTERN));
  }

  @Test
  void swaggerUi_carriesTheSwaggerOnlyContentSecurityPolicy() {
    client
        .get()
        .uri("/swagger-ui/index.html")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("Content-Security-Policy", SWAGGER_CSP)
        .expectHeader()
        .valueEquals("Referrer-Policy", "no-referrer")
        .expectHeader()
        .exists(RequestIdFilter.HEADER);
  }

  @Test
  void apiDocs_withoutKey_isPublicJson() {
    client
        .get()
        .uri("/v3/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.openapi")
        .exists()
        .jsonPath("$.info.title")
        .isEqualTo("URL Shortener API");
  }
}
