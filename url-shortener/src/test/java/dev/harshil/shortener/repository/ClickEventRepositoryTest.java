package dev.harshil.shortener.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.api.dto.CampaignStats;
import dev.harshil.shortener.api.dto.ReferrerStats;
import dev.harshil.shortener.domain.ClickAttribution;
import dev.harshil.shortener.domain.ClickEvent;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.support.TestProfile;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestProfile
class ClickEventRepositoryTest {

  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00Z");
  private static final Instant SINCE_24H = NOW.minus(Duration.ofHours(24));
  private static final Instant SINCE_7D = NOW.minus(Duration.ofDays(7));

  @Autowired private ClickEventRepository clickEvents;
  @Autowired private LinkRepository links;
  @Autowired private DataSource dataSource;

  @AfterEach
  void cleanTables() {
    clickEvents.deleteAllInBatch();
    links.deleteAllInBatch();
  }

  @Test
  void summarize_eventsInsideAndOutsideEachWindow_countsTotalWindowsAndLastClick() {
    Link link = saveLink("summary1");
    clickEvents.save(new ClickEvent(link.getId(), NOW.minus(Duration.ofHours(1))));
    clickEvents.save(new ClickEvent(link.getId(), NOW.minus(Duration.ofDays(2))));
    clickEvents.save(new ClickEvent(link.getId(), NOW.minus(Duration.ofDays(10))));

    ClickSummary summary = clickEvents.summarize(link.getId(), SINCE_24H, SINCE_7D);

    assertThat(summary).isEqualTo(new ClickSummary(3, 1, 2, NOW.minus(Duration.ofHours(1))));
  }

  @Test
  void summarize_eventExactlyOnWindowBoundary_isCountedInsideTheWindow() {
    Link link = saveLink("boundary");
    clickEvents.save(new ClickEvent(link.getId(), SINCE_24H));
    clickEvents.save(new ClickEvent(link.getId(), SINCE_7D));

    ClickSummary summary = clickEvents.summarize(link.getId(), SINCE_24H, SINCE_7D);

    assertThat(summary.total()).isEqualTo(2);
    assertThat(summary.last24h()).isEqualTo(1);
    assertThat(summary.last7d()).isEqualTo(2);
  }

  @Test
  void summarize_linkWithoutEvents_returnsZerosAndNullLastClick() {
    Link link = saveLink("noclick");

    ClickSummary summary = clickEvents.summarize(link.getId(), SINCE_24H, SINCE_7D);

    assertThat(summary).isEqualTo(new ClickSummary(0, 0, 0, null));
  }

