package dev.harshil.shortener.api.dto;

/**
 * One row of the referrer breakdown in {@link StatsResponse}: a referring host and how many clicks
 * on the link arrived from it, counted over all time (R-03 decision). Only the host is ever
 * recorded, never the full referrer URL; clicks without a usable referer have no row.
 *
 * @param host lower-cased host of the referring page
 * @param clicks number of clicks that carried it
 */
public record ReferrerStats(String host, long clicks) {}
