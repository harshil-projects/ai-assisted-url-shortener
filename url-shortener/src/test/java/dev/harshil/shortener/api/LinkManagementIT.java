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

/** Hard delete over HTTP, verified through the repositories so the database cascade is proven. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class LinkManagementIT {

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
  void delete_linkWithClicks_returns204AndRemovesTheLinkAndItsEvents() {
    String code = createLink("https://example.com/to-delete");
    client.get().uri("/" + code).exchange().expectStatus().isFound();
    await().atMost(AT_MOST).untilAsserted(() -> assertThat(clickEvents.count()).isEqualTo(1));

    delete(code).expectStatus().isNoContent().expectBody().isEmpty();

    assertThat(links.findByCode(code)).isEmpty();
    assertThat(clickEvents.count()).isZero();
  }

  @Test
  void delete_twice_secondCallReturns404LinkNotFound() {
    String code = createLink("https://example.com/once");

    delete(code).expectStatus().isNoContent();
    delete(code)
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/link-not-found")
        .jsonPath("$.timestamp")
        .exists();
  }

  @Test
  void delete_withoutApiKey_returns401AndKeepsTheLink() {
    String code = createLink("https://example.com/kept");

    client.delete().uri(LINKS + "/" + code).exchange().expectStatus().isUnauthorized();

    assertThat(links.existsByCode(code)).isTrue();
  }

  private RestTestClient.ResponseSpec delete(String code) {
    return client.delete().uri(LINKS + "/" + code).header(API_KEY_HEADER, API_KEY).exchange();
  }

  private String createLink(String url) {
    LinkResponse body =
        client
            .post()
            .uri(LINKS)
            .header(API_KEY_HEADER, API_KEY)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateLinkRequest(url, null, null))
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
