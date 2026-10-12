package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.harshil.shortener.domain.ClickEvent;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.LinkRepository;
import dev.harshil.shortener.support.TestApiKey;
import dev.harshil.shortener.support.TestProfile;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Click attribution over HTTP (R-03, T-202): what the redirect records from the {@code Referer}
 * header and the {@code utm_*} parameters, what the stats endpoint shows for it, and that the
 * redirect itself is unchanged. The stored target carries its own query string so the test can tell
 * "unchanged" from "parameters appended".
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class AttributionIT {

  private static final String TARGET = "https://example.com/landing?keep=1";
  private static final String LINKS = "/api/v1/links";
  private static final Duration AT_MOST = Duration.ofSeconds(5);

  @Autowired private RestTestClient client;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clickEvents;

  @AfterEach
  void cleanTables() {
    clickEvents.deleteAllInBatch();
    links.deleteAllInBatch();
  }

  @Test
  void redirectWithRefererAndUtmShowsUpInStats() {
    links.save(new Link("attr001", TARGET, Instant.now(), null));

    client
        .get()
        .uri("/attr001?utm_source=newsletter&utm_medium=email&utm_campaign=oct")
        .header(HttpHeaders.REFERER, "https://News.Example.com/story?x=1")
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, TARGET);
    awaitClicks(1);

    stats("attr001")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.totalClicks")
        .isEqualTo(1)
        .jsonPath("$.referrers.length()")
        .isEqualTo(1)
        .jsonPath("$.referrers[0].host")
        .isEqualTo("news.example.com")
        .jsonPath("$.referrers[0].clicks")
        .isEqualTo(1)
        .jsonPath("$.campaigns.length()")
        .isEqualTo(1)
        .jsonPath("$.campaigns[0].source")
        .isEqualTo("newsletter")
        .jsonPath("$.campaigns[0].medium")
        .isEqualTo("email")
        .jsonPath("$.campaigns[0].campaign")
        .isEqualTo("oct")
        .jsonPath("$.campaigns[0].clicks")
        .isEqualTo(1);
  }

  @Test
  void redirectWithoutRefererRecordsNullHost() {
    links.save(new Link("attr002", TARGET, Instant.now(), null));

    client.get().uri("/attr002").exchange().expectStatus().isFound();
    awaitClicks(1);

    assertThat(onlyClick().getReferrerHost()).isNull();
    stats("attr002")
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.totalClicks")
        .isEqualTo(1)
        .jsonPath("$.referrers")
        .isEmpty()
        .jsonPath("$.campaigns")
        .isEmpty();
  }

  @Test
  void redirect_refererThatIsNotAUrl_recordsNullHostAndStillCounts() {
    links.save(new Link("attr003", TARGET, Instant.now(), null));

    client
        .get()
        .uri("/attr003")
        .header(HttpHeaders.REFERER, "not a url")
        .exchange()
        .expectStatus()
        .isFound();
    awaitClicks(1);

    ClickEvent click = onlyClick();
    assertThat(click.getReferrerHost()).isNull();
    assertThat(click.getUtmSource()).isNull();
  }

  @Test
  void redirect_utmWithControlCharactersAndLongValues_isCleanedAndTruncated() {
    links.save(new Link("attr004", TARGET, Instant.now(), null));

    client
        .get()
        .uri(
            "/attr004?utm_source={source}&utm_campaign={campaign}",
            "news\r\nletter",
            "c".repeat(100))
        .exchange()
        .expectStatus()
        .isFound();
    awaitClicks(1);

    ClickEvent click = onlyClick();
    assertThat(click.getUtmSource()).isEqualTo("newsletter");
    assertThat(click.getUtmMedium()).isNull();
    assertThat(click.getUtmCampaign()).isEqualTo("c".repeat(64));
  }

  @Test
  void queryParametersAreNotForwarded() {
    links.save(new Link("attr005", TARGET, Instant.now(), null));

    client
        .get()
        .uri("/attr005?utm_source=newsletter&utm_medium=email&utm_campaign=oct&gclid=abc")
        .header(HttpHeaders.REFERER, "https://news.example.com/")
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, TARGET)
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");

    // The click is recorded asynchronously; wait for it so it cannot leak into the next test.
    awaitClicks(1);
  }

  private void awaitClicks(long expected) {
    await()
        .atMost(AT_MOST)
        .untilAsserted(() -> assertThat(clickEvents.count()).isEqualTo(expected));
  }

  private ClickEvent onlyClick() {
    return clickEvents.findAll().get(0);
  }

  private RestTestClient.ResponseSpec stats(String code) {
    return client
        .get()
        .uri(LINKS + "/" + code + "/stats")
        .header(TestApiKey.HEADER, TestApiKey.VALUE)
        .exchange();
  }
}
