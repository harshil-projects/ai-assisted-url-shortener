package dev.harshil.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.repository.LinkRepository;
import dev.harshil.shortener.support.TestProfile;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs against the real H2 schema with no surrounding test transaction, so every {@code insert}
 * commits or rolls back exactly as it does in production and the unique constraint is the arbiter.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(LinkAllocator.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestProfile
class LinkAllocatorTest {

  private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

  @Autowired private LinkAllocator allocator;
  @Autowired private LinkRepository links;

  @AfterEach
  void cleanTables() {
    links.deleteAllInBatch();
  }

  @Test
  void insert_newCode_commitsARowThatIsVisibleAfterTheCall() {
    Link inserted = allocator.insert("alloc01", "https://example.com/a", NOW, null);

    assertThat(inserted.getId()).isNotNull();
    assertThat(links.findByCode("alloc01"))
        .hasValueSatisfying(
            link -> {
              assertThat(link.getOriginalUrl()).isEqualTo("https://example.com/a");
              assertThat(link.getCreatedAt()).isEqualTo(NOW);
            });
  }

  @Test
  void insert_withExpiry_persistsTheExpiryInstantInTheNewColumn() {
    Instant expiresAt = NOW.plusSeconds(86_400);

    allocator.insert("expiry1", "https://example.com/e", NOW, expiresAt);

    assertThat(links.findByCode("expiry1"))
        .hasValueSatisfying(link -> assertThat(link.getExpiresAt()).isEqualTo(expiresAt));
  }

  @Test
  void insert_duplicateCode_throwsDataIntegrityViolationNamingTheConstraintAndKeepsTheFirstRow() {
    allocator.insert("dupe001", "https://example.com/first", NOW, null);

    assertThatThrownBy(() -> allocator.insert("dupe001", "https://example.com/second", NOW, null))
        .isInstanceOf(DataIntegrityViolationException.class)
        .message()
        .containsIgnoringCase("uk_links_code");

    assertThat(links.findByCode("dupe001"))
        .hasValueSatisfying(
            link -> assertThat(link.getOriginalUrl()).isEqualTo("https://example.com/first"));
    assertThat(links.count()).isEqualTo(1);
  }

  @Test
  void insert_afterAFailedAttempt_nextCodeStillCommitsBecauseEachAttemptIsItsOwnTransaction() {
    allocator.insert("retry01", "https://example.com/one", NOW, null);
    assertThatThrownBy(() -> allocator.insert("retry01", "https://example.com/two", NOW, null))
        .isInstanceOf(DataIntegrityViolationException.class);

    Link second = allocator.insert("retry02", "https://example.com/two", NOW, null);

    assertThat(second.getId()).isNotNull();
    assertThat(links.existsByCode("retry02")).isTrue();
    assertThat(links.count()).isEqualTo(2);
  }
}
