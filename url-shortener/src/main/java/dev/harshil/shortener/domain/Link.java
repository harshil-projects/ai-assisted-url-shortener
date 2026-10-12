package dev.harshil.shortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * A short link: the code clients follow and the validated target it resolves to.
 *
 * <p>Equality is defined by {@code code}, which is unique in the database and the only identity
 * clients ever see; the surrogate {@code id} exists for the foreign key from click events. {@link
 * #toString()} deliberately omits the target URL so an entity that lands in a log line or an
 * exception message never reveals where a link points (design 3.5, NFR-05). The entity has no
 * setters: a link is immutable after creation, and the database cascade, not an entity collection,
 * removes its click events when it is deleted.
 */
@Entity
@Table(name = "links")
public class Link {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "code", nullable = false, length = 32, unique = true)
  private String code;

  @Column(name = "original_url", nullable = false, length = 2048)
  private String originalUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  /** JPA only; Hibernate populates the fields reflectively. */
  protected Link() {}

  /**
   * Creates a link ready to be persisted; the identifier is assigned by the database.
   *
   * @param code the unique short code, already validated by the service
   * @param originalUrl the canonical target URL, already validated by the service
   * @param createdAt the creation instant from the application clock
   */
  public Link(String code, String originalUrl, Instant createdAt) {
    this.code = code;
    this.originalUrl = originalUrl;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getOriginalUrl() {
    return originalUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof Link link)) {
      return false;
    }
    return Objects.equals(code, link.code);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(code);
  }

  /** Omits the target URL on purpose; see the class comment. */
  @Override
  public String toString() {
    return "Link{id=" + id + ", code=" + code + ", createdAt=" + createdAt + "}";
  }
}
