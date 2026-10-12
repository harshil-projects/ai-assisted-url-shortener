package dev.harshil.shortener.domain;

import java.time.Instant;

/**
 * Immutable fact that a redirect was served, built on the request thread and handed to the click
 * recorder's executor (design 3.6, ADR 0005). It carries everything the recorder needs so nothing
 * from the servlet request is ever read off the request thread, and the request id so the
 * asynchronous log line can be correlated with the redirect that caused it.
 *
 * @param linkId identifier of the link that was followed
 * @param occurredAt the instant the redirect was served, from the application clock
 * @param requestId the correlation id of the redirect request, or {@code null} when none was set
 */
public record ClickRecorded(Long linkId, Instant occurredAt, String requestId) {}
