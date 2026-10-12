# Testing approach

| Field | Value |
|---|---|
| Runner | Surefire 3.5.6 for every test, so one JaCoCo exec file covers unit, slice, and integration tests |
| Framework | JUnit Jupiter 6.0.3, AssertJ 3.27.7, Mockito 5.23.0, Awaitility 4.3.0, Boot 4 test starters |
| Profile | Every test carries one `@ActiveProfiles("test")` meta-annotation; in-memory H2 in PostgreSQL mode |
| Owner | Harshil |

## Strategy

Shares were targets by test count; the actual figures at v0.1.0 are in the table at the end of this document.

| Layer | Share by count | Scope | Tooling | Examples |
|---|---|---|---|---|
| Unit | about 65 percent | Pure logic: generator, validator, reserved words, log sanitiser, problem mapping, executor rejection | JUnit 6, AssertJ, Mockito | `CodeGeneratorTest`, `UrlValidatorTest` as a parameterised table of about 30 URLs |
| Slice | about 20 percent | Controllers with `@WebMvcTest`, `MockMvcTester`, `@Import(SecurityConfig.class)`, `@EnableConfigurationProperties(AppProperties.class)`, `@MockitoBean` services; repositories and migrations with `@DataJpaTest` | Boot 4 test starters | `LinkControllerTest`, `ClickEventRepositoryTest`, `MigrationTest` |
| Integration | about 15 percent | Full context, random port, `RestTestClient` via `@AutoConfigureRestTestClient`: create to redirect to stats; auth cases; rate limit; Problem Details from filters; actuator; OpenAPI drift; startup fail-fast for prod | `@SpringBootTest`, Awaitility | `LinkFlowIT`, `ApiKeyIT`, `RateLimitIT`, `ProblemDetailIT`, `ActuatorIT`, `OpenApiIT`, `ProdStartupIT` |
| Real-database behaviour | targeted | Allocation retry and alias race on H2 | `@DataJpaTest` or `@SpringBootTest` | `LinkAllocatorTest` |
| Regression | one per brownfield fix | Named after the defect | JUnit 6 | per T-105 |
| Performance smoke | opt-in | Percentiles over in-process requests | HdrHistogram | `RedirectLatencySmokeTest` |

## Gates

All default gates run from `mvnw verify` with no network beyond the first dependency download and no API keys.

| Gate | Tool and version | Threshold | Maven phase | When |
|---|---|---|---|---|
| Format | Spotless 3.10.4, google-java-format 1.37.0 | Zero diffs | `validate` | default |
| Style | maven-checkstyle-plugin 3.6.0 with Checkstyle 14.3.0, project `checkstyle.xml` based on `google_checks.xml` plus `MethodLength` 50, `FileLength` 400, `RegexpSingleline` against `@Autowired` on fields; `violationSeverity=warning`, `includeTestSourceDirectory=true`; Javadoc checks suppressed outside the `api` package | Zero violations | `verify` | default |
| Bug patterns and security SAST | spotbugs-maven-plugin 4.10.4.1, SpotBugs 4.10.4, findsecbugs 1.14.0, effort max, threshold medium, committed exclusion file with reasons | Zero unreviewed findings | `verify` | default |
| Tests | Surefire 3.5.6, JUnit 6.0.3, Mockito agent on the `argLine`; `excludedGroups=perf` by default | All pass | `test` | default |
| Coverage | JaCoCo 0.8.15 `check`: BUNDLE LINE at least 0.80, BRANCH at least 0.70, CLASS MISSEDCOUNT 0 excluding `*Application`, `*Properties`, DTO records | Fails build | `verify` | default |
| Environment | maven-enforcer-plugin 3.6.3: `requireJavaVersion [21,27)`, `requireMavenVersion [3.9,4)`, `banDuplicatePomDependencyVersions` | Fails build | `validate` | default |
| Process traceability | `harness-check` JUnit tests | Zero violations; git-history rule skips with a notice when no `.git` or baseline tag exists | `test` | default |
| OpenAPI drift | `OpenApiIT` compares `/v3/api-docs.yaml` with `docs/reference/openapi.yaml` | Identical | `test` | default |
| Performance smoke | `RedirectLatencySmokeTest` tagged `perf`, HdrHistogram, warm-up 200, measure 2,000, p99 under 200 ms | Fails test | `test` | `-Pperf`, Ubuntu CI job |
| Secrets | gitleaks-action v3 with full history; harness-check regex fallback | Zero findings | CI job and default | CI plus default |
| Dependency updates | Dependabot weekly | Pull requests | n/a | CI |

## Rules for tests

| Rule | Why |
|---|---|
| One behaviour per test | A failure names one cause |
| Names read `method_scenario_expectedOutcome` or `should...When...` | The test list reads as a specification |
| No logic in tests: no loops, branches, or computed expectations | A test with logic needs its own test |
| `SAME_THREAD` timeouts only | Separate-thread timeouts break Spring context and MDC assumptions |
| `@AfterEach` table cleanup in integration tests | Cached contexts share the in-memory database |
| Every AI-generated test diff is reviewed for deleted, skipped, or weakened assertions before acceptance | SAI-06; the most common way generated code goes green |
| Tests and implementation are not changed in the same prompt unless the task spec says so | Keeps the test as an independent oracle |
| Expiry tests use an injected fixed `Clock` | No sleeps, no flakiness across time zones |
| Records for DTOs use boxed types with `@NotNull` | Jackson 3 turns a missing primitive into a generic 400 that hides the field name |

