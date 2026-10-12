package dev.harshil.shortener.api.dto;

import java.time.Instant;

/**
 * Click statistics for one link (design 3.3), computed by a single aggregate query.
 *
 * @param code the short code
 * @param totalClicks every recorded click
 * @param clicksLast24h clicks in the 24 hours before the request, inclusive of the boundary
 * @param clicksLast7d clicks in the 7 days before the request, inclusive of the boundary
 * @param lastClickedAt the most recent click, or {@code null} when the link was never followed
 */
public record StatsResponse(
    String code, Long totalClicks, Long clicksLast24h, Long clicksLast7d, Instant lastClickedAt) {}
