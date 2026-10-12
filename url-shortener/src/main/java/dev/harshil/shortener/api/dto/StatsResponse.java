package dev.harshil.shortener.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Click statistics for one link (design 3.3): the totals from a single aggregate query, the expiry
 * state, and the two attribution breakdowns decided in R-03, each at most ten rows ordered by
 * clicks descending then name, counted over all clicks of the link. Expired links keep their rows
 * and events, so the counts are reported unchanged alongside the expiry state.
 *
 * @param code the short code
 * @param totalClicks every recorded click
 * @param clicksLast24h clicks in the 24 hours before the request, inclusive of the boundary
 * @param clicksLast7d clicks in the 7 days before the request, inclusive of the boundary
 * @param lastClickedAt the most recent click, or {@code null} when the link was never followed
 * @param expiresAt when the link stops redirecting, UTC, or {@code null} when it never expires
 * @param expired whether the link had expired at the moment this response was built
 * @param referrers top referring hosts; empty, never {@code null}, when none was recorded
 * @param campaigns top campaign triples; empty, never {@code null}, when none was recorded
 */
public record StatsResponse(
    String code,
    Long totalClicks,
    Long clicksLast24h,
    Long clicksLast7d,
    Instant lastClickedAt,
    Instant expiresAt,
    Boolean expired,
    List<ReferrerStats> referrers,
    List<CampaignStats> campaigns) {

  /**
   * Renders absent breakdowns as empty arrays, absent expiry as not expired, and copies the lists.
   */
  public StatsResponse {
    expired = expired != null && expired;
    referrers = referrers == null ? List.of() : List.copyOf(referrers);
    campaigns = campaigns == null ? List.of() : List.copyOf(campaigns);
  }

  /**
   * Creates statistics for a link that never expires and has no attribution breakdowns.
   *
   * @param code the short code
   * @param totalClicks every recorded click
   * @param clicksLast24h clicks in the last 24 hours
   * @param clicksLast7d clicks in the last 7 days
   * @param lastClickedAt the most recent click, or {@code null}
   */
  public StatsResponse(
      String code, Long totalClicks, Long clicksLast24h, Long clicksLast7d, Instant lastClickedAt) {
    this(code, totalClicks, clicksLast24h, clicksLast7d, lastClickedAt, null, false, null, null);
  }

  /**
   * Creates statistics with the expiry state and no attribution breakdowns.
   *
   * @param code the short code
   * @param totalClicks every recorded click
   * @param clicksLast24h clicks in the last 24 hours
   * @param clicksLast7d clicks in the last 7 days
   * @param lastClickedAt the most recent click, or {@code null}
   * @param expiresAt when the link stops redirecting, or {@code null}
   * @param expired whether the link had expired when the response was built
   */
  public StatsResponse(
      String code,
      Long totalClicks,
      Long clicksLast24h,
      Long clicksLast7d,
      Instant lastClickedAt,
      Instant expiresAt,
      Boolean expired) {
    this(
        code,
        totalClicks,
        clicksLast24h,
        clicksLast7d,
        lastClickedAt,
        expiresAt,
        expired,
        null,
        null);
  }

  /**
   * Creates statistics with attribution breakdowns for a link that never expires.
   *
   * @param code the short code
   * @param totalClicks every recorded click
   * @param clicksLast24h clicks in the last 24 hours
   * @param clicksLast7d clicks in the last 7 days
   * @param lastClickedAt the most recent click, or {@code null}
   * @param referrers top referring hosts
   * @param campaigns top campaign triples
   */
  public StatsResponse(
      String code,
      Long totalClicks,
      Long clicksLast24h,
      Long clicksLast7d,
      Instant lastClickedAt,
      List<ReferrerStats> referrers,
      List<CampaignStats> campaigns) {
    this(
        code,
        totalClicks,
        clicksLast24h,
        clicksLast7d,
        lastClickedAt,
        null,
        false,
        referrers,
        campaigns);
  }
}
