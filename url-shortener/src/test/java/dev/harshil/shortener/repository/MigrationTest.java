package dev.harshil.shortener.repository;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.support.TestProfile;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Proves migrations V1 and V3 through the database catalogue rather than through Hibernate, so the
 * named constraints and the index the design relies on (3.5) exist exactly as written, and so the
 * privacy position (ADR 0006, R-03) is enforced by the schema: {@code click_events} has exactly the
 * seven columns the two migrations add and nothing that could hold an address or a user agent.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestProfile
class MigrationTest {

  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;

  @BeforeEach
  void createTemplate() {
    jdbc = new JdbcTemplate(dataSource);
  }

  @Test
  void flywayHistory_versionOne_isAppliedSuccessfully() {
    Integer applied =
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where version = '1' and success = true",
            Integer.class);

    assertThat(applied).isEqualTo(1);
  }

  @Test
  void v2AddsNullableExpiresAt() {
    Integer applied =
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where version = '2' and success = true",
            Integer.class);
    String nullable =
        jdbc.queryForObject(
            "select is_nullable from information_schema.columns "
                + "where lower(table_name) = 'links' and lower(column_name) = 'expires_at'",
            String.class);
    String dataType =
        jdbc.queryForObject(
            "select data_type from information_schema.columns "
                + "where lower(table_name) = 'links' and lower(column_name) = 'expires_at'",
            String.class);

    assertThat(applied).isEqualTo(1);
    assertThat(nullable).isEqualTo("YES");
    // H2 reports catalogue names in lower case under DATABASE_TO_LOWER; PostgreSQL would too.
    assertThat(dataType).isEqualToIgnoringCase("TIMESTAMP WITH TIME ZONE");
  }

  @Test
  void links_haveUniqueConstraintOnCode_namedUkLinksCode() {
    Integer constraints =
        jdbc.queryForObject(
            "select count(*) from information_schema.table_constraints "
                + "where lower(constraint_name) = 'uk_links_code' "
                + "and constraint_type = 'UNIQUE' and lower(table_name) = 'links'",
            Integer.class);

    assertThat(constraints).isEqualTo(1);
  }

  @Test
  void clickEvents_haveForeignKeyToLinks_namedFkClickEventsLink_withCascadeDelete() {
    String deleteRule =
        jdbc.queryForObject(
            "select rc.delete_rule from information_schema.referential_constraints rc "
                + "where lower(rc.constraint_name) = 'fk_click_events_link'",
            String.class);

    assertThat(deleteRule).isEqualTo("CASCADE");
  }

  @Test
  void clickEvents_haveIndexOnLinkIdAndOccurredAt_namedIxClickEventsLinkTime() {
    List<String> columns =
        jdbc.queryForList(
            "select lower(column_name) from information_schema.index_columns "
                + "where lower(index_name) = 'ix_click_events_link_time' order by ordinal_position",
            String.class);

    assertThat(columns).containsExactly("link_id", "occurred_at");
  }

  @Test
  void clickEvents_storeNoVisitorData_exactlyTheSevenColumnsOfV1AndV3() {
    List<String> columns =
        jdbc.queryForList(
            "select lower(column_name) from information_schema.columns "
                + "where lower(table_name) = 'click_events' order by ordinal_position",
            String.class);

    assertThat(columns)
        .containsExactly(
            "id",
            "link_id",
            "occurred_at",
            "referrer_host",
            "utm_source",
            "utm_medium",
            "utm_campaign");
  }

  @Test
  void flywayHistory_versionThree_isAppliedSuccessfully() {
    Integer applied =
        jdbc.queryForObject(
            "select count(*) from flyway_schema_history where version = '3' and success = true",
            Integer.class);

    assertThat(applied).isEqualTo(1);
  }

  @Test
  void v3AddsNullableAttributionColumns() {
    List<String> columns =
        jdbc.queryForList(
            "select lower(column_name) || '|' || is_nullable || '|' || lower(data_type) || '|' "
                + "|| cast(character_maximum_length as varchar) "
                + "from information_schema.columns where lower(table_name) = 'click_events' "
                + "and lower(column_name) in "
                + "('referrer_host', 'utm_source', 'utm_medium', 'utm_campaign') "
                + "order by ordinal_position",
            String.class);

    assertThat(columns)
        .containsExactly(
            "referrer_host|YES|character varying|253",
            "utm_source|YES|character varying|64",
            "utm_medium|YES|character varying|64",
            "utm_campaign|YES|character varying|64");
  }
}
