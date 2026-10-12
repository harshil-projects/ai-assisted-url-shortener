package dev.harshil.shortener.api.dto;

/**
 * One row of the campaign breakdown in {@link StatsResponse}: a distinct {@code utm_source}, {@code
 * utm_medium}, {@code utm_campaign} triple as marketing put it on the short link, and how many
 * clicks carried it, counted over all time (R-03 decision). Any of the three may be {@code null}
 * when the link was shared with only some of the parameters; clicks that carried none have no row.
 *
 * @param source {@code utm_source}, or {@code null}
 * @param medium {@code utm_medium}, or {@code null}
 * @param campaign {@code utm_campaign}, or {@code null}
 * @param clicks number of clicks that carried exactly this triple
 */
public record CampaignStats(String source, String medium, String campaign, long clicks) {}
