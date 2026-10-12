package dev.harshil.shortener.domain;

import java.time.Instant;

/**
 * Immutable fact that a redirect was served, built on the request thread and handed to the click
 * recorder's executor (design 3.6, ADR 0005). It carries everything the recorder needs so nothing
 * from the servlet request is ever read off the request thread, and the request id so the
 * asynchronous log line can be correlated with the redirect that caused it. Since R-03 it also
 * carries the click's {@link ClickAttribution}, computed by the controller before publishing.
 *
 * @param linkId identifier of the link that was followed
 * @param occurredAt the instant the redirect was served, from the application clock
 * @param requestId the correlation id of the redirect request, or {@code null} when none was set
 * @param attribution where the click came from; {@link ClickAttribution#NONE} when nothing usable
 *     was on the request, never {@code null}
 */
public record ClickRecorded(
    Long linkId, Instant occurredAt, String requestId, ClickAttribution attribution) {

  /** Normalises a missing attribution to {@link ClickAttribution#NONE}. */
  public ClickRecorded {
    attribution = attribution == null ? ClickAttribution.NONE : attribution;
  }

  /**
   * Creates an event without attribution.
   *
   * @param linkId identifier of the link that was followed
   * @param occurredAt the instant the redirect was served
   * @param requestId the correlation id, or {@code null}
   */
  public ClickRecorded(Long linkId, Instant occurredAt, String requestId) {
    this(linkId, occurredAt, requestId, ClickAttribution.NONE);
  }
}