## How to run everything

| Goal | Windows | Linux or macOS |
|---|---|---|
| Every default gate | `.\mvnw.cmd verify` | `./mvnw verify` |
| Default gates plus the latency smoke test | `.\mvnw.cmd verify -Pperf` | `./mvnw verify -Pperf` |
| Gate run with evidence capture for a task | `scripts\gate.ps1 T-NNN` or `scripts\gate.ps1 T-NNN -Perf` | PowerShell 7 required |
| One test class | `.\mvnw.cmd -pl url-shortener test -Dtest=LinkFlowIT` | `./mvnw -pl url-shortener test -Dtest=LinkFlowIT` |
| Harness check only | `.\mvnw.cmd -pl harness-check test` | `./mvnw -pl harness-check test` |
| Coverage report | after `verify`, open `url-shortener/target/site/jacoco/index.html` | same |
| Traceability report | `scripts\report.ps1`, then read `docs/reports/traceability.md` | `./mvnw -pl harness-check test`, then read `harness-check/target/traceability.md` |

`scripts/gate.ps1` runs `mvnw spotless:apply` first, then `mvnw -ntp verify`, tees the log to `harness/evidence/T-NNN/verify-N.log`, and appends a gate record to the ledger with a real timestamp, the overall result, the failing plugin mapped to a gate key when red, and the current commit. Red runs are kept; they are evidence.

Mockito on JDK 21 needs the agent attached. The pom uses the `maven-dependency-plugin` `properties` goal and `<argLine>@{argLine} -javaagent:${org.mockito:mockito-core:jar}</argLine>` with an empty `<argLine/>` property so JaCoCo's agent chains correctly.

## How coverage is measured and why one runner

| Aspect | Choice | Reason |
|---|---|---|
| Runner | Surefire for unit, slice, and integration tests; no Failsafe | One JaCoCo exec file, so a line covered only by an integration test counts |
| Thresholds | BUNDLE LINE 0.80, BRANCH 0.70, CLASS MISSEDCOUNT 0 | NFR-09; the class rule stops a generated class shipping untouched |
| Exclusions | `*Application`, `*Properties`, DTO records | Boilerplate with no branches; everything else must be touched |
| Enforcement | `jacoco:check` bound to `verify` fails the build | A drop below threshold cannot be merged |
| Perf test | Excluded from coverage by the `perf` tag default exclusion | Smoke timing is not behaviour coverage |

Results at submission:

| Metric | Value |
|---|---|
| Test count by layer | 419 test executions from 270 test methods: 144 unit, 63 slice, 62 integration, 1 opt-in perf (parameterised cases counted once as methods, once per row as executions) |
| Line coverage | 97.9 percent, 751 of 767 lines (gate: 80) |
| Branch coverage | 94.0 percent, 220 of 234 branches (gate: 70) |
| Classes with zero coverage | 0 of 47 analysed classes (gate: 0) |
| p99 redirect latency, in-process smoke | 119 ms over 2,000 redirects (p50 8 ms, p95 45 ms) on a loaded laptop with other builds running; the 200 ms assertion is a smoke threshold, not a capacity claim |

## What is not tested and why

| Area | Not tested | Why |
|---|---|---|
| PostgreSQL path | Running the migrations and service on PostgreSQL | Reviewers have no Docker; migrations use the shared SQL subset and are documented as the path |
| Structured logging | ECS JSON output in the prod profile | Property is set; the assertion was cut from the timebox |
| Graceful shutdown | Timing of the 20-second phase and 10-second executor drain | Configuration reviewed and documented; a timing test would be slow and flaky |
| Database outage | A live outage producing 503 end to end | Exception mapping is unit-tested; simulating a lost H2 file under a running context is not deterministic |
| DNS-based bypass | A hostname that resolves to a private address | No DNS resolution at create by design; recorded as an accepted risk |
| Capacity | Throughput, concurrency limits, soak | Local smoke only; no capacity claims are made |
| HSTS | Header on HTTPS | Reviewers run plain HTTP; the header appears only on HTTPS |
| Trusted proxies | `X-Forwarded-For` handling | `server.forward-headers-strategy=NONE` by default; the how-to is documented, not exercised |
| Dependency CVEs | NVD-backed scan in the build | Dependabot alerts only; a scan is proxy-hostile and slow for reviewers |
| Multi-instance | Behaviour behind a load balancer | Single node by design; the scaling path is documented |
| Swagger UI | Automated browser check | Manual browser check with the Authorize button, recorded in the greenfield scenario document |
| Harness judgement | Whether a rationale is honest or a review was careful | The harness checks presence, consistency, and computed kept ratios; judgement is the reviewer's job |
