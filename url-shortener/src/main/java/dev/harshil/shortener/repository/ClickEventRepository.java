package dev.harshil.shortener.repository;

import dev.harshil.shortener.api.dto.CampaignStats;
import dev.harshil.shortener.api.dto.ReferrerStats;
import dev.harshil.shortener.domain.ClickEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Append-only persistence for {@link ClickEvent} plus the aggregates the stats endpoint needs: the
 * one-statement summary and, since R-03, the two attribution breakdowns.
 */
public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

  /**
   * Computes every stats figure for a link in one statement. The windowed counts use {@code sum}
   * over a {@code case} rather than separate {@code count} queries so the index is scanned once,
   * and {@code coalesce} turns the {@code null} that {@code sum} yields for an empty link into 0.
   * {@code max} stays {@code null} on purpose: "never clicked" is a real answer.
   *
   * @param linkId the link to summarise
   * @param since24h inclusive lower bound of the 24-hour window, computed by the caller's clock
   * @param since7d inclusive lower bound of the 7-day window, computed by the caller's clock
   * @return the totals; never {@code null}
   */
  @Query(
      """
      select new dev.harshil.shortener.repository.ClickSummary(
          count(e),
          coalesce(sum(case when e.occurredAt >= :since24h then 1L else 0L end), 0L),
          coalesce(sum(case when e.occurredAt >= :since7d then 1L else 0L end), 0L),
          max(e.occurredAt))
      from ClickEvent e
      where e.linkId = :linkId
      """)
  ClickSummary summarize(
      @Param("linkId") Long linkId,
      @Param("since24h") Instant since24h,
      @Param("since7d") Instant since7d);

  /**
   * Top referring hosts of a link over all time, most clicks first and the host name as the
   * tie-break; clicks that recorded no host are excluded (R-03, FR-15d).
   *
   * @param linkId the link to break down
   * @param limit the most rows to return
   * @return at most {@code limit} rows; empty when nothing was recorded
   */
  default List<ReferrerStats> topReferrers(long linkId, int limit) {
    return topReferrers(linkId, PageRequest.of(0, limit));
  }

  /**
   * Grouped query behind {@link #topReferrers(long, int)}; the page bounds the row count.
   *
   * @param linkId the link to break down
   * @param page the first page, whose size is the row limit
   * @return the rows of that page
   */
  @Query(
      """
      select new dev.harshil.shortener.api.dto.ReferrerStats(e.referrerHost, count(e))
      from ClickEvent e
      where e.linkId = :linkId and e.referrerHost is not null
      group by e.referrerHost
      order by count(e) desc, e.referrerHost asc
      """)
  List<ReferrerStats> topReferrers(@Param("linkId") long linkId, Pageable page);

  /**
   * Top campaign triples of a link over all time, most clicks first and the source, medium and
   * campaign names as tie-breaks; clicks that recorded none of the three are excluded (R-03,
   * FR-15d).
   *
   * @param linkId the link to break down
   * @param limit the most rows to return
   * @return at most {@code limit} rows; empty when nothing was recorded
   */
  default List<CampaignStats> topCampaigns(long linkId, int limit) {
    return topCampaigns(linkId, PageRequest.of(0, limit));
  }

  /**
   * Grouped query behind {@link #topCampaigns(long, int)}; the page bounds the row count.
   *
   * @param linkId the link to break down
   * @param page the first page, whose size is the row limit
   * @return the rows of that page
   */
  @Query(
      """
      select new dev.harshil.shortener.api.dto.CampaignStats(
          e.utmSource, e.utmMedium, e.utmCampaign, count(e))
      from ClickEvent e
      where e.linkId = :linkId
        and (e.utmSource is not null or e.utmMedium is not null or e.utmCampaign is not null)
      group by e.utmSource, e.utmMedium, e.utmCampaign
      order by count(e) desc, e.utmSource asc, e.utmMedium asc, e.utmCampaign asc
      """)
  List<CampaignStats> topCampaigns(@Param("linkId") long linkId, Pageable page);
}
