package dev.harshil.shortener.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.domain.ClickEvent;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.support.TestProfile;
import java.time.Duration;
import java.time.Instant;
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

  private Link saveLink(String code) {
    return links.save(
        new Link(code, "https://example.com/" + code, NOW.minus(Duration.ofDays(30))));
  }
}
