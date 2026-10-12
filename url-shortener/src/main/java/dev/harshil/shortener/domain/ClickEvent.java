package dev.harshil.shortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One recorded redirect of a link.
 *
 * <p>{@code linkId} is a plain column rather than a relationship so recording a click is a single
 * {@code INSERT} with no preceding {@code SELECT} (design 3.5). The row carries nothing about the
 * visitor: no address, no user agent (ADR 0006). Equality is left as identity because two clicks on
 * the same link at the same instant are two distinct events.
 */
@Entity
@Table(name = "click_events")
public class ClickEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "link_id", nullable = false)
  private Long linkId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  /** JPA only; Hibernate populates the fields reflectively. */
  protected ClickEvent() {}

  /**
   * Creates an event ready to be persisted.
   *
   * @param linkId identifier of the link that was followed
   * @param occurredAt the instant the redirect was served, from the application clock
   */
  public ClickEvent(Long linkId, Instant occurredAt) {
    this.linkId = linkId;
    this.occurredAt = occurredAt;
  }

  public Long getId() {
    return id;
  }

  public Long getLinkId() {
    return linkId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  @Override
  public String toString() {
    return "ClickEvent{id=" + id + ", linkId=" + linkId + ", occurredAt=" + occurredAt + "}";
  }
}
