package dev.harshil.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.harshil.shortener.api.dto.CampaignStats;
import dev.harshil.shortener.api.dto.ReferrerStats;
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
import java.util.List;
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

    assertThat(stats).isEqualTo(new StatsResponse("stats01", 10L, 2L, 7L, lastClick, null, false));
  }

  @Test
  void stats_linkNeverClicked_returnsZerosAndNullLastClickedAt() {
    when(links.findByCode("quiet01")).thenReturn(Optional.of(linkWithId("quiet01")));
    when(clickEvents.summarize(LINK_ID, SINCE_24H, SINCE_7D))
        .thenReturn(new ClickSummary(0, 0, 0, null));

    StatsResponse stats = service.stats("quiet01");

    assertThat(stats).isEqualTo(new StatsResponse("quiet01", 0L, 0L, 0L, null, null, false));
  }

  @Test
  void stats_expiredLink_reportsExpiredTrueWithTheExpiryAndKeepsCountingItsClicks() {
    Instant expiredAt = NOW.minus(Duration.ofMinutes(1));
    Link expired = linkWithId("expired");
    ReflectionTestUtils.setField(expired, "expiresAt", expiredAt);
    when(links.findByCode("expired")).thenReturn(Optional.of(expired));
    when(clickEvents.summarize(LINK_ID, SINCE_24H, SINCE_7D))
        .thenReturn(new ClickSummary(5, 1, 3, NOW.minus(Duration.ofHours(2))));

    StatsResponse stats = service.stats("expired");

    assertThat(stats)
        .isEqualTo(
            new StatsResponse(
                "expired", 5L, 1L, 3L, NOW.minus(Duration.ofHours(2)), expiredAt, true));
  }

  @Test
  void stats_linkExpiringLater_reportsExpiredFalseWithTheExpiry() {
    Instant expiresAt = NOW.plus(Duration.ofMinutes(1));
    Link live = linkWithId("live001");
    ReflectionTestUtils.setField(live, "expiresAt", expiresAt);
    when(links.findByCode("live001")).thenReturn(Optional.of(live));
    when(clickEvents.summarize(LINK_ID, SINCE_24H, SINCE_7D))
        .thenReturn(new ClickSummary(0, 0, 0, null));

    StatsResponse stats = service.stats("live001");

    assertThat(stats.expired()).isFalse();
    assertThat(stats.expiresAt()).isEqualTo(expiresAt);
  }

  @Test
  void stats_knownCode_fillsTheTopTenReferrersAndCampaignsFromTheRepository() {
    List<ReferrerStats> referrers = List.of(new ReferrerStats("news.example.com", 2));
    List<CampaignStats> campaigns = List.of(new CampaignStats("newsletter", "email", "oct", 1));
    when(links.findByCode("attr01")).thenReturn(Optional.of(linkWithId("attr01")));
    when(clickEvents.summarize(LINK_ID, SINCE_24H, SINCE_7D))
        .thenReturn(new ClickSummary(3, 3, 3, NOW));
    when(clickEvents.topReferrers(LINK_ID, AnalyticsService.TOP_N)).thenReturn(referrers);
    when(clickEvents.topCampaigns(LINK_ID, AnalyticsService.TOP_N)).thenReturn(campaigns);

    StatsResponse stats = service.stats("attr01");

    assertThat(stats.referrers()).containsExactlyElementsOf(referrers);
    assertThat(stats.campaigns()).containsExactlyElementsOf(campaigns);
    assertThat(AnalyticsService.TOP_N).isEqualTo(10);
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
    Link link = new Link(code, "https://example.com/" + code, NOW.minus(Duration.ofDays(30)), null);
    ReflectionTestUtils.setField(link, "id", LINK_ID);
    return link;
  }
}
