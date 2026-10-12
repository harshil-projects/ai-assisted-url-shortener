package dev.harshil.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.support.TestProfile;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves the application context starts and every committed Flyway migration is in place: V1
 * creates the two tables and V3 (click attribution, R-03) is the latest version applied.
 */
@SpringBootTest
@TestProfile
class UrlShortenerApplicationTest {

  /** The newest migration under db/migration; bump it when a migration is added. */
  private static final int LATEST_MIGRATION_VERSION = 3;

  @Autowired private DataSource dataSource;

  @Test
  void contextStarts_appliesEveryCommittedMigration_createsLinksAndClickEventsTables() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    Integer tables =
        jdbc.queryForObject(
            "select count(*) from information_schema.tables "
                + "where lower(table_name) in ('links', 'click_events')",
            Integer.class);
    Integer version =
        jdbc.queryForObject(
            "select max(version) from flyway_schema_history where success = true", Integer.class);

    assertThat(tables).isEqualTo(2);
    assertThat(version).isEqualTo(LATEST_MIGRATION_VERSION);
  }
}
