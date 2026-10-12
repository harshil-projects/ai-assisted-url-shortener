package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class OpenApiErrorCustomizerTest {

  private final OpenApiErrorCustomizer customizer = new OpenApiErrorCustomizer();

  @Test
  void customise_apiOperation_getsTheCatalogueIncluding401() {
    Operation create =
        new Operation()
            .responses(
                new ApiResponses().addApiResponse("201", new ApiResponse().description("Created")));
    OpenAPI openApi = documentWith("/api/v1/links", new PathItem().post(create));

    customizer.customise(openApi);

    assertThat(create.getResponses().keySet())
        .containsExactly("201", "400", "401", "409", "413", "415", "429", "500", "503");
    assertThat(create.getResponses().get("401").getHeaders())
        .containsKey(HttpHeaders.WWW_AUTHENTICATE);
    assertThat(create.getResponses().get("429").getHeaders()).containsKey(HttpHeaders.RETRY_AFTER);
    assertThat(create.getResponses().get("503").getHeaders()).containsKey(HttpHeaders.RETRY_AFTER);
    assertThat(create.getResponses().get("409").getHeaders()).isNull();
    assertThat(create.getResponses()).doesNotContainKey("404");
  }

  @Test
  void customise_publicOperation_getsTheCatalogueWithout401() {
    Operation redirect = new Operation();
    OpenAPI openApi = documentWith("/{code}", new PathItem().get(redirect));

    customizer.customise(openApi);

    assertThat(redirect.getResponses().keySet()).containsExactly("404", "410", "500", "503");
  }

  @Test
  void customise_redirectOperations_get410WithNoStoreHeaderOnGetAndHead() {
    Operation redirect = new Operation();
    Operation probe = new Operation();
    OpenAPI openApi = documentWith("/{code}", new PathItem().get(redirect).head(probe));

    customizer.customise(openApi);

    assertThat(redirect.getResponses().get("410").getHeaders())
        .containsKey(HttpHeaders.CACHE_CONTROL);
    assertThat(probe.getResponses().get("410").getHeaders()).containsKey(HttpHeaders.CACHE_CONTROL);
  }

  @Test
  void customise_managementOperation_doesNotGet410() {
    Operation lookup = new Operation();
    OpenAPI openApi = documentWith("/api/v1/links/{code}", new PathItem().get(lookup));

    customizer.customise(openApi);

    assertThat(lookup.getResponses().keySet()).containsExactly("401", "404", "500", "503");
  }

  @Test
  void customise_everyAddedResponse_referencesTheSharedProblemSchemaAsProblemJson() {
    Operation create = new Operation();
    OpenAPI openApi = documentWith("/api/v1/links", new PathItem().post(create));

    customizer.customise(openApi);

    assertThat(create.getResponses().values())
        .allSatisfy(
            response -> {
              assertThat(response.getDescription()).isNotBlank();
              Schema<?> schema =
                  response.getContent().get(OpenApiErrorCustomizer.PROBLEM_MEDIA_TYPE).getSchema();
              assertThat(schema.get$ref())
                  .isEqualTo("#/components/schemas/" + OpenApiErrorCustomizer.PROBLEM_SCHEMA);
            });
  }

  @Test
  void customise_responseAlreadyDeclaredForStatus_isLeftUntouched() {
    ApiResponse authored = new ApiResponse().description("Unknown code, this one is specific");
    Operation lookup =
        new Operation().responses(new ApiResponses().addApiResponse("404", authored));
    OpenAPI openApi = documentWith("/api/v1/links/{code}", new PathItem().get(lookup));

    customizer.customise(openApi);

    assertThat(lookup.getResponses().get("404")).isSameAs(authored);
  }

  @Test
  void customise_registersTheSharedProblemSchemaWithTheCatalogueMembers() {
    OpenAPI openApi = documentWith("/api/v1/links", new PathItem().post(new Operation()));

    customizer.customise(openApi);

    Schema<?> problem =
        openApi.getComponents().getSchemas().get(OpenApiErrorCustomizer.PROBLEM_SCHEMA);
    assertThat(problem.getProperties().keySet())
        .containsExactly(
            "type", "title", "status", "detail", "instance", "requestId", "timestamp", "errors");
    // JSON Schema "required" is a set; swagger-core stores it sorted, so order is not asserted.
    assertThat(problem.getRequired()).containsExactlyInAnyOrder("type", "status");
    Schema<?> errors = problem.getProperties().get("errors");
    assertThat(errors.getItems().getProperties().keySet()).containsExactly("field", "message");
  }

  @Test
  void customise_documentWithoutPathsOrComponents_onlyRegistersTheSchema() {
    OpenAPI bare = new OpenAPI();

    customizer.customise(bare);

    assertThat(bare.getComponents().getSchemas())
        .containsKey(OpenApiErrorCustomizer.PROBLEM_SCHEMA);
    assertThat(bare.getPaths()).isNull();
  }

  @Test
  void customise_appliedTwice_isIdempotent() {
    Operation create = new Operation();
    OpenAPI openApi = documentWith("/api/v1/links", new PathItem().post(create));

    customizer.customise(openApi);
    customizer.customise(openApi);

    assertThat(create.getResponses()).hasSize(8);
    assertThat(openApi.getComponents().getSchemas()).hasSize(1);
  }

  private static OpenAPI documentWith(String path, PathItem item) {
    return new OpenAPI().paths(new Paths().addPathItem(path, item));
  }
}
