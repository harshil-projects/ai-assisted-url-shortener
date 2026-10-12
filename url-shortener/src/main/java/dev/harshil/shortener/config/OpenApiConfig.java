package dev.harshil.shortener.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Top-level OpenAPI document: title, version, description and the API key scheme (design 3.3,
 * FR-12).
 *
 * <p>The {@code ApiKey} security scheme is declared once and required globally, which is what makes
 * Swagger UI show its Authorize button and send {@code X-API-Key} on try-it-out calls. The
 * description states that redirects, health probes and the document itself are public, because the
 * global requirement alone would suggest otherwise. The version comes from the build info the Maven
 * plugin writes; without it (an IDE run that skipped the goal) a fixed fallback keeps the document
 * deterministic for the drift test.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

  static final String TITLE = "URL Shortener API";
  static final String SECURITY_SCHEME = "ApiKey";
  static final String API_KEY_HEADER = "X-API-Key";
  static final String FALLBACK_VERSION = "0.1.0";

  private static final String DESCRIPTION =
      "Create short links, redirect on them and read their click statistics. "
          + "Management operations under /api/v1 require the X-API-Key header; redirects "
          + "(GET /{code}), health probes and this document are public. Every error is an RFC 9457 "
          + "Problem Details body (application/problem+json) whose requestId equals the "
          + "X-Request-Id response header.";

  /**
   * A relative server URL keeps the document identical wherever it is served. Without it springdoc
   * writes the absolute URL of the current request, including the random port of a test, and the
   * committed export would never match the runtime.
   */
  static final String RELATIVE_SERVER_URL = "/";

  @Bean
  OpenAPI urlShortenerOpenApi(ObjectProvider<BuildProperties> buildProperties) {
    return new OpenAPI()
        .info(
            new Info()
                .title(TITLE)
                .version(resolveVersion(buildProperties))
                .description(DESCRIPTION))
        .servers(
            List.of(
                new Server()
                    .url(RELATIVE_SERVER_URL)
                    .description("The origin this document was served from")))
        .components(new Components().addSecuritySchemes(SECURITY_SCHEME, apiKeyScheme()))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
  }

  private static String resolveVersion(ObjectProvider<BuildProperties> buildProperties) {
    BuildProperties build = buildProperties.getIfAvailable();
    if (build == null || build.getVersion() == null) {
      return FALLBACK_VERSION;
    }
    return build.getVersion();
  }

  private static SecurityScheme apiKeyScheme() {
    return new SecurityScheme()
        .type(SecurityScheme.Type.APIKEY)
        .in(SecurityScheme.In.HEADER)
        .name(API_KEY_HEADER)
        .description(
            "Shared API key. Required on /api/v1/** and, in production, on the actuator "
                + "endpoints other than health.");
  }
}
