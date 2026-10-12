package dev.harshil.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LinkTest {

  private static final Instant CREATED = Instant.parse("2026-10-12T09:30:00Z");

  @Test
  void equals_sameCodeDifferentUrlAndTime_areEqualWithSameHash() {
    Link first = new Link("abcdefg", "https://example.com/one", CREATED);
    Link second = new Link("abcdefg", "https://example.org/two", CREATED.plusSeconds(60));

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  void equals_differentCode_areNotEqual() {
    Link first = new Link("abcdefg", "https://example.com/one", CREATED);
    Link second = new Link("hijklmn", "https://example.com/one", CREATED);

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void equals_selfNullAndForeignType_isReflexiveAndRejectsOthers() {
    Link link = new Link("abcdefg", "https://example.com/one", CREATED);

    assertThat(link).isEqualTo(link).isNotEqualTo(null).isNotEqualTo("abcdefg");
  }

  @Test
  void toString_neverContainsTheTargetUrl() {
    Link link = new Link("abcdefg", "https://secret.example.com/path?token=abc", CREATED);

    assertThat(link.toString())
        .contains("abcdefg")
        .contains(CREATED.toString())
        .doesNotContain("secret.example.com")
        .doesNotContain("token");
  }
}
