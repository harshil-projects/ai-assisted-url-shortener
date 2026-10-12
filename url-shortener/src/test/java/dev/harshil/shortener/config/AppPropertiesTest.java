package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AppPropertiesTest {

  private static final String[] VALID_EXCEPT_KEY = {
    "app.base-url=https://sho.rt",
    "app.ratelimit.create.capacity=5",
    "app.ratelimit.create.period=30s",
    "app.request.max-bytes=1024",
    "app.security.actuator-requires-api-key=false"
  };

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(PropertiesHolder.class);

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void hasBaseUrl_isFalseForNullEmptyOrBlank(String baseUrl) {
    assertThat(properties(baseUrl).hasBaseUrl()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://sho.rt", "http://localhost:9090"})
  void hasBaseUrl_isTrueWhenConfigured(String baseUrl) {
    assertThat(properties(baseUrl).hasBaseUrl()).isTrue();
  }

  @Test
  void binding_validPropertySet_bindsEveryGroup() {
    runner
        .withPropertyValues(VALID_EXCEPT_KEY)
        .withPropertyValues("app.api-key=secret")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              AppProperties bound = context.getBean(AppProperties.class);
              assertThat(bound.apiKey()).isEqualTo("secret");
              assertThat(bound.baseUrl()).isEqualTo("https://sho.rt");
              assertThat(bound.ratelimit().create())
                  .isEqualTo(new AppProperties.RateLimit.Create(5, Duration.ofSeconds(30)));
              assertThat(bound.request().maxBytes()).isEqualTo(1024);
              assertThat(bound.security().actuatorRequiresApiKey()).isFalse();
            });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   "})
  void binding_blankApiKey_failsValidationNamingTheField(String blankKey) {
    runner
        .withPropertyValues(VALID_EXCEPT_KEY)
        .withPropertyValues("app.api-key=" + blankKey)
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(BindValidationException.class)
                  .hasMessageContaining("apiKey");
            });
  }

  @Test
  void binding_nonPositiveCapacity_failsValidation() {
    runner
        .withPropertyValues(
            "app.api-key=secret",
            "app.ratelimit.create.capacity=0",
            "app.ratelimit.create.period=30s",
            "app.request.max-bytes=1024",
            "app.security.actuator-requires-api-key=false")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .rootCause()
                  .isInstanceOf(BindValidationException.class)
                  .hasMessageContaining("capacity");
            });
  }

  private static AppProperties properties(String baseUrl) {
    return new AppProperties(
        "key",
        baseUrl,
        new AppProperties.RateLimit(new AppProperties.RateLimit.Create(20, Duration.ofMinutes(1))),
        new AppProperties.Request(16_384),
        new AppProperties.Security(true));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(AppProperties.class)
  static class PropertiesHolder {}
}
