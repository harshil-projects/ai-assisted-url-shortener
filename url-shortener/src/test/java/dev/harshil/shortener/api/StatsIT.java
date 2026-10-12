package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.harshil.shortener.api.dto.CreateLinkRequest;
import dev.harshil.shortener.api.dto.LinkResponse;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.LinkRepository;
import dev.harshil.shortener.support.TestProfile;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Stats over HTTP: create, redirect, then read the aggregate once the asynchronous clicks land. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class StatsIT {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String API_KEY = "test-api-key";
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
  void stats_linkWithoutClicks_returnsZerosAndNullLastClickedAt() {
    String code = createLink("https://example.com/quiet");

    stats(code)
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo(code)
        .jsonPath("$.totalClicks")
        .isEqualTo(0)
        .jsonPath("$.clicksLast24h")
        .isEqualTo(0)
        .jsonPath("$.clicksLast7d")
        .isEqualTo(0)
        .jsonPath("$.lastClickedAt")
        .isEmpty();
  }

  @Test
  void stats_afterTwoRedirects_countsTwoInEveryWindowAndSetsLastClickedAt() {
    String code = createLink("https://example.com/busy");
    client.get().uri("/" + code).exchange().expectStatus().isFound();
    client.get().uri("/" + code).exchange().expectStatus().isFound();

    await()
        .atMost(AT_MOST)
        .untilAsserted(() -> stats(code).expectBody().jsonPath("$.totalClicks").isEqualTo(2));

    stats(code)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.totalClicks")
        .isEqualTo(2)
        .jsonPath("$.clicksLast24h")
        .isEqualTo(2)
        .jsonPath("$.clicksLast7d")
        .isEqualTo(2)
        .jsonPath("$.lastClickedAt")
        .isNotEmpty();
  }

  @Test
  void stats_unknownCode_returns404LinkNotFoundProblem() {
    stats("unknown1")
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/link-not-found");
  }

  @Test
  void stats_withoutApiKey_returns401() {
    client
        .get()
        .uri(LINKS + "/abcdefg/stats")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/unauthorized");
  }

  private RestTestClient.ResponseSpec stats(String code) {
    return client
        .get()
        .uri(LINKS + "/" + code + "/stats")
        .header(API_KEY_HEADER, API_KEY)
        .exchange();
  }

  private String createLink(String url) {
    LinkResponse body =
        client
            .post()
            .uri(LINKS)
            .header(API_KEY_HEADER, API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateLinkRequest(url, null))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(LinkResponse.class)
            .returnResult()
            .getResponseBody();
    assertThat(body).isNotNull();
    return body.code();
  }
}
