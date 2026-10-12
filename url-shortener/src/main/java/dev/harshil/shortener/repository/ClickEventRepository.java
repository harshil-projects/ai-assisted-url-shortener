package dev.harshil.shortener.repository;

import dev.harshil.shortener.domain.ClickEvent;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Append-only persistence for {@link ClickEvent} plus the one aggregate the stats endpoint needs.
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
}
