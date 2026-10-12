package dev.harshil.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ReservedWordsTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "api",
        "actuator",
        "health",
        "metrics",
        "prometheus",
        "swagger-ui",
        "v3",
        "api-docs",
        "error",
        "favicon.ico",
        "robots.txt",
        ".well-known",
        "static",
        "assets",
        "stats",
        "admin",
        "login",
        "h2-console"
      })
  void isReserved_everyWordOnTheDesignList_isTrue(String word) {
    assertThat(ReservedWords.isReserved(word)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"API", "Admin", "SWAGGER-UI", "Favicon.ICO", "H2-Console"})
  void isReserved_mixedCaseVariant_isTrue(String word) {
    assertThat(ReservedWords.isReserved(word)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"abcd", "apis", "my-api", "api2", "stat", "healthz", "admin1", "login_"})
  void isReserved_similarButDifferentAlias_isFalse(String alias) {
    assertThat(ReservedWords.isReserved(alias)).isFalse();
  }

  @ParameterizedTest
  @NullAndEmptySource
  void isReserved_nullOrEmpty_isFalse(String alias) {
    assertThat(ReservedWords.isReserved(alias)).isFalse();
  }
}
