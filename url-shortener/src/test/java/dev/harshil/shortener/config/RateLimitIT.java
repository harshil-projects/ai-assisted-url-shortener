package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.support.TestApiKey;
import dev.harshil.shortener.support.TestProfile;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * The create rate limit over HTTP (FR-11, NFR-05) in a dedicated context with a capacity of three.
 *
 * <p>Every test request arrives from the loopback address, so all of them share one bucket. The
 * bucket is therefore consumed by exactly one test, and the tests are ordered so the checks that
 * must run against an empty bucket come after it. Allowed requests assert "not 429" rather than 201
 * because controllers are not part of this slice of the work.
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = "app.ratelimit.create.capacity=3")
@AutoConfigureRestTestClient
@TestProfile
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimitIT {

  private static final String CREATE_BODY = "{\"url\":\"https://example.com/docs\"}";

  @Autowired private RestTestClient client;

  @Test
  @Order(1)
  void create_fourthRequestInPeriod_returns429ProblemAndThrottlesBeforeAuthentication() {
    expectAllowed(createWithKey(), "2");
    expectAllowed(createWithKey(), "1");
    expectAllowed(createWithKey(), "0");

    createWithKey()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectHeader()
        .valueMatches(HttpHeaders.RETRY_AFTER, "[1-9][0-9]*")
        .expectHeader()
        .valueMatches(RateLimitFilter.RESET_HEADER, "[1-9][0-9]*")
        .expectHeader()
        .valueEquals(RateLimitFilter.LIMIT_HEADER, "3")
        .expectHeader()
        .valueEquals(RateLimitFilter.REMAINING_HEADER, "0")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/rate-limited")
        .jsonPath("$.title")
        .isEqualTo("Too many requests")
        .jsonPath("$.status")
        .isEqualTo(429);

    // The limiter sits before the security chain: an unauthenticated flood is throttled, not
    // answered with 401, so key guessing cannot bypass it.
    createWithoutKey().expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
  }

  @Test
  @Order(2)
  void get_afterTheCreateBucketIsEmpty_isNeverRateLimited() {
    client
        .get()
        .uri("/api/v1/links/abcdefg")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()))
        .expectHeader()
        .doesNotExist(RateLimitFilter.LIMIT_HEADER);
  }

  @Test
  @Order(3)
  void post_toAnotherApiPath_afterTheCreateBucketIsEmpty_isNotRateLimited() {
    client
        .post()
        .uri("/api/v1/links/abcdefg")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(CREATE_BODY)
        .exchange()
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()))
        .expectHeader()
        .doesNotExist(RateLimitFilter.LIMIT_HEADER);
  }

  private ResponseSpec createWithKey() {
    return client
        .post()
        .uri("/api/v1/links")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .contentType(MediaType.APPLICATION_JSON)
        .body(CREATE_BODY)
        .exchange();
  }

  private ResponseSpec createWithoutKey() {
    return client
        .post()
        .uri("/api/v1/links")
        .contentType(MediaType.APPLICATION_JSON)
        .body(CREATE_BODY)
        .exchange();
  }

  private static void expectAllowed(ResponseSpec response, String expectedRemaining) {
    response
        .expectStatus()
        .value(status -> assertThat(status).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()))
        .expectHeader()
        .valueEquals(RateLimitFilter.LIMIT_HEADER, "3")
        .expectHeader()
        .valueEquals(RateLimitFilter.REMAINING_HEADER, expectedRemaining);
  }
}
