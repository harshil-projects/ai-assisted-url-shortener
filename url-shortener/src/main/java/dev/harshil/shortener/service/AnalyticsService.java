package dev.harshil.shortener.service;

import dev.harshil.shortener.api.dto.StatsResponse;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.domain.LinkNotFoundException;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.ClickSummary;
import dev.harshil.shortener.repository.LinkRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers the stats endpoint with the link lookup, one aggregate over the {@code (link_id,
 * occurred_at)} index (design 3.6) and, since R-03, the two grouped attribution breakdowns. All run
 * in a single read-only transaction so they see one consistent snapshot, and the time windows are
 * cut from the injected clock, which keeps the arithmetic in UTC and lets tests pin "now".
 */
@Service
public class AnalyticsService {

  static final Duration LAST_24H = Duration.ofHours(24);
  static final Duration LAST_7D = Duration.ofDays(7);

  /** Rows per attribution breakdown (R-03 decision: top ten). */
  static final int TOP_N = 10;

  private final LinkRepository links;
  private final ClickEventRepository clickEvents;
  private final Clock clock;

  /**
   * Creates the service.
   *
   * @param links link persistence
   * @param clickEvents click persistence with the aggregate query
   * @param clock application clock the windows are relative to
   */
  public AnalyticsService(LinkRepository links, ClickEventRepository clickEvents, Clock clock) {
    this.links = links;
    this.clickEvents = clickEvents;
    this.clock = clock;
  }

  /**
   * Computes the statistics for a link. An expired link is reported like any other, with {@code
   * expired} set from the same clock instant that cuts the windows: its events are kept, so its
   * counts do not change when it expires (design 3.6).
   *
   * @param code the short code
   * @return totals, windowed counts, the last click, the expiry state and the top-ten attribution
   *     breakdowns
   * @throws LinkNotFoundException when no link has that code
   */
  @Transactional(readOnly = true)
  public StatsResponse stats(String code) {
    Link link = links.findByCode(code).orElseThrow(() -> new LinkNotFoundException(code));
    Instant now = clock.instant();
    ClickSummary summary =
        clickEvents.summarize(link.getId(), now.minus(LAST_24H), now.minus(LAST_7D));
    return new StatsResponse(
        link.getCode(),
        summary.total(),
        summary.last24h(),
        summary.last7d(),
        summary.lastClickedAt(),
        link.getExpiresAt(),
        link.isExpired(now),
        clickEvents.topReferrers(link.getId(), TOP_N),
        clickEvents.topCampaigns(link.getId(), TOP_N));
  }
}
