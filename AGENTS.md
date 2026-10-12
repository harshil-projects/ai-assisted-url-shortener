# AGENTS.md

Instructions for any AI coding assistant working in this repository. The engineer leads; the assistant drafts inside a task spec. Read this file before writing anything. `CLAUDE.md` and `.github/copilot-instructions.md` point here; this is the single source.

## Project

| Item | Value |
|---|---|
| Service | URL shortener on Spring Boot 4.1.1, Spring Security 7, Java 21, Maven Wrapper |
| Modules | `url-shortener` (the service), `harness-check` (JUnit rules over `harness/` and the git history) |
| Package | `dev.harshil.shortener` with sub-packages `api`, `service`, `domain`, `repository`, `config` |
| Design | `docs/design/2026-10-12-system-design.md` is the source of truth for behaviour; cite its section numbers |
| Process data | `harness/` holds requirements, task specs, prompts, raw AI output, ledger, reviews, policy |

## Commands

| Purpose | Command |
|---|---|
| Run the service, dev profile, H2 file under `./data` | `.\mvnw.cmd -pl url-shortener spring-boot:run` or `scripts/run.ps1 [-Port 9090]` |
| Full build with every gate | `.\mvnw.cmd verify` |
| Full build plus the perf smoke test | `.\mvnw.cmd -Pperf verify` |
| Snapshot the raw AI diff for a task | `scripts/snap.ps1 T-NNN` |
| Format, verify, and append the gate record | `scripts/gate.ps1 T-NNN [-Perf]` |
| Regenerate `docs/reports/traceability.md` | `scripts/report.ps1` |
| Walk the API against a running service | `scripts/demo.ps1 [-BaseUrl http://localhost:8080] [-ApiKey dev-only-key-do-not-use]` |
| Enable the commit message hook | `git config core.hooksPath scripts/hooks` |

## Lifecycle, one line per step

1. Normalize: the engineer writes intent, ambiguities, assumptions, and EARS acceptance criteria in `harness/requirements/R-0N.md`.
2. Decompose: task specs with dependencies in `harness/tasks/T-NNN.yaml`, shaped by `harness/templates/task.template.yaml`.
3. Prompt: a playbook from `harness/playbooks/` plus the task spec becomes `harness/prompts/T-NNN/v1.md`, committed before use.
4. Generate: the assistant drafts into the working tree. Nothing is committed yet.
5. Snapshot: `scripts/snap.ps1 T-NNN` saves the raw diff to `harness/evidence/T-NNN/ai-raw.patch` before any edit.
6. Review: the engineer marks each file generated, edited, or rejected with a rationale in `harness/ledger.yaml`.
7. Gate: `scripts/gate.ps1 T-NNN` runs format, style, SAST, tests, coverage, and traceability, and appends the result.
8. Sign-off: high-impact tasks (schema, security, dependency, data-deletion) get `harness/reviews/T-NNN.md` from a fresh context and a sign-off record.
9. Commit: Conventional Commit with a `Task: T-NNN` trailer.
10. Harness check: runs inside `mvnw verify`; a violation names the rule and the file; the report is regenerated from data.

## Conventions (design section 4.3)

| Rule | Enforced by |
|---|---|
| Google Java Style | Spotless with google-java-format, Checkstyle |
| Constructor injection only; no field `@Autowired` | Checkstyle regex |
| Methods under 50 lines, files under 400 lines | Checkstyle `MethodLength`, `FileLength` |
| DTOs are records with boxed types and `@NotNull`; Jackson 3 turns a missing primitive into a generic 400 | Review |
| Each domain exception maps to exactly one Problem type in `ApiExceptionHandler` | Test |
| No `synchronized` around I/O in application code | Review |
| No raw user input in a log statement without `LogSanitizer` | FindSecBugs pattern, review |
| Tests: one behaviour per test; names `method_scenario_expectedOutcome`; no logic in tests; `SAME_THREAD` timeouts; `@AfterEach` table cleanup in integration tests | Review |
| Tests and implementation are not changed in the same prompt unless the task spec says so | Review, SAI-06 |
| Boot 4 idioms only; rejected Boot 3 drafts are recorded in the ledger as evidence | The gotchas below |

## Boot 4 and Spring Security 7 gotchas

