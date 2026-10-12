package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harshil.shortener.UrlShortenerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;

/**
 * ADR 0007 fail-fast: the prod profile refuses to start without {@code APP_API_KEY}.
 *
 * <p>Uses a real {@code SpringApplication} with the prod property file so the check covers the
 * {@code app.api-key=${APP_API_KEY:}} wiring, not just the annotation. The servlet web type is kept
 * because filter registrations, which bind {@code AppProperties}, are created while the embedded
 * server starts, before any other singleton; the failure therefore surfaces in a fraction of a
 * second and never touches the database.
 */
class ProdStartupIT {

  @Test
  void prodProfile_withoutApiKey_failsStartupWithBindValidationErrorOnApiKey() {
    SpringApplicationBuilder prod =
        new SpringApplicationBuilder(UrlShortenerApplication.class)
            .web(WebApplicationType.SERVLET)
            .profiles("prod");

    assertThatThrownBy(
            () ->
                prod.run(
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:h2:mem:prodcheck;MODE=PostgreSQL",
                        // Command-line arguments outrank the OS environment, so the test does not
                        // depend on whether this machine happens to export APP_API_KEY.
                        "--APP_API_KEY=")
                    .close())
        .rootCause()
        .isInstanceOf(BindValidationException.class)
        .hasMessageContaining("apiKey");
  }
}
