package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.info.BuildProperties;

class OpenApiConfigTest {

  private final OpenApiConfig config = new OpenApiConfig();

  @Test
  void urlShortenerOpenApi_withBuildInfo_usesTheBuildVersion() {
    OpenAPI openApi =
        config.urlShortenerOpenApi(
            buildFactory(buildProperties("1.2.3")).getBeanProvider(BuildProperties.class));

    assertThat(openApi.getInfo().getTitle()).isEqualTo(OpenApiConfig.TITLE);
    assertThat(openApi.getInfo().getVersion()).isEqualTo("1.2.3");
    assertThat(openApi.getInfo().getDescription()).contains("redirects").contains("public");
  }

  @Test
  void urlShortenerOpenApi_declaresOnlyARelativeServerSoTheExportIsPortIndependent() {
    OpenAPI openApi =
        config.urlShortenerOpenApi(
            new DefaultListableBeanFactory().getBeanProvider(BuildProperties.class));

    assertThat(openApi.getServers())
        .singleElement()
        .satisfies(server -> assertThat(server.getUrl()).isEqualTo("/"));
  }

  @Test
  void urlShortenerOpenApi_withoutBuildInfo_fallsBackToTheFixedVersion() {
    OpenAPI openApi =
        config.urlShortenerOpenApi(
            new DefaultListableBeanFactory().getBeanProvider(BuildProperties.class));

    assertThat(openApi.getInfo().getVersion()).isEqualTo(OpenApiConfig.FALLBACK_VERSION);
  }

  @Test
  void urlShortenerOpenApi_buildInfoWithoutVersion_fallsBackToTheFixedVersion() {
    OpenAPI openApi =
        config.urlShortenerOpenApi(
            buildFactory(new BuildProperties(new Properties()))
                .getBeanProvider(BuildProperties.class));

    assertThat(openApi.getInfo().getVersion()).isEqualTo(OpenApiConfig.FALLBACK_VERSION);
  }

  @Test
  void urlShortenerOpenApi_declaresApiKeyHeaderSchemeAndRequiresItGlobally() {
    OpenAPI openApi =
        config.urlShortenerOpenApi(
            new DefaultListableBeanFactory().getBeanProvider(BuildProperties.class));

    SecurityScheme scheme =
        openApi.getComponents().getSecuritySchemes().get(OpenApiConfig.SECURITY_SCHEME);
    assertThat(scheme.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
    assertThat(scheme.getIn()).isEqualTo(SecurityScheme.In.HEADER);
    assertThat(scheme.getName()).isEqualTo(OpenApiConfig.API_KEY_HEADER);
    assertThat(openApi.getSecurity())
        .singleElement()
        .satisfies(
            requirement -> assertThat(requirement).containsOnlyKeys(OpenApiConfig.SECURITY_SCHEME));
  }

  private static DefaultListableBeanFactory buildFactory(BuildProperties buildProperties) {
    DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
    beanFactory.registerSingleton("buildProperties", buildProperties);
    return beanFactory;
  }

  private static BuildProperties buildProperties(String version) {
    Properties entries = new Properties();
    entries.setProperty("version", version);
    return new BuildProperties(entries);
  }
}