| Use | Never |
|---|---|
| `spring-boot-starter-webmvc` | `spring-boot-starter-web` |
| `tools.jackson.*` (Jackson 3) | `com.fasterxml.jackson.*` |
| `spring-boot-starter-flyway` with Flyway managed by the Boot BOM | Unmanaged Flyway coordinates |
| Test starters `spring-boot-starter-webmvc-test`, `spring-boot-starter-data-jpa-test`, `spring-boot-starter-security-test` | `spring-boot-starter-test` alone for slices |
| `org.springframework.test.context.bean.override.mockito.MockitoBean` | `@MockBean`, `@SpyBean` |
| `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` with `MockMvcTester` | `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` |
| `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc` | The Boot 3 package of the same annotation |
| `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` | `org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` |
| `org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase` | The Boot 3 package of the same annotation |
| `org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient` with `org.springframework.test.web.servlet.client.RestTestClient` | `TestRestTemplate`, `WebTestClient` for MVC tests |
| Security lambda DSL, for example `http.authorizeHttpRequests(a -> a.requestMatchers("/api/v1/**").authenticated())` | `and()`, `authorizeRequests()`, `antMatchers()`, `AntPathRequestMatcher` |
| API key filter constructed inside `SecurityConfig` and added with `addFilterBefore(..., AuthorizationFilter.class)` | Also marking the filter `@Component`; it would run twice |
| Click executor bean declared with `defaultCandidate = false` | Displacing Boot's application task executor |
| `Instant` fields on `TIMESTAMP WITH TIME ZONE` columns with `ddl-auto=validate` | `LocalDateTime`, `TIMESTAMP` without zone |
| One `ALTER TABLE ... ADD COLUMN` per statement | Multi-column `ALTER`; H2 and PostgreSQL disagree on it |
| Mockito agent on the Surefire `argLine` for JDK 21 | Ignoring the inline mock maker warning |
| `spring.threads.virtual.enabled=false`; the H2 driver pins carriers on Java 21 | Enabling virtual threads with H2 |
| Every test under Surefire with the project's `@ActiveProfiles("test")` meta-annotation | Failsafe or a second JaCoCo exec file |
| Every version managed by the Boot BOM or pinned in the pom | Unmanaged or floating versions |

## Security rules (design section 3.7)

- Redirect only to the stored URL validated at create time; never reflect request input; schemes `http` and `https` only; codes are non-enumerable.
- `UrlValidator` rejects loopback, RFC 1918, link-local, `0.0.0.0/8`, multicast, `::1`, `fc00::/7`, `fe80::/10`, `169.254.169.254`, `localhost`, and `metadata.google.internal`; no DNS resolution; the service never fetches a target.
- Codes are random 7-character Base62 from `SecureRandom`; the rate limiter runs before authentication so key guessing is throttled.
- JPA parameter binding only; FindSecBugs runs in the build.
- `LogSanitizer` strips CR, LF, and control characters from every user-controlled value before logging; never log full target URLs; client request ids are bounded to 64 characters of `[A-Za-z0-9-]`.
- The API returns JSON only; Swagger UI runs under its own Content-Security-Policy.
- API key required on `/api/v1/**` and on actuator in the prod profile; public paths are redirect, health, and API docs only; 401 carries `WWW-Authenticate`.
- Bounds: URL 2048 characters, body 16 KiB on `/api/v1/**`, bounded click executor and queue, 2-second connection timeout.
- No secrets in the repository; the key comes from `APP_API_KEY`; `.gitignore` covers `.env*`, `data/`, `*.mv.db`; gitleaks runs in CI.
- Every version is managed by the Boot BOM or pinned; Dependabot watches them.
- Actuator exposure is explicit; the H2 console is dev only; `ddl-auto=validate`; stack traces never appear in responses.
- No IP address or raw user agent is persisted.

## Dependencies

Verify every Maven coordinate on Maven Central before suggesting it and state the URL, in the form `https://central.sonatype.com/artifact/<groupId>/<artifactId>/<version>` or the `https://repo1.maven.org/maven2/<group path>/<artifactId>/<version>/` directory. An unverified coordinate is rejected. Do not edit a pom unless the task spec names it; a dependency change is high impact and needs a sign-off.

## Out of scope

- User accounts, per-user ownership, per-user audit of deletes.
- Active expiry purge or retention deletion; expired rows are kept.
- Distributed cache, queue, key generation service, rollup tables.
- Server-side fetching of target URLs.
- Idempotency keys, load testing, dependency CVE scanning in the default build, compile-time semantic checks beyond the compiler.
- Docker image, unless all three scenarios are complete.

## Commits

- Conventional Commits 1.0.0 header, for example `feat(redirect): return 410 for expired links`.
- Every non-merge commit carries a `Task: T-NNN` trailer; `scripts/hooks/commit-msg` enforces both rules and harness-check verifies the task exists.
- No AI product names in commit messages, descriptions, or trailers. The tool and model are recorded in the ledger entry the trailer resolves to.
- Never name the hiring firm anywhere in the repository; say "the assignment brief".
