package dev.harshil.shortener.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.support.TestProfile;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestProfile
class LinkRepositoryTest {

  private static final Instant CREATED = Instant.parse("2026-10-12T09:30:00Z");

  @Autowired private LinkRepository links;

  @Test
  void findByCode_knownCode_returnsTheLinkWithItsFields() {
    links.save(new Link("known01", "https://example.com/page", CREATED));

    assertThat(links.findByCode("known01"))
        .hasValueSatisfying(
            link -> {
              assertThat(link.getId()).isNotNull();
              assertThat(link.getCode()).isEqualTo("known01");
              assertThat(link.getOriginalUrl()).isEqualTo("https://example.com/page");
              assertThat(link.getCreatedAt()).isEqualTo(CREATED);
            });
  }

  @Test
  void findByCode_unknownCode_returnsEmpty() {
    assertThat(links.findByCode("missing")).isEmpty();
  }

  @Test
  void findByCode_differentCase_returnsEmptyBecauseCodesAreCaseSensitive() {
    links.save(new Link("CaseAbc", "https://example.com/case", CREATED));

    assertThat(links.findByCode("caseabc")).isEmpty();
  }

  @Test
  void existsByCode_knownAndUnknownCodes_reportPresence() {
    links.save(new Link("exists1", "https://example.com/e", CREATED));

    assertThat(links.existsByCode("exists1")).isTrue();
    assertThat(links.existsByCode("exists2")).isFalse();
  }

  @Test
  void deleteByCode_knownCode_removesTheRowAndReturnsOne() {
    links.save(new Link("delete1", "https://example.com/d", CREATED));

    long removed = links.deleteByCode("delete1");

    assertThat(removed).isEqualTo(1);
    assertThat(links.existsByCode("delete1")).isFalse();
  }

  @Test
  void deleteByCode_unknownCode_returnsZero() {
    assertThat(links.deleteByCode("nothing")).isZero();
  }

  @Test
  void saveAndFlush_duplicateCode_throwsDataIntegrityViolationNamingTheConstraint() {
    links.saveAndFlush(new Link("twice01", "https://example.com/1", CREATED));

    assertThatThrownBy(
            () -> links.saveAndFlush(new Link("twice01", "https://example.com/2", CREATED)))
        .isInstanceOf(DataIntegrityViolationException.class)
        .message()
        .containsIgnoringCase("uk_links_code");
  }
}
