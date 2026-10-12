package dev.harshil.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSanitizerTest {

  @Test
  void sanitize_null_returnsTheWordNull() {
    assertThat(LogSanitizer.sanitize(null)).isEqualTo("null");
  }

  @Test
  void sanitize_plainText_isReturnedUnchanged() {
    assertThat(LogSanitizer.sanitize("abc-DEF_123 example.com"))
        .isEqualTo("abc-DEF_123 example.com");
  }

  @Test
  void sanitize_carriageReturnAndLineFeed_areRemovedSoNoSecondLogLineCanBeForged() {
    String forged = "ok\r\n2026-10-12 ERROR fake line\ntail";

    assertThat(LogSanitizer.sanitize(forged)).isEqualTo("ok2026-10-12 ERROR fake linetail");
  }

  @Test
  void sanitize_otherControlCharacters_areRemoved() {
    String withControls =
        "a\tb\u0000c\u001bd\u007fe\u0085f"; // NUL, ESC, DEL, NEL are all control characters

    assertThat(LogSanitizer.sanitize(withControls)).isEqualTo("abcdef");
  }

  @Test
  void sanitize_nonControlUnicode_isKept() {
    assertThat(LogSanitizer.sanitize("café naïve ünïcode")).isEqualTo("café naïve ünïcode");
  }

  @Test
  void sanitize_longValue_isTruncatedToTwoHundredCharacters() {
    String longValue = "x".repeat(LogSanitizer.MAX_LENGTH + 50);

    assertThat(LogSanitizer.sanitize(longValue)).hasSize(LogSanitizer.MAX_LENGTH);
  }

  @Test
  void sanitize_valueOfExactlyMaxLength_isKeptWhole() {
    String exact = "y".repeat(LogSanitizer.MAX_LENGTH);

    assertThat(LogSanitizer.sanitize(exact)).isEqualTo(exact);
  }

  @Test
  void sanitize_controlCharactersDoNotCountTowardsTheLimit() {
    String value = "\n".repeat(10) + "z".repeat(LogSanitizer.MAX_LENGTH);

    assertThat(LogSanitizer.sanitize(value)).isEqualTo("z".repeat(LogSanitizer.MAX_LENGTH));
  }

  @Test
  void sanitize_emptyString_returnsEmptyString() {
    assertThat(LogSanitizer.sanitize("")).isEmpty();
  }
}
