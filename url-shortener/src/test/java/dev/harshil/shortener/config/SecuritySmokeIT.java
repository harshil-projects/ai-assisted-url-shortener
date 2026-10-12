package dev.harshil.shortener.config;

import dev.harshil.shortener.support.TestProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Proves the security skeleton end to end over HTTP: the API key gate, the Problem Details body
 * written by the filter, public health, and the default security headers.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class SecuritySmokeIT {

  private static final String API_KEY = "test-api-key";

  @Autowired private RestTestClient client;

  @Test
  void protectedPath_withoutKey_returns401ProblemWithChallenge() {
    client
        .get()
        .uri("/api/v1/links/abcdefg")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, ApiKeyFilter.CHALLENGE)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/unauthorized")
        .jsonPath("$.title")
        .isEqualTo("Missing or invalid API key")
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.timestamp")
        .exists();
  }

  @Test
  void protectedPath_withWrongKey_returns401() {
    client
        .get()
        .uri("/api/v1/links/abcdefg")
        .header(ApiKeyFilter.HEADER, "not-the-key")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, ApiKeyFilter.CHALLENGE);
  }

  @Test
  void protectedPath_withValidKey_passesAuthentication() {
    // No controller is mapped yet in the skeleton, so passing the gate surfaces as 404, not 401.
    client
        .get()
        .uri("/api/v1/links/abcdefg")
        .header(ApiKeyFilter.HEADER, API_KEY)
        .exchange()
        .expectStatus()
        .isNotFound();
  }

  @Test
  void health_isPublicAndCarriesSecurityHeaders() {
    client
        .get()
        .uri("/actuator/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Content-Type-Options", "nosniff")
        .expectHeader()
        .valueEquals("X-Frame-Options", "DENY")
        .expectHeader()
        .valueEquals("Referrer-Policy", "no-referrer");
  }

  @Test
  void metrics_requireKeyUnderTestProfile() {
    client.get().uri("/actuator/metrics").exchange().expectStatus().isUnauthorized();
    client
        .get()
        .uri("/actuator/metrics")
        .header(ApiKeyFilter.HEADER, API_KEY)
        .exchange()
        .expectStatus()
        .isOk();
  }
}
