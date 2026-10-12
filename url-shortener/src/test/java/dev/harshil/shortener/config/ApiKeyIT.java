package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.support.TestApiKey;
import dev.harshil.shortener.support.TestProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * The API key gate over HTTP (FR-10, ADR 0007): what is protected, what is public, and how a
 * rejection looks. Success paths assert "not 401" because controllers are not part of this slice of
 * the work; once they exist the same requests return 200 or 404.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class ApiKeyIT {

  @Autowired private RestTestClient client;

  @Test
  void apiPath_withoutKey_returns401ProblemWithChallenge() {
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
        .jsonPath("$.status")
        .isEqualTo(401);
  }

  @Test
  void apiPath_withWrongKey_returns401WithChallenge() {
    client
        .get()
        .uri("/api/v1/links/abcdefg")
        .header(TestApiKey.HEADER, "not-the-key")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .valueEquals(HttpHeaders.WWW_AUTHENTICATE, ApiKeyFilter.CHALLENGE);
  }

  @Test
  void apiPath_withValidKey_passesAuthentication() {
    client
        .get()
        .uri("/api/v1/links/abcdefg")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()))
        .expectHeader()
        .doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
  }

  @Test
  void redirectPath_withoutKey_isNotChallenged() {
    client
        .get()
        .uri("/abcdefg")
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(HttpStatus.UNAUTHORIZED.value()))
        .expectHeader()
        .doesNotExist(HttpHeaders.WWW_AUTHENTICATE);
  }

  @Test
  void health_withoutKey_isPublic() {
    client.get().uri("/actuator/health").exchange().expectStatus().isOk();
  }

  @Test
  void prometheus_underTestProfile_requiresTheKey() {
    client.get().uri("/actuator/prometheus").exchange().expectStatus().isUnauthorized();
    client
        .get()
        .uri("/actuator/prometheus")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .exchange()
        .expectStatus()
        .isOk();
  }
}
