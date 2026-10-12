package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.harshil.shortener.config.SecurityConfig;
import dev.harshil.shortener.domain.ClickRecorded;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.service.LinkService;
import dev.harshil.shortener.support.TestProfile;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * Web slice for the redirect: status, headers, the published click event and the outcome counters.
 * A synchronous listener stands in for the asynchronous recorder so the event can be inspected.
 */
@WebMvcTest(RedirectController.class)
@Import({SecurityConfig.class, RedirectControllerTest.SliceSupport.class})
@TestProfile
class RedirectControllerTest {

  private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
  private static final String TARGET = "https://example.com/landing?x=1";

  @Autowired private MockMvcTester mvc;
  @Autowired private ClickSink clicks;
  @Autowired private MeterRegistry registry;
  @MockitoBean private LinkService linkService;

  @BeforeEach
  void stubKnownLink() {
    Link link = new Link("abcdefg", TARGET, NOW.minusSeconds(3600));
    ReflectionTestUtils.setField(link, "id", 42L);
    when(linkService.resolve("abcdefg")).thenReturn(link);
    when(linkService.resolve("missing")).thenThrow(new LinkNotFoundException("missing"));
    clicks.events().clear();
  }

  @Test
  void get_knownCode_returns302WithStoredLocationAndExplicitNoStore() {
    assertThat(mvc.get().uri("/abcdefg"))
        .hasStatus(HttpStatus.FOUND)
        .hasHeader(HttpHeaders.LOCATION, TARGET)
        .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");
  }

  @Test
  void get_knownCode_publishesOneClickWithLinkIdAndClockInstant() {
    double hitsBefore = hits();

    assertThat(mvc.get().uri("/abcdefg")).hasStatus(HttpStatus.FOUND);

    assertThat(clicks.events()).containsExactly(new ClickRecorded(42L, NOW, null));
    assertThat(hits()).isEqualTo(hitsBefore + 1);
  }

  @Test
  void head_knownCode_returns302WithSameHeadersAndPublishesNothing() {
    assertThat(mvc.head().uri("/abcdefg"))
        .hasStatus(HttpStatus.FOUND)
        .hasHeader(HttpHeaders.LOCATION, TARGET)
        .hasHeader(HttpHeaders.CACHE_CONTROL, "no-store");

    assertThat(clicks.events()).isEmpty();
  }

  @Test
  void get_unknownCode_returns404LinkNotFoundProblemAndCountsAMiss() {
    double missesBefore = misses();

    assertThat(mvc.get().uri("/missing"))
        .hasStatus(HttpStatus.NOT_FOUND)
        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .bodyJson()
        .hasPathSatisfying(
            "$.type", type -> type.assertThat().isEqualTo("/problems/link-not-found"));

    assertThat(clicks.events()).isEmpty();
    assertThat(misses()).isEqualTo(missesBefore + 1);
  }

  @Test
  void get_pathFailingTheCodePattern_returns404WithoutResolvingAnything() {
    assertThat(mvc.get().uri("/favicon.ico")).hasStatus(HttpStatus.NOT_FOUND);
    assertThat(mvc.get().uri("/abc")).hasStatus(HttpStatus.NOT_FOUND);

    verifyNoInteractions(linkService);
    assertThat(clicks.events()).isEmpty();
  }

  @Test
  void get_redirect_isPublicAndNeedsNoApiKey() {
    assertThat(mvc.get().uri("/abcdefg")).hasStatus(HttpStatus.FOUND);
  }

  private double hits() {
    return registry.counter("shortener.redirects", "outcome", "hit").count();
  }

  private double misses() {
    return registry.counter("shortener.redirects", "outcome", "miss").count();
  }

  /** Captures published click events synchronously so the test can assert on them. */
  static final class ClickSink {

    private final List<ClickRecorded> events = new CopyOnWriteArrayList<>();

    @EventListener
    void on(ClickRecorded event) {
      events.add(event);
    }

    List<ClickRecorded> events() {
      return events;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class SliceSupport {

    @Bean
    Clock clock() {
      return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }

    @Bean
    ClickSink clickSink() {
      return new ClickSink();
    }
  }
}