  @Test
  void summarize_eventsOfAnotherLink_areNotCounted() {
    Link counted = saveLink("counted");
    Link other = saveLink("otherln");
    clickEvents.save(new ClickEvent(counted.getId(), NOW.minus(Duration.ofMinutes(5))));
    clickEvents.save(new ClickEvent(other.getId(), NOW.minus(Duration.ofMinutes(1))));

    ClickSummary summary = clickEvents.summarize(counted.getId(), SINCE_24H, SINCE_7D);

    assertThat(summary).isEqualTo(new ClickSummary(1, 1, 1, NOW.minus(Duration.ofMinutes(5))));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void deleteLink_throughJdbc_cascadesToItsClickEvents() {
    Link link = saveLink("cascade");
    clickEvents.save(new ClickEvent(link.getId(), NOW.minus(Duration.ofMinutes(1))));
    clickEvents.save(new ClickEvent(link.getId(), NOW.minus(Duration.ofMinutes(2))));
    assertThat(clickEvents.count()).isEqualTo(2);

    int removed =
        new JdbcTemplate(dataSource).update("delete from links where id = ?", link.getId());

    assertThat(removed).isEqualTo(1);
    assertThat(clickEvents.count()).isZero();
    assertThat(links.existsByCode("cascade")).isFalse();
  }

  @Test
  void topReferrersAndCampaigns_linkWithoutClicks_returnEmptyLists() {
    Link link = saveLink("quiet01");

    assertThat(clickEvents.topReferrers(link.getId(), 10)).isEmpty();
    assertThat(clickEvents.topCampaigns(link.getId(), 10)).isEmpty();
  }

  @Test
  void topReferrers_hostsWithDifferentAndTiedCounts_ordersByClicksDescendingThenHost() {
    Link link = saveLink("refs001");
    saveClick(link, "b.example", null, null, null);
    saveClick(link, "c.example", null, null, null);
    saveClick(link, "a.example", null, null, null);
    saveClick(link, "c.example", null, null, null);
    saveClick(link, "b.example", null, null, null);
    saveClick(link, "a.example", null, null, null);
    saveClick(link, "c.example", null, null, null);

    List<ReferrerStats> top = clickEvents.topReferrers(link.getId(), 10);

    assertThat(top)
        .containsExactly(
            new ReferrerStats("c.example", 3),
            new ReferrerStats("a.example", 2),
            new ReferrerStats("b.example", 2));
  }

  @Test
  void topReferrers_moreHostsThanTheLimit_returnsOnlyTheLimitInOrder() {
    Link link = saveLink("refs002");
    for (int i = 11; i >= 0; i--) {
      saveClick(link, "h" + String.format("%02d", i) + ".example", null, null, null);
    }

    List<ReferrerStats> top = clickEvents.topReferrers(link.getId(), 10);

    assertThat(top)
        .hasSize(10)
        .extracting(ReferrerStats::host)
        .containsExactly(
            "h00.example",
            "h01.example",
            "h02.example",
            "h03.example",
            "h04.example",
            "h05.example",
            "h06.example",
            "h07.example",
            "h08.example",
            "h09.example");
  }

  @Test
  void topReferrers_clicksWithoutAHost_areExcludedFromTheBreakdown() {
    Link link = saveLink("refs003");
    saveClick(link, null, "newsletter", null, null);
    saveClick(link, null, null, null, null);
    saveClick(link, "a.example", null, null, null);

    assertThat(clickEvents.topReferrers(link.getId(), 10))
        .containsExactly(new ReferrerStats("a.example", 1));
  }

  @Test
  void topReferrers_eventsOfAnotherLink_areNotCounted() {
    Link counted = saveLink("refs004");
    Link other = saveLink("refs005");
    saveClick(counted, "a.example", null, null, null);
    saveClick(other, "a.example", null, null, null);
    saveClick(other, "b.example", null, null, null);

    assertThat(clickEvents.topReferrers(counted.getId(), 10))
        .containsExactly(new ReferrerStats("a.example", 1));
  }

  @Test
  void topCampaigns_triplesWithDifferentCounts_ordersByClicksDescendingThenNames() {
    Link link = saveLink("camp001");
    saveClick(link, null, "twitter", "social", "oct");
    saveClick(link, null, "newsletter", "email", "oct");
    saveClick(link, null, "newsletter", "email", "sep");
    saveClick(link, null, "newsletter", "email", "oct");

    List<CampaignStats> top = clickEvents.topCampaigns(link.getId(), 10);

    assertThat(top)
        .containsExactly(
            new CampaignStats("newsletter", "email", "oct", 2),
            new CampaignStats("newsletter", "email", "sep", 1),
            new CampaignStats("twitter", "social", "oct", 1));
  }

  @Test
  void topCampaigns_clicksWithAllThreeFieldsNull_areExcludedButPartialTriplesKept() {
    Link link = saveLink("camp002");
    saveClick(link, "a.example", null, null, null);
    saveClick(link, null, null, null, null);
    saveClick(link, null, "newsletter", null, null);

    assertThat(clickEvents.topCampaigns(link.getId(), 10))
        .containsExactly(new CampaignStats("newsletter", null, null, 1));
  }

  @Test
  void topCampaigns_moreTriplesThanTheLimit_returnsOnlyTheLimit() {
    Link link = saveLink("camp003");
    for (int i = 0; i < 11; i++) {
      saveClick(link, null, "source" + String.format("%02d", i), "email", "oct");
    }

    assertThat(clickEvents.topCampaigns(link.getId(), 10))
        .hasSize(10)
        .extracting(CampaignStats::source)
        .doesNotContain("source10");
  }

  private Link saveLink(String code) {
    return links.save(
        new Link(code, "https://example.com/" + code, NOW.minus(Duration.ofDays(30)), null));
  }

  private void saveClick(Link link, String host, String source, String medium, String campaign) {
    clickEvents.save(
        new ClickEvent(link.getId(), NOW, new ClickAttribution(host, source, medium, campaign)));
  }
}
