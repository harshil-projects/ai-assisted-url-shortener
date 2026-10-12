package dev.harshil.shortener.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.LinkRepository;
import dev.harshil.shortener.support.TestProfile;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/** The public redirect over HTTP; the test client does not follow redirects. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class RedirectIT {

  private static final String TARGET = "https://example.com/landing?utm=short";

  @Autowired private RestTestClient client;
  @Autowired private ClickEventRepository clickEvents;
  @MockitoSpyBean private LinkRepository links;

  @AfterEach
  void cleanTables() {
    clickEvents.deleteAllInBatch();
    links.deleteAllInBatch();
  }

  @Test
  void get_knownCode_returns302WithStoredLocationAndNoStore() {
    links.save(new Link("redir01", TARGET, Instant.now(), null));

    client
        .get()
        .uri("/redir01")
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, TARGET)
        .expectHeader()
        .valueEquals(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  @Test
  void head_knownCode_returns302WithTheSameLocation() {
    links.save(new Link("probe01", TARGET, Instant.now(), null));

    client
        .head()
        .uri("/probe01")
        .exchange()
        .expectStatus()
        .isFound()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, TARGET);
  }

  @Test
  void get_unknownCode_returns404LinkNotFoundProblem() {
    client
        .get()
        .uri("/unknown1")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/link-not-found")
        .jsonPath("$.status")
        .isEqualTo(404)
        .jsonPath("$.timestamp")
        .exists();
  }

  @Test
  void get_faviconIco_returns404WithoutTouchingTheRepository() {
    client.get().uri("/favicon.ico").exchange().expectStatus().isNotFound();

    verify(links, never()).findByCode(anyString());
  }

  @Test
  void get_codeShorterThanThePattern_returns404WithoutTouchingTheRepository() {
    client
        .get()
        .uri("/abc")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.detail")
        .isEqualTo("No resource at this path");

    verify(links, never()).findByCode(anyString());
  }
}
