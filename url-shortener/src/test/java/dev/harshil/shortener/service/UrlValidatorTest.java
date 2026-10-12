package dev.harshil.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harshil.shortener.domain.UrlValidationException;
import dev.harshil.shortener.domain.UrlValidationException.FieldError;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

  private final UrlValidator validator = new UrlValidator();

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
      delimiter = '|',
      value = {
        "https://example.com/path?q=1#frag | https://example.com/path?q=1#frag",
        "HTTPS://EXAMPLE.COM/Path/Keeps/Case | https://example.com/Path/Keeps/Case",
        "Http://Example.Com | http://example.com",
        "http://example.com:8080/a | http://example.com:8080/a",
        "https://sub.example.co.uk/a%20b?x=%2F | https://sub.example.co.uk/a%20b?x=%2F",
        "https://example.com?query=only | https://example.com?query=only",
        "https://example.com./trailing-dot | https://example.com./trailing-dot",
        "https://xn--bcher-kva.example/ | https://xn--bcher-kva.example/",
        "https://8.8.8.8/dns | https://8.8.8.8/dns",
        "http://172.32.0.1/just-outside-rfc1918 | http://172.32.0.1/just-outside-rfc1918",
        "http://11.0.0.1/public | http://11.0.0.1/public",
        "https://[2001:db8::1]/v6 | https://[2001:db8::1]/v6",
        "https://[2606:4700:4700::1111]:8443/x | https://[2606:4700:4700::1111]:8443/x",
        "'  https://example.com/trimmed  ' | https://example.com/trimmed",
      })
  void validate_acceptableUrl_returnsCanonicalForm(String raw, String canonical) {
    assertThat(validator.validate(raw)).isEqualTo(canonical);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t\n"})
  void validate_blankUrl_rejectsAsRequired(String raw) {
    assertRejected(raw, UrlValidator.MSG_REQUIRED);
  }

  @ParameterizedTest(name = "{0} -> {1}")
  @MethodSource("rejectedUrls")
  void validate_unacceptableUrl_rejectsWithOneFixedMessage(String raw, String message) {
    assertRejected(raw, message);
  }

  static Stream<Arguments> rejectedUrls() {
    return Stream.of(
        Arguments.of("not a url", UrlValidator.MSG_MALFORMED),
        Arguments.of("http://exa mple.com/", UrlValidator.MSG_MALFORMED),
        Arguments.of("http://[::1/", UrlValidator.MSG_MALFORMED),
        Arguments.of("example.com/path", UrlValidator.MSG_SCHEME),
        Arguments.of("//example.com/path", UrlValidator.MSG_SCHEME),
        Arguments.of("/relative/path", UrlValidator.MSG_SCHEME),
        Arguments.of("ftp://example.com/file", UrlValidator.MSG_SCHEME),
        Arguments.of("javascript:alert(1)", UrlValidator.MSG_SCHEME),
        Arguments.of("mailto:someone@example.com", UrlValidator.MSG_SCHEME),
        Arguments.of("file:///etc/passwd", UrlValidator.MSG_SCHEME),
        Arguments.of("data:text/html,hi", UrlValidator.MSG_SCHEME),
        Arguments.of("http://user:pass@example.com/", UrlValidator.MSG_USER_INFO),
        Arguments.of("https://user@example.com/", UrlValidator.MSG_USER_INFO),
        Arguments.of("http:///path-without-host", UrlValidator.MSG_HOST),
        Arguments.of("http://exa_mple.com/", UrlValidator.MSG_HOST),
        Arguments.of("http://2130706433/", UrlValidator.MSG_HOST),
        Arguments.of("http://2130706433./", UrlValidator.MSG_HOST),
        Arguments.of("http://0x7f000001/", UrlValidator.MSG_HOST),
        Arguments.of("http://0x7f.0.0.1/", UrlValidator.MSG_HOST),
        Arguments.of("http://012.0.0.1/", UrlValidator.MSG_HOST),
        Arguments.of("http://0177.0.0.1/", UrlValidator.MSG_HOST),
        Arguments.of("http://127.1/", UrlValidator.MSG_HOST),
        Arguments.of("http://example.123/", UrlValidator.MSG_HOST),
        Arguments.of("http://256.1.1.1/", UrlValidator.MSG_HOST),
        Arguments.of("http://localhost/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://LOCALHOST:8080/x", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://localhost./", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of(
            "http://metadata.google.internal/computeMetadata/v1/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://127.0.0.1/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://127.255.255.254/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://10.0.0.1/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://172.16.0.1/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://172.31.255.255/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://192.168.1.1/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://169.254.169.254/latest/meta-data/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://169.254.0.1/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://0.0.0.0/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://0.1.2.3/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://224.0.0.1/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://239.255.255.255/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[::1]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[::]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[fc00::1]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[fdab:1234::1]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[fe80::1]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[febf::1]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[ff02::1]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[::ffff:127.0.0.1]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[::ffff:10.0.0.1]/", UrlValidator.MSG_BLOCKED_HOST),
        Arguments.of("http://[::ffff:169.254.169.254]/", UrlValidator.MSG_BLOCKED_HOST));
  }

  @Test
  void validate_urlOfExactlyMaxLength_isAccepted() {
    String prefix = "https://example.com/";
    String raw = prefix + "a".repeat(UrlValidator.MAX_LENGTH - prefix.length());

    assertThat(validator.validate(raw)).hasSize(UrlValidator.MAX_LENGTH);
  }

  @Test
  void validate_urlOneCharacterOverMaxLength_isRejectedAsTooLong() {
    String prefix = "https://example.com/";
    String raw = prefix + "a".repeat(UrlValidator.MAX_LENGTH - prefix.length() + 1);

    assertRejected(raw, UrlValidator.MSG_TOO_LONG);
  }

  @Test
  void validate_lengthIsMeasuredAfterCanonicalisation_notOnTheRawInput() {
    String prefix = "https://example.com/";
    String padded = "   " + prefix + "a".repeat(UrlValidator.MAX_LENGTH - prefix.length()) + "   ";

    assertThat(validator.validate(padded)).hasSize(UrlValidator.MAX_LENGTH);
  }

  @Test
  void validate_hostName_isNeverResolved_soAnUnresolvableNamePasses() {
    String raw = "https://this-name-does-not-exist.invalid/path";

    assertThat(validator.validate(raw)).isEqualTo(raw);
  }

  private void assertRejected(String raw, String message) {
    assertThatThrownBy(() -> validator.validate(raw))
        .isInstanceOfSatisfying(
            UrlValidationException.class,
            ex ->
                assertThat(ex.getErrors())
                    .containsExactly(new FieldError(UrlValidator.FIELD, message)));
  }
}
