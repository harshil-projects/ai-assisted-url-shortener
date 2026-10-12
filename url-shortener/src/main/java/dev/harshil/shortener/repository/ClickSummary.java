package dev.harshil.shortener.repository;

import java.time.Instant;

/**
 * Aggregate of one link's clicks produced by a single query over the {@code (link_id, occurred_at)}
 * index (design 3.6).
 *
 * @param total every recorded click
 * @param last24h clicks at or after the 24-hour boundary supplied by the caller
 * @param last7d clicks at or after the 7-day boundary supplied by the caller
 * @param lastClickedAt the most recent click, or {@code null} when the link was never followed
 */
public record ClickSummary(long total, long last24h, long last7d, Instant lastClickedAt) {}
