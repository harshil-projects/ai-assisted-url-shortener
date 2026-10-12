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

  private static final String CREATE_PATH = "/api/v1/links";

  /** The redirect path as springdoc renders it, with the code pattern stripped. */
  private static final String REDIRECT_PATH = "/{code}";

  private static final String SCHEMA_REF = "#/components/schemas/" + PROBLEM_SCHEMA;
  private static final String RETRY_AFTER_DESCRIPTION = "Seconds to wait before retrying";

  /** Shared by every operation: the unexpected error and the database outage. */
  private static final List<CatalogueEntry> SHARED_ENTRIES =
      List.of(
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

  private static final CatalogueEntry NOT_FOUND_ENTRY =
      new CatalogueEntry("404", "Link not found", null, null);

  private static final CatalogueEntry GONE_ENTRY =
      new CatalogueEntry(
          GONE,
          "Link has expired; the row and its click history are kept",
          HttpHeaders.CACHE_CONTROL,
          "Always no-store");

  /** Create (design 3.3): validation, key, alias conflict, body size, media type, rate limit. */
  private static final List<CatalogueEntry> CREATE_ENTRIES =
      sorted(
          new CatalogueEntry(
              "400", "Request validation failed; errors lists the offending fields", null, null),
          UNAUTHORIZED_ENTRY,
          new CatalogueEntry("409", "Alias already in use", null, null),
          new CatalogueEntry("413", "Request body too large; the limit is 16 KiB", null, null),
          new CatalogueEntry("415", "Request body must be application/json", null, null),
          new CatalogueEntry(
              "429", "Too many requests", HttpHeaders.RETRY_AFTER, RETRY_AFTER_DESCRIPTION));

  /** Metadata, statistics and delete (design 3.3): key and unknown code. */
  private static final List<CatalogueEntry> MANAGEMENT_ENTRIES =
      sorted(UNAUTHORIZED_ENTRY, NOT_FOUND_ENTRY);

  /** The public redirect and probe (design 3.3): unknown code and expiry. */
  private static final List<CatalogueEntry> REDIRECT_ENTRIES = sorted(NOT_FOUND_ENTRY, GONE_ENTRY);

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
    item.readOperationsMap()
        .forEach(
            (method, operation) -> {
              ApiResponses responses = responsesOf(operation);
              for (CatalogueEntry entry : catalogueFor(path, method)) {
                responses.putIfAbsent(entry.status(), entry.toResponse());
              }
            });
  }

  /**
   * The catalogue is per operation so the export lists exactly the statuses an endpoint produces:
   * the create operation carries the conflict, size, media-type and rate-limit cases, the
   * management operations carry the key and unknown-code cases, and the public redirect carries
   * unknown code and expiry. The two shared entries close every list.
   */
  private static List<CatalogueEntry> catalogueFor(String path, PathItem.HttpMethod method) {
    if (REDIRECT_PATH.equals(path)) {
      return REDIRECT_ENTRIES;
    }
    if (CREATE_PATH.equals(path) && method == PathItem.HttpMethod.POST) {
      return CREATE_ENTRIES;
    }
    return MANAGEMENT_ENTRIES;
  }

  private static List<CatalogueEntry> sorted(CatalogueEntry... specific) {
    return Stream.concat(Stream.of(specific), SHARED_ENTRIES.stream())
        .sorted(Comparator.comparing(CatalogueEntry::status))
        .toList();
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
