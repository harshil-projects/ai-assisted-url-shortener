package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.harshil.shortener.api.dto.LinkResponse;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.LinkRepository;
import dev.harshil.shortener.support.MutableClock;
import dev.harshil.shortener.support.TestProfile;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Expiry end to end against the full context with a controllable clock (design 3.6, 3.13): a link
 * redirects until its {@code expiresAt} passes, then answers 410 with {@code no-store} while its
 * row, metadata and click history stay; past and zoneless values are refused at create.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class ExpiryIT {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String API_KEY = "test-api-key";
  private static final String LINKS = "/api/v1/links";
  private static final Instant START = Instant.parse("2026-10-12T10:00:00Z");
  private static final Duration AT_MOST = Duration.ofSeconds(5);
  private static final Duration SETTLE = Duration.ofMillis(500);

  @Autowired private RestTestClient client;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clickEvents;
  @Autowired private MutableClock clock;
  @Autowired private MeterRegistry registry;

  @BeforeEach
  void resetClock() {
    clock.set(START);
  }

  @AfterEach
  void cleanTables() {
    clickEvents.deleteAllInBatch();
    links.deleteAllInBatch();
  }

  @Test
  void expiredLinkIs410AndStatsRemain() {
    // Offset other than Z on purpose: 16:30+05:30 is 11:00Z, one hour after START.
    String code =
        createAndReadCode(
            "{\"url\":\"https://example.com/expiring\",\"expiresAt\":\"2026-10-12T16:30:00+05:30\"}",
            "2026-10-12T11:00:00Z",
            false);
    client.get().uri("/" + code).exchange().expectStatus().isFound();
    client.head().uri("/" + code).exchange().expectStatus().isFound();
    await().atMost(AT_MOST).untilAsserted(() -> assertThat(clickEvents.count()).isEqualTo(1));
    double expiredBefore = expiredRedirects();

    clock.advance(Duration.ofHours(2));

    client
        .get()
        .uri("/" + code)
        .exchange()
        .expectStatus()
        .isEqualTo(410)
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/link-expired")
        .jsonPath("$.title")
        .isEqualTo("Link has expired")
        .jsonPath("$.status")
        .isEqualTo(410)
        .jsonPath("$.timestamp")
        .exists();
    client
        .head()
        .uri("/" + code)
        .exchange()
        .expectStatus()
        .isEqualTo(410)
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
    metadata(code)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.expired")
        .isEqualTo(true)
        .jsonPath("$.expiresAt")
        .isEqualTo("2026-10-12T11:00:00Z");
    stats(code)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.totalClicks")
        .isEqualTo(1)
        .jsonPath("$.clicksLast24h")
        .isEqualTo(1)
        .jsonPath("$.expired")
        .isEqualTo(true)
        .jsonPath("$.expiresAt")
        .isEqualTo("2026-10-12T11:00:00Z");
    assertThat(expiredRedirects()).isEqualTo(expiredBefore + 2);
    await().during(SETTLE).atMost(AT_MOST).until(() -> clickEvents.count() == 1);
    assertThat(links.findByCode(code)).isPresent();
  }

  @Test
  void expiredLink_canStillBeDeletedAndItsCodeIsThenUnknown() {
    String code =
        createAndReadCode(
            "{\"url\":\"https://example.com/delete-me\",\"expiresAt\":\"2026-10-12T10:00:01Z\"}",
            "2026-10-12T10:00:01Z",
            false);
    clock.advance(Duration.ofSeconds(1));
    client.get().uri("/" + code).exchange().expectStatus().isEqualTo(410);

    client
        .delete()
        .uri(LINKS + "/" + code)
        .header(API_KEY_HEADER, API_KEY)
        .exchange()
        .expectStatus()
        .isNoContent();

    client.get().uri("/" + code).exchange().expectStatus().isNotFound();
    assertThat(links.existsByCode(code)).isFalse();
  }

  @Test
  void linkWithoutExpiry_neverExpiresAndReportsNullExpiresAt() {
    String code = createAndReadCode("{\"url\":\"https://example.com/forever\"}", null, false);

    clock.advance(Duration.ofDays(3650));

    client.get().uri("/" + code).exchange().expectStatus().isFound();
    metadata(code)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.expired")
        .isEqualTo(false)
        .jsonPath("$.expiresAt")
        .isEmpty();
    stats(code).expectStatus().isOk().expectBody().jsonPath("$.expired").isEqualTo(false);
  }

  @Test
  void rejectsPastAndZoneless() {
    create("{\"url\":\"https://example.com/past\",\"expiresAt\":\"2026-10-12T09:59:59Z\"}")
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/validation-failed")
        .jsonPath("$.errors[0].field")
        .isEqualTo("expiresAt")
        .jsonPath("$.errors[0].message")
        .isEqualTo("expiresAt must be in the future");

    create("{\"url\":\"https://example.com/zoneless\",\"expiresAt\":\"2026-10-13T10:00:00\"}")
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/validation-failed")
        .jsonPath("$.detail")
        .isEqualTo(ApiExceptionHandler.UNREADABLE)
        .jsonPath("$.errors")
        .isEmpty();

    assertThat(links.count()).isZero();
  }

  @Test
  void expiresAtEqualToNow_isRejectedAtCreate() {
    create("{\"url\":\"https://example.com/now\",\"expiresAt\":\"2026-10-12T10:00:00Z\"}")
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.errors[0].field")
        .isEqualTo("expiresAt");
  }

  private RestTestClient.ResponseSpec create(String json) {
    return client
        .post()
        .uri(LINKS)
        .header(API_KEY_HEADER, API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(json)
        .exchange();
  }

  private RestTestClient.ResponseSpec metadata(String code) {
    return client.get().uri(LINKS + "/" + code).header(API_KEY_HEADER, API_KEY).exchange();
  }

  private RestTestClient.ResponseSpec stats(String code) {
    return client
        .get()
        .uri(LINKS + "/" + code + "/stats")
        .header(API_KEY_HEADER, API_KEY)
        .exchange();
  }

  private String createAndReadCode(String json, String expectedExpiresAt, boolean expectedExpired) {
    LinkResponse body =
        create(json)
            .expectStatus()
            .isCreated()
            .expectBody(LinkResponse.class)
            .returnResult()
            .getResponseBody();
    assertThat(body).isNotNull();
    assertThat(body.expired()).isEqualTo(expectedExpired);
    assertThat(body.expiresAt())
        .isEqualTo(expectedExpiresAt == null ? null : Instant.parse(expectedExpiresAt));
    return body.code();
  }

  private double expiredRedirects() {
    return registry.counter("shortener.redirects", "outcome", "expired").count();
  }

  /**
   * Replaces the application clock with one the test can move; the production bean stays unused.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class ClockSupport {

    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock(START);
    }
  }
}
