package dev.harshil.shortener;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.support.TestProfile;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Proves the application context starts and the Flyway baseline schema is in place. */
@SpringBootTest
@TestProfile
class UrlShortenerApplicationTest {

  @Autowired private DataSource dataSource;

  @Test
  void contextStarts_appliesMigrationV1_createsLinksAndClickEventsTables() {
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
    assertThat(version).isEqualTo(1);
  }
}
