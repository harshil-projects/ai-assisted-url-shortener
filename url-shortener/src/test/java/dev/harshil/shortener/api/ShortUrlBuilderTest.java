package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.config.AppProperties;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class ShortUrlBuilderTest {

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void shortUrl_withBaseUrlConfigured_usesItAndIgnoresTheRequest() {
    bindRequest("http", "ignored.example", 8080);

    String url = new ShortUrlBuilder(properties("https://sho.rt")).shortUrl("abc1234");

    assertThat(url).isEqualTo("https://sho.rt/abc1234");
  }

  @Test
  void shortUrl_baseUrlWithTrailingSlashOrWhitespace_doesNotDoubleTheSlash() {
    String url = new ShortUrlBuilder(properties(" https://sho.rt/ ")).shortUrl("abc1234");

    assertThat(url).isEqualTo("https://sho.rt/abc1234");
  }

  @Test
  void shortUrl_withoutBaseUrl_usesCurrentRequestSchemeHostAndPort() {
    bindRequest("https", "reviewer.example", 9090);

    String url = new ShortUrlBuilder(properties("")).shortUrl("abc1234");

    assertThat(url).isEqualTo("https://reviewer.example:9090/abc1234");
  }

  @Test
  void shortUrl_withoutBaseUrlOnDefaultPort_omitsThePort() {
    bindRequest("http", "localhost", 80);

    String url = new ShortUrlBuilder(properties(null)).shortUrl("abc1234");

    assertThat(url).isEqualTo("http://localhost/abc1234");
  }

  private static void bindRequest(String scheme, String host, int port) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setScheme(scheme);
    request.setServerName(host);
    request.setServerPort(port);
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
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
