package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.api.dto.CreateLinkRequest;
import dev.harshil.shortener.api.dto.LinkResponse;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.LinkRepository;
import dev.harshil.shortener.support.TestProfile;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/** Create, read and delete links over HTTP against the full application context. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class LinkFlowIT {

  private static final String API_KEY_HEADER = "X-API-Key";
  private static final String API_KEY = "test-api-key";
  private static final String LINKS = "/api/v1/links";

  @Autowired private RestTestClient client;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clickEvents;
  @Autowired private MeterRegistry registry;

  @AfterEach
  void cleanTables() {
    clickEvents.deleteAllInBatch();
    links.deleteAllInBatch();
  }

  @Test
  void create_validUrl_returns201WithLocationSevenCharacterCodeAndBody() {
    create(new CreateLinkRequest("HTTPS://Example.COM/Landing?x=1", null, null))
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueMatches(HttpHeaders.LOCATION, LINKS + "/[A-Za-z0-9]{7}")
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.code")
        .value(String.class, code -> assertThat(code).matches("[A-Za-z0-9]{7}"))
        .jsonPath("$.url")
        .isEqualTo("https://example.com/Landing?x=1")
        .jsonPath("$.shortUrl")
        .value(
            String.class,
            shortUrl -> assertThat(shortUrl).matches("http://localhost:\\d+/[A-Za-z0-9]{7}"))
        .jsonPath("$.createdAt")
        .exists();
  }

  @Test
  void create_customAlias_isHonouredAsTheCode() {
    create(new CreateLinkRequest("https://example.com/alias", "my-Alias_1", null))
        .expectStatus()
        .isCreated()
        .expectHeader()
        .valueEquals(HttpHeaders.LOCATION, LINKS + "/my-Alias_1")
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("my-Alias_1");
  }

  @Test
  void create_reservedAlias_returns400ValidationFailedOnCustomAlias() {
    create(new CreateLinkRequest("https://example.com/x", "actuator", null))
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/validation-failed")
        .jsonPath("$.errors[0].field")
        .isEqualTo("customAlias")
        .jsonPath("$.timestamp")
        .exists();
  }

  @Test
  void create_duplicateAlias_returns409AliasTaken() {
    create(new CreateLinkRequest("https://example.com/one", "dupeAlias", null))
        .expectStatus()
        .isCreated();

    create(new CreateLinkRequest("https://example.com/two", "dupeAlias", null))
        .expectStatus()
        .isEqualTo(409)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/alias-taken")
        .jsonPath("$.status")
        .isEqualTo(409);
  }

  @Test
  void create_invalidUrl_returns400WithUrlFieldError() {
    create(new CreateLinkRequest("http://127.0.0.1/admin", null, null))
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/validation-failed")
        .jsonPath("$.errors[0].field")
        .isEqualTo("url")
        .jsonPath("$.errors[0].message")
        .isNotEmpty();
  }

  @Test
  void create_withoutApiKey_returns401AndStoresNothing() {
    client
        .post()
        .uri(LINKS)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new CreateLinkRequest("https://example.com/x", null, null))
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/unauthorized");

    assertThat(links.count()).isZero();
  }

  @Test
  void create_wrongContentType_returns415UnsupportedMediaType() {
    client
        .post()
        .uri(LINKS)
        .header(API_KEY_HEADER, API_KEY)
        .contentType(MediaType.TEXT_PLAIN)
        .body("url=https://example.com")
        .exchange()
        .expectStatus()
        .isEqualTo(415)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/unsupported-media-type")
        .jsonPath("$.timestamp")
        .exists();
  }

  @Test
  void get_createdLink_returnsItsMetadata() {
    String code = createAndReadCode("https://example.com/meta", null);

    client
        .get()
        .uri(LINKS + "/" + code)
        .header(API_KEY_HEADER, API_KEY)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo(code)
        .jsonPath("$.url")
        .isEqualTo("https://example.com/meta");
  }

  @Test
  void delete_createdLink_returns204AndSubsequentGetIs404() {
    String code = createAndReadCode("https://example.com/gone", null);

    client
        .delete()
        .uri(LINKS + "/" + code)
        .header(API_KEY_HEADER, API_KEY)
        .exchange()
        .expectStatus()
        .isNoContent();

    client
        .get()
        .uri(LINKS + "/" + code)
        .header(API_KEY_HEADER, API_KEY)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.type")
        .isEqualTo("/problems/link-not-found");
  }

  @Test
  void create_success_incrementsTheLinksCreatedCounter() {
    double before = registry.counter("shortener.links.created").count();

    create(new CreateLinkRequest("https://example.com/counted", null, null))
        .expectStatus()
        .isCreated();

    assertThat(registry.counter("shortener.links.created").count()).isEqualTo(before + 1);
  }

  private RestTestClient.ResponseSpec create(CreateLinkRequest request) {
    return client
        .post()
        .uri(LINKS)
        .header(API_KEY_HEADER, API_KEY)
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange();
  }

  private String createAndReadCode(String url, String alias) {
    LinkResponse body =
        create(new CreateLinkRequest(url, alias, null))
            .expectStatus()
            .isCreated()
            .expectBody(LinkResponse.class)
            .returnResult()
            .getResponseBody();
    assertThat(body).isNotNull();
    return body.code();
  }
}
