package dev.harshil.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.harshil.shortener.api.dto.StatsResponse;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.ClickSummary;
import dev.harshil.shortener.repository.LinkRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
  private static final Instant SINCE_24H = NOW.minus(Duration.ofHours(24));
  private static final Instant SINCE_7D = NOW.minus(Duration.ofDays(7));
  private static final long LINK_ID = 42L;

  @Mock private LinkRepository links;
  @Mock private ClickEventRepository clickEvents;

  private AnalyticsService service;

  @BeforeEach
  void createService() {
    service = new AnalyticsService(links, clickEvents, Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void stats_knownCode_queriesOneAggregateWithWindowsCutFromTheClockAndMapsEveryField() {
    Instant lastClick = NOW.minus(Duration.ofMinutes(3));
    when(links.findByCode("stats01")).thenReturn(Optional.of(linkWithId("stats01")));
    when(clickEvents.summarize(LINK_ID, SINCE_24H, SINCE_7D))
        .thenReturn(new ClickSummary(10, 2, 7, lastClick));

    StatsResponse stats = service.stats("stats01");

    assertThat(stats).isEqualTo(new StatsResponse("stats01", 10L, 2L, 7L, lastClick));
  }

  @Test
  void stats_linkNeverClicked_returnsZerosAndNullLastClickedAt() {
    when(links.findByCode("quiet01")).thenReturn(Optional.of(linkWithId("quiet01")));
    when(clickEvents.summarize(LINK_ID, SINCE_24H, SINCE_7D))
        .thenReturn(new ClickSummary(0, 0, 0, null));

    StatsResponse stats = service.stats("quiet01");

    assertThat(stats).isEqualTo(new StatsResponse("quiet01", 0L, 0L, 0L, null));
  }

  @Test
  void stats_unknownCode_throwsLinkNotFoundWithoutQueryingClicks() {
    when(links.findByCode("nothere")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.stats("nothere"))
        .isInstanceOfSatisfying(
            LinkNotFoundException.class, ex -> assertThat(ex.getCode()).isEqualTo("nothere"));

    verifyNoInteractions(clickEvents);
  }

  private static Link linkWithId(String code) {
    Link link = new Link(code, "https://example.com/" + code, NOW.minus(Duration.ofDays(30)));
    ReflectionTestUtils.setField(link, "id", LINK_ID);
    return link;
  }
}
