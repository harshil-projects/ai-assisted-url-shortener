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
 * visitor: no address, no user agent (ADR 0006). Since V3 (R-03) it carries where the click came
 * from in the bounded, nullable form of {@link ClickAttribution}: the referring host and the three
 * campaign labels marketing put on the link, never the full referrer URL. Equality is left as
 * identity because two clicks on the same link at the same instant are two distinct events.
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

  @Column(name = "referrer_host", length = ClickAttribution.MAX_HOST_LENGTH)
  private String referrerHost;

  @Column(name = "utm_source", length = ClickAttribution.MAX_UTM_LENGTH)
  private String utmSource;

  @Column(name = "utm_medium", length = ClickAttribution.MAX_UTM_LENGTH)
  private String utmMedium;

  @Column(name = "utm_campaign", length = ClickAttribution.MAX_UTM_LENGTH)
  private String utmCampaign;

  /** JPA only; Hibernate populates the fields reflectively. */
  protected ClickEvent() {}

  /**
   * Creates an event without attribution, ready to be persisted.
   *
   * @param linkId identifier of the link that was followed
   * @param occurredAt the instant the redirect was served, from the application clock
   */
  public ClickEvent(Long linkId, Instant occurredAt) {
    this(linkId, occurredAt, ClickAttribution.NONE);
  }

  /**
   * Creates an event ready to be persisted.
   *
   * @param linkId identifier of the link that was followed
   * @param occurredAt the instant the redirect was served, from the application clock
   * @param attribution where the click came from, already cleaned and bounded
   */
  public ClickEvent(Long linkId, Instant occurredAt, ClickAttribution attribution) {
    this.linkId = linkId;
    this.occurredAt = occurredAt;
    this.referrerHost = attribution.referrerHost();
    this.utmSource = attribution.utmSource();
    this.utmMedium = attribution.utmMedium();
    this.utmCampaign = attribution.utmCampaign();
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

  public String getReferrerHost() {
    return referrerHost;
  }

  public String getUtmSource() {
    return utmSource;
  }

  public String getUtmMedium() {
    return utmMedium;
  }

  public String getUtmCampaign() {
    return utmCampaign;
  }

  @Override
  public String toString() {
    return "ClickEvent{id=" + id + ", linkId=" + linkId + ", occurredAt=" + occurredAt + "}";
  }
}
