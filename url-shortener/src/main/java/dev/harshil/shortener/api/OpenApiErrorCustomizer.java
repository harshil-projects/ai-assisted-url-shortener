package dev.harshil.shortener.api;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Adds the error catalogue of design section 3.4 to every operation of the generated document.
 *
 * <p>Controllers declare only their success responses. The failure modes produced by the filters,
 * the authentication entry point and the exception handler are the same everywhere, so they are
 * added here once instead of being repeated in annotations that drift. Every error response points
 * at one shared {@code ProblemDetail} schema. Operations under {@code /api/} additionally get the
 * 401; the redirect operations under {@code /{code}} additionally get the 410. A response an
 * operation already declares for a status is left untouched, so an author can still document a
 * specific case.
 */
@Component
public class OpenApiErrorCustomizer implements OpenApiCustomizer {

  /** Name of the shared schema under {@code components.schemas}. */
  public static final String PROBLEM_SCHEMA = "ProblemDetail";

  static final String PROBLEM_MEDIA_TYPE = "application/problem+json";
  static final String UNAUTHORIZED = "401";
  static final String GONE = "410";

  private static final String API_PREFIX = "/api/";

  /** The redirect path as springdoc renders it, with the code pattern stripped. */
  private static final String REDIRECT_PATH = "/{code}";

  private static final String SCHEMA_REF = "#/components/schemas/" + PROBLEM_SCHEMA;
  private static final String RETRY_AFTER_DESCRIPTION = "Seconds to wait before retrying";

  /** Added to every operation, in document order. */
  private static final List<CatalogueEntry> COMMON_ENTRIES =
      List.of(
          new CatalogueEntry(
              "400", "Request validation failed; errors lists the offending fields", null, null),
          new CatalogueEntry("404", "Link not found", null, null),
          new CatalogueEntry(
              "429", "Too many requests", HttpHeaders.RETRY_AFTER, RETRY_AFTER_DESCRIPTION),
          new CatalogueEntry("500", "Internal error; no stack trace is exposed", null, null),
          new CatalogueEntry(
              "503",
              "Service temporarily unavailable; the database could not be reached",
              HttpHeaders.RETRY_AFTER,
              RETRY_AFTER_DESCRIPTION));

  private static final CatalogueEntry UNAUTHORIZED_ENTRY =
      new CatalogueEntry(
          UNAUTHORIZED,
          "Missing or invalid API key",
          HttpHeaders.WWW_AUTHENTICATE,
          "Challenge, always ApiKey realm=\"url-shortener\"");

  private static final CatalogueEntry GONE_ENTRY =
      new CatalogueEntry(
          GONE,
          "Link has expired; the row and its click history are kept",
          HttpHeaders.CACHE_CONTROL,
          "Always no-store");

  /** The redirect's catalogue: the common entries plus the 410, kept in status order. */
  private static final List<CatalogueEntry> REDIRECT_ENTRIES =
      Stream.concat(COMMON_ENTRIES.stream(), Stream.of(GONE_ENTRY))
          .sorted(Comparator.comparing(CatalogueEntry::status))
          .toList();

  @Override
  public void customise(OpenAPI openApi) {
    components(openApi).addSchemas(PROBLEM_SCHEMA, problemSchema());
    if (openApi.getPaths() == null) {
      return;
    }
    openApi.getPaths().forEach(OpenApiErrorCustomizer::addErrors);
  }

  private static Components components(OpenAPI openApi) {
    if (openApi.getComponents() == null) {
      openApi.components(new Components());
    }
    return openApi.getComponents();
  }

  private static void addErrors(String path, PathItem item) {
    for (Operation operation : item.readOperations()) {
      ApiResponses responses = responsesOf(operation);
      if (path.startsWith(API_PREFIX)) {
        responses.putIfAbsent(UNAUTHORIZED_ENTRY.status(), UNAUTHORIZED_ENTRY.toResponse());
      }
      for (CatalogueEntry entry : catalogueFor(path)) {
        responses.putIfAbsent(entry.status(), entry.toResponse());
      }
    }
  }

  private static List<CatalogueEntry> catalogueFor(String path) {
    return REDIRECT_PATH.equals(path) ? REDIRECT_ENTRIES : COMMON_ENTRIES;
  }

  private static ApiResponses responsesOf(Operation operation) {
    if (operation.getResponses() == null) {
      operation.setResponses(new ApiResponses());
    }
    return operation.getResponses();
  }

  private static Schema<?> problemSchema() {
    return new ObjectSchema()
        .description("RFC 9457 Problem Details; every error body of this service has this shape.")
        .addProperty(
            "type",
            new StringSchema()
                .format("uri-reference")
                .description(
                    "Problem type relative to the service origin, e.g. /problems/link-not-found"))
        .addProperty("title", new StringSchema().description("Short, stable summary of the type"))
        .addProperty("status", new IntegerSchema().description("HTTP status code"))
        .addProperty(
            "detail",
            new StringSchema().description("Human-readable explanation of this occurrence"))
        .addProperty(
            "instance",
            new StringSchema().format("uri-reference").description("Request path, when known"))
        .addProperty(
            "requestId",
            new StringSchema().description("Correlation id, equal to the X-Request-Id header"))
        .addProperty(
            "timestamp",
            new StringSchema().format("date-time").description("When the problem was produced"))
        .addProperty(
            "errors",
            new ArraySchema()
                .items(fieldErrorSchema())
                .description("Only on 400: the fields that failed validation"))
        .required(List.of("type", "status"));
  }

  private static Schema<?> fieldErrorSchema() {
    return new ObjectSchema()
        .addProperty("field", new StringSchema().description("Request field that failed"))
        .addProperty("message", new StringSchema().description("Why it failed"));
  }

  /** One row of the error catalogue: the status, its description and an optional header. */
  private record CatalogueEntry(
      String status, String description, String headerName, String headerDescription) {

    ApiResponse toResponse() {
      ApiResponse response =
          new ApiResponse()
              .description(description)
              .content(
                  new Content()
                      .addMediaType(
                          PROBLEM_MEDIA_TYPE,
                          new MediaType().schema(new Schema<>().$ref(SCHEMA_REF))));
      if (headerName != null) {
        response.addHeaderObject(
            headerName, new Header().description(headerDescription).schema(new StringSchema()));
      }
      return response;
    }
  }
}
