package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AppPropertiesTest {

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

  private static AppProperties properties(String baseUrl) {
    return new AppProperties(
        "key",
        baseUrl,
        new AppProperties.RateLimit(new AppProperties.RateLimit.Create(20, Duration.ofMinutes(1))),
        new AppProperties.Request(16_384),
        new AppProperties.Security(true));
  }
}
