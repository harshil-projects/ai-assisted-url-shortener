package dev.harshil.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every rule of {@link ClickAttribution#from(String, Map)} (T-202) plus the edge-case table of
 * T-203: case folding, trailing dots, IPv6 and userinfo in the referer, relative and opaque
 * references, over-long hosts, duplicate and empty parameters, whitespace, control characters and
 * non-ASCII text. Each row states the input and the single value that must come out.
 */
class ClickAttributionTest {

  private static final Map<String, String[]> NO_QUERY = Map.of();
  private static final String LONG_LABEL = "a".repeat(300);

  @Test
  void from_refererAndAllThreeUtmParameters_keepsTheLowerCasedHostAndTheTriple() {
    Map<String, String[]> query =
        query("utm_source", "newsletter", "utm_medium", "email", "utm_campaign", "oct");

    ClickAttribution attribution =
        ClickAttribution.from("https://News.Example.com/story?x=1", query);

    assertThat(attribution)
        .isEqualTo(new ClickAttribution("news.example.com", "newsletter", "email", "oct"));
  }

  @Test
  void from_nullRefererAndNullQuery_returnsNoneWithoutThrowing() {
    assertThat(ClickAttribution.from(null, null)).isEqualTo(ClickAttribution.NONE);
  }

  @Test
  void from_queryWithOnlyOtherParameters_ignoresThemIncludingUpperCaseUtmKeys() {
    Map<String, String[]> query =
        query("gclid", "abc", "fbclid", "def", "UTM_SOURCE", "shout", "utm_term", "x");

    assertThat(ClickAttribution.from(null, query)).isEqualTo(ClickAttribution.NONE);
  }

  @Test
  void from_eachUtmKeyIsReadIndependently_soAPartialTripleKeepsOnlyWhatWasSent() {
    Map<String, String[]> query = query("utm_medium", "social");

    assertThat(ClickAttribution.from(null, query))
        .isEqualTo(new ClickAttribution(null, null, "social", null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("refererEdgeCases")
  void from_refererEdgeCase_keepsOnlyTheLowerCasedHostOrNull(String referer, String expectedHost) {
    assertThat(ClickAttribution.from(referer, NO_QUERY).referrerHost()).isEqualTo(expectedHost);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("utmEdgeCases")
  void from_utmSourceEdgeCase_isCleanedTrimmedBoundedOrNull(
      Map<String, String[]> query, String expectedSource) {
    assertThat(ClickAttribution.from(null, query).utmSource()).isEqualTo(expectedSource);
  }

  static Stream<Arguments> refererEdgeCases() {
    return Stream.of(
        row(
            "mixed-case host is lower-cased",
            "https://News.Example.com/story?x=1",
            "news.example.com"),
        row("upper-case scheme and host", "HTTPS://EXAMPLE.COM", "example.com"),
        row("host without a path", "https://example.com", "example.com"),
        row("port is dropped", "https://example.com:8443/", "example.com"),
        row("userinfo is dropped", "https://alice:secret@example.com/inbox", "example.com"),
        row("trailing dot is kept as sent", "https://example.com./", "example.com."),
        row("IPv6 host keeps its brackets", "https://[2001:DB8::1]:8443/path", "[2001:db8::1]"),
        row("IPv4 host", "http://192.0.2.10/page", "192.0.2.10"),
        row("localhost", "http://localhost:3000/dev", "localhost"),
        row("relative reference", "/internal/page", null),
        row("scheme-relative reference", "//example.com/x", null),
        row("data URL is opaque", "data:text/plain,hello", null),
        row("mailto URL is opaque", "mailto:someone@example.com", null),
        row("not a URL", "not a url", null),
        row("only colons", "::::", null),
        row("empty header", "", null),
        row("absent header", null, null),
        row("underscore makes the authority registry-based", "https://my_host.example.com/", null),
        row("non-ASCII host is not parsed by java.net.URI", "https://bücher.example/", null),
        row(
            "very long host is cut to 253 characters",
            "https://" + LONG_LABEL + ".example.com/",
            LONG_LABEL.substring(0, ClickAttribution.MAX_HOST_LENGTH)));
  }

  static Stream<Arguments> utmEdgeCases() {
    return Stream.of(
        row("plain value is kept", query("utm_source", "newsletter"), "newsletter"),
        row(
            "surrounding whitespace is trimmed",
            query("utm_source", "  newsletter \t"),
            "newsletter"),
        row("CR and LF are removed", query("utm_source", "news\r\nletter"), "newsletter"),
        row(
            "NUL, ESC, DEL and C1 controls are removed",
            query("utm_source", "a\u0000b\u001bc\u007fd\u0085e"), // NEL (U+0085) is a C1 control
            "abcde"),
        row(
            "controls do not count towards the limit",
            query("utm_source", "\n".repeat(10) + "y".repeat(ClickAttribution.MAX_UTM_LENGTH)),
            "y".repeat(ClickAttribution.MAX_UTM_LENGTH)),
        row(
            "over-long value is cut to 64 characters",
            query("utm_source", "x".repeat(100)),
            "x".repeat(ClickAttribution.MAX_UTM_LENGTH)),
        row(
            "value of exactly 64 characters is kept whole",
            query("utm_source", "z".repeat(ClickAttribution.MAX_UTM_LENGTH)),
            "z".repeat(ClickAttribution.MAX_UTM_LENGTH)),
        row(
            "a surrogate pair is not split at the cut",
            query(
                "utm_source",
                "w".repeat(63) + Character.toString(0x1F600)), // 63 chars + one emoji = 65
            "w".repeat(63)),
        row("non-ASCII text is kept", query("utm_source", "café-été"), "café-été"),
        row(
            "duplicate key takes the first value",
            values("utm_source", "first", "second"),
            "first"),
        row("empty value becomes null", query("utm_source", ""), null),
        row("whitespace-only value becomes null", query("utm_source", "   "), null),
        row("control-only value becomes null", query("utm_source", "\r\n\u0000"), null),
        row("empty value array becomes null", values("utm_source"), null),
        row("null first value becomes null", values("utm_source", (String) null), null),
        row("absent key is null", query("utm_medium", "email"), null),
        row("empty query is null", NO_QUERY, null));
  }

  private static Arguments row(String name, Object input, String expected) {
    return Arguments.of(Named.of(name, input), expected);
  }

  /** Single-valued parameters, as the servlet container hands them over. */
  private static Map<String, String[]> query(String... keyValuePairs) {
    Map<String, String[]> query = new HashMap<>();
    for (int i = 0; i < keyValuePairs.length; i += 2) {
      query.put(keyValuePairs[i], new String[] {keyValuePairs[i + 1]});
    }
    return query;
  }

  /** One parameter with every value it was sent with, in order. */
  private static Map<String, String[]> values(String key, String... values) {
    Map<String, String[]> query = new HashMap<>();
    query.put(key, values);
    return query;
  }
}
