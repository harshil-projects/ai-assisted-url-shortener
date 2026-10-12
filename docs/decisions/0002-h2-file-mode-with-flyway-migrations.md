---
status: accepted
date: 2026-10-12
decision-makers: Harshil
---

# H2 in file mode with Flyway migrations validated at startup

In the context of a single-node prototype reviewed on laptops without Docker or admin rights, facing the need for durable storage plus reviewable schema change, we decided for H2 in file mode with Flyway migrations, JPA `ddl-auto=validate`, and SQL written in the intersection accepted by H2's PostgreSQL mode and PostgreSQL, and neglected PostgreSQL via Docker, SQLite, and in-memory only, to achieve zero-install persistence with a credible path to PostgreSQL, accepting that the PostgreSQL path is documented but untested in this timebox.

## Context and Problem Statement

Reviewers cannot run Docker or install a database server. Links must survive a restart so a create followed by a redirect works across runs. The brownfield scenario needs a real, reviewable migration. Schema drift between entities and tables must fail at startup, not at the first query. Which storage and schema tooling meets all four with nothing beyond a JDK?

## Decision Drivers

| Driver | Weight |
|---|---|
| No install, no admin rights, no Docker on reviewer machines | High |
| Schema change as a reviewed file, not a side effect of entity edits | High |
| Startup fails on entity and schema drift | High |
| Realistic path to a production database | Medium |
| Test isolation from the running dev server | Medium |

## Considered Options

1. H2 file mode, Flyway, `ddl-auto=validate`, SQL in the H2 and PostgreSQL intersection
2. PostgreSQL in Docker with Testcontainers for tests
3. SQLite through a JDBC driver
4. H2 in-memory only with `ddl-auto=create`

## Decision Outcome

Chosen option 1. The dev profile uses `jdbc:h2:./data/urlshortener;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_ON_EXIT=FALSE`. Flyway owns the schema; Hibernate only validates it. Every migration is a single-statement-per-column SQL file that both H2 in PostgreSQL mode and PostgreSQL accept, so moving to PostgreSQL is a datasource change plus two BOM-managed artifacts. The test profile uses an in-memory H2 in the same mode, so slices and integration tests validate migrations in the dialect mode dev runs.

### Consequences

| Kind | Consequence |
|---|---|
| Good | Nothing to install; the database file is created on first run |
| Good | Each schema change is a reviewable file and a sign-off trigger |
| Good | `ddl-auto=validate` turns drift into a startup failure |
| Good | In-memory test profile eliminates the H2 file lock when tests run beside the dev server |
| Bad | H2 is not a production database and `MODE=PostgreSQL` is an approximation |
| Bad | The H2 JDBC driver pins virtual threads on Java 21, so platform threads are used |
| Bad | The PostgreSQL path is untested in the timebox |

### Confirmation

| Check | Where defined |
|---|---|
| `MigrationTest` runs Flyway under the test profile with `@DataJpaTest` | Section 4.2 slice tests, NFR-10 |
| Application context starts with `ddl-auto=validate` in every integration test | NFR-10 |
| `ClickEventRepositoryTest` and `LinkAllocatorTest` run against the migrated schema | Section 4.2 |

## Pros and Cons of the Options

| Option | Pros | Cons |
|---|---|---|
| H2 file, Flyway, validate | No install; durable across restarts; reviewable migrations; same SQL later on PostgreSQL | Not production grade; mode is an approximation; driver pins virtual threads |
| PostgreSQL in Docker | Production realism; exact dialect | Reviewers have no Docker or admin rights; the build would not be green on their machines |
| SQLite | Single file; no install | Weak `ALTER TABLE`; no `TIMESTAMP WITH TIME ZONE`; Hibernate dialect is community maintained |
| H2 in-memory only | Simplest | Data lost on restart; `ddl-auto=create` hides schema change from review |

## More Information

Spring Boot reference sections on SQL databases and database initialization; Flyway Java API reference; H2 features, ALTER TABLE grammar, and changelog; Hibernate ORM 7.0 migration guide and `Instant` mapping; HikariCP README; Spring Boot 4.1.1 managed dependency coordinates.
