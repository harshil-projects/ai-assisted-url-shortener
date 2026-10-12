package dev.harshil.shortener.repository;

import dev.harshil.shortener.domain.Link;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/** Persistence for {@link Link}; every lookup is by the unique code clients present. */
public interface LinkRepository extends JpaRepository<Link, Long> {

  Optional<Link> findByCode(String code);

  boolean existsByCode(String code);

  /**
   * Removes the link with the given code; the database cascade removes its click events.
   *
   * <p>Declared read-write explicitly: a derived delete loads then removes the entity, which
   * requires a writable transaction, and the repository default for query methods is read-only.
   *
   * @param code the code to delete
   * @return the number of links removed, 0 or 1
   */
  @Transactional
  long deleteByCode(String code);
}
