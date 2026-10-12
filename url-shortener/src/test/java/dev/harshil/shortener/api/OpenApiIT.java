package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.harshil.shortener.support.TestProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Keeps the committed OpenAPI export honest (FR-12, design 4.1).
 *
 * <p>{@code docs/reference/openapi.yaml} is what reviewers and client generators read; it must be
 * byte for byte what the running service serves at {@code /v3/api-docs.yaml}, line endings aside.
 * Run with {@code -Dopenapi.update=true} the test rewrites the file instead of comparing, which is
 * the one sanctioned way to change it. Until the file exists, the comparison is skipped with an
 * assumption rather than failed, so a fresh checkout still builds.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class OpenApiIT {

  /** Surefire runs with the module directory as working directory; the docs live one level up. */
  private static final Path COMMITTED_SPEC =
      Path.of("..", "docs", "reference", "openapi.yaml").toAbsolutePath().normalize();

  private static final String UPDATE_PROPERTY = "openapi.update";
  private static final String UPDATE_COMMAND =
      "./mvnw.cmd -pl url-shortener -Dtest=OpenApiIT -Dopenapi.update=true test";

  @Autowired private RestTestClient client;

  @Test
  void apiDocsYaml_isPublicAndDescribesThisService() {
    String runtime = fetchRuntimeSpec();

    assertThat(runtime)
        .contains("openapi: 3.1")
        .contains("title: URL Shortener API")
        .contains("ProblemDetail:")
        .contains("ApiKey:")
        .contains("name: X-API-Key")
        // The 3.1 writer takes schema types from Schema.types, not Schema.type; a schema built
        // without them would silently lose its type, so the shared schema's types are checked.
        .contains("type: object")
        .contains("type: integer")
        .contains("type: string")
        // Only the relative server declared by OpenApiConfig; springdoc's generated absolute URL
        // would carry this test's random port and break the committed-spec comparison.
        .contains("url: /")
        .doesNotContain("Generated server url")
        .doesNotContain("localhost");
  }

  @Test
  void committedSpec_matchesRuntime() throws IOException {
    String runtime = fetchRuntimeSpec();
    if (Boolean.getBoolean(UPDATE_PROPERTY)) {
      Files.createDirectories(COMMITTED_SPEC.getParent());
      Files.writeString(COMMITTED_SPEC, runtime);
      return;
    }
    assumeTrue(
        Files.exists(COMMITTED_SPEC),
        () -> COMMITTED_SPEC + " does not exist yet; generate it with: " + UPDATE_COMMAND);

    String committed = normaliseLineEndings(Files.readString(COMMITTED_SPEC));

    assertThat(runtime)
        .as(
            "docs/reference/openapi.yaml no longer matches the running service. Re-export it with:"
                + " %s and commit the result together with the code change.",
            UPDATE_COMMAND)
        .isEqualTo(committed);
  }

  private String fetchRuntimeSpec() {
    String body =
        client
            .get()
            .uri("/v3/api-docs.yaml")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();
    assertThat(body).isNotBlank();
    return normaliseLineEndings(body);
  }

  private static String normaliseLineEndings(String text) {
    return text.replace("\r\n", "\n");
  }
}
