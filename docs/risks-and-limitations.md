# Risks and limitations

| Field | Value |
|---|---|
| Owner | Harshil |
| Date | 2026-10-12 |
| Scope | Delivery risks for the three-day build, and the product limitations accepted for a reviewer-runnable prototype |

## Risk register

Likelihood and impact are H, M, or L. Response is one of Mitigate, Eliminate, Accept.

| ID | Risk | L | I | Response | Mitigation | Trigger |
|---|---|---|---|---|---|---|
| RK-01 | Timebox overrun leaves scenarios incomplete | H | H | Mitigate | Cuts already applied; descoping ladder; gates wired on day 1 | Day 2 afternoon not started on brownfield |
| RK-02 | AI emits Boot 3 idioms that do not compile on Boot 4 | H | M | Mitigate | Gotchas in `AGENTS.md`; rejections recorded in the ledger as evidence | Compile errors citing `com.fasterxml` or `@MockBean` |
| RK-03 | Reviewer behind a proxy or TLS inspection cannot download Maven or dependencies | M | H | Mitigate | README: `settings.xml` proxy block, `MAVEN_OPTS=-Djavax.net.ssl.trustStoreType=Windows-ROOT`, manual Maven zip fallback, link to the green Windows CI run | Reviewer reports wrapper failure |
| RK-04 | Reviewer has JDK 8 or 17 on `JAVA_HOME` | H | H | Mitigate | Enforcer fails fast with a clear message; README gives the session-only override and the no-admin zip path | Enforcer failure |
| RK-05 | H2 file lock when tests run while the dev server is up | H | L | Eliminate | Tests use in-memory H2 under the test profile | Lock error |
| RK-06 | Hallucinated Maven coordinates | M | M | Mitigate | SAI-05 verification with URL recorded | Resolution failure |
| RK-07 | AI weakens or deletes tests to go green | M | H | Mitigate | SAI-06 review rule; CLASS MISSEDCOUNT 0 | Coverage drop or skipped tests |
| RK-08 | Coverage gate blocks late in the day | M | M | Mitigate | Gate active from T-001; single runner so integration coverage counts | Coverage under 80 percent |
| RK-09 | Open redirect abuse by design | H | M | Accept with controls | Validation, non-enumerable codes, rate limits, documented residual | Not applicable |
| RK-10 | Log injection in generated code | M | M | Mitigate | `LogSanitizer`, FindSecBugs pattern, review item | SAST finding |
| RK-11 | Click events lost on crash after the drain window | M | L | Accept | Documented; counted when the executor rejects | Not applicable |
| RK-12 | Deleted alias can be re-registered by another party | M | M | Accept in greenfield | Risk recorded; candidate for T-105 only if surfaced as a defect | Not applicable |
| RK-13 | Perf smoke test flaky on CI runners | M | L | Mitigate | Opt-in profile, generous threshold, warm-up, Ubuntu job only | Spurious failures |
| RK-14 | Ledger backfilled from memory, uniform timestamps, one voice | M | H | Mitigate | Scripts write timestamps and logs; raw patches captured before review; prompts committed before use | Report shows identical timestamps |
| RK-15 | Engineer cannot defend a decision live | L | H | Mitigate | Nine ADRs with alternatives; the engineer's own walkthrough of each record before presenting | Not applicable |
| RK-16 | Swagger UI blank under CSP or no way to set the API key | M | M | Mitigate | CSP scoped and relaxed for Swagger paths; `@SecurityScheme` for the Authorize button; checked in a browser on day 1 | Blank page |

## Risks that materialised during the build

Taken from the `debug` entries in `harness/ledger.yaml`; each has a hypothesis, a finding and a fix there.

| When | What happened | Related risk | How it was handled | Evidence |
|---|---|---|---|---|
| T-000 | google-java-format 1.37.0 failed on every file under Spotless 3.10.4 (new commonmark dependency not loaded) | RK-06 | Pinned to 1.36.1, the version Spotless ships; JDK exports kept in `.mvn/jvm.config` | `harness/evidence/T-000/pre-gate-run-1.log`, `-2.log` |
| T-000 | `ProblemDetails.toMap` dereferenced a null type on a bare `ProblemDetail` | RK-02 | Caught by its unit test and by SpotBugs; defaulted to `about:blank` | ledger T-000 debug |
| T-000 | First CI run red on Windows: HC-12 flagged planned tasks whose impact analysis did not exist yet | RK-14 | Rule limited to done tasks; synthetic test added | ledger T-000 debug; CI run 38194833323 |
| T-000 | First CI run red on Ubuntu: `mvnw` committed without the executable bit | RK-04 | `git update-index --chmod=+x` | same CI run |
| T-000 | Marking a task done before its gate made HC-04 fail inside that very run | RK-14 | Lifecycle order fixed: gate, then done, then commit; red log kept | `harness/evidence/T-000/verify-1.log` |
| T-001 | Checkstyle suppression path pattern matched only Linux paths, silently suppressing Javadoc rules on Windows | RK-02 | Portable separator class; Ubuntu runner caught the missing Javadoc | ledger T-001 debug; CI run 38196102890 |
| T-001 | gitleaks flagged the random password Boot logged for its auto-configured default user | RK-10 | Default user auto-configuration excluded; allowlist entries with reasons in `.gitleaks.toml` | CI run 38196733778 |
| T-003 | Numeric shorthand hosts (`http://2130706433/`) passed the block list because `URI` treats them as names | RK-09 | Rejected as ambiguous hosts; four test cases added | ledger T-003 debug |
| T-008 | springdoc documented 200 for operations returning 201, 204 and 302 | RK-16 | Explicit `@ResponseStatus` and `@ApiResponse`; export regenerated; drift test guards it | ledger T-008 debug |
| T-007 | Boot 4 moved `SecurityProperties.DEFAULT_FILTER_ORDER` and renamed `PAYLOAD_TOO_LARGE` | RK-02 | Boot 4 names used; recorded in `AGENTS.md` gotchas | ledger T-007 debug |

## Follow-ups from the fresh-context review (T-010)

`harness/reviews/greenfield-review.md` judged 30 candidates. The hardening-header defect became
T-105. The other candidates marked real are accepted as open follow-ups, each with a reproduction
note in the review:

| # | Follow-up | Severity |
|---|---|---|
| 1 | Map `CannotCreateTransactionException` (pool timeout inside a transaction) to the 503 Problem, not 500 | should-fix |
| 2 | Clear the global API-key requirement on the public redirect operations in the OpenAPI export | note |
| 4 | OpenAPI error catalogue made per operation: 409, 413, 415 and 429 on create only, 401 and 404 on management, 404 and 410 on the redirect | fixed after the review (T-008 ledger entry) |
| 5 | One Problem shape for `instance` between filter-written and handler-written bodies; add the handler case to `ProblemDetailIT` | should-fix |
| 8 | Emit `X-RateLimit-Reset` on every create or document it as 429-only | note |
| 13 | Add the real-database collision and alias-race tests the task specs name | should-fix |
| 15 | Make the OpenAPI drift test fail, not skip, when the committed export is missing | note |
| 20 | Decide whether key guessing on read endpoints is throttled, or narrow the claim | should-fix, security |
| 22 | Block `*.localhost` names in the URL validator | should-fix, security |
| 26 | Render firewall rejections and `/error` as Problem Details | note, security |
| 28 | Use a bulk `@Modifying` delete so two concurrent deletes yield 204 and 404, never 500 | should-fix, data-deletion |
| 29 | Prove the click executor thread and the saturation drop with tests | note |

## Limitations found while building

| Area | Limitation | Trade-off accepted | What production would do |
|---|---|---|---|
| SAST reach | FindSecBugs 1.14.0 taint rules cover `javax.servlet`, not `jakarta.servlet`, so header and parameter handling in the filters is not taint-analysed | The regex allow-list on the request id and bounded parsing in the validator are the controls, verified by tests | Add a taint-aware scanner with Jakarta support (CodeQL or Semgrep rules) to CI |
| Host names | No IDNA normalisation; two spellings of one internationalised name could both be stored | Rare for this prototype | Normalise with `java.net.IDN` at validation |
| Rate limiting | Keyed on the socket address; behind a proxy all clients share one bucket until `server.forward-headers-strategy` is configured for that proxy | No proxy in the reviewer setup | Configure the trusted proxy and key on the forwarded address |

## Limitations and trade-offs

| Area | Limitation | Trade-off accepted | What production would do |
|---|---|---|---|
| Storage | H2 file, single process; PostgreSQL path documented but untested | Portability for reviewers over production realism | PostgreSQL with managed backups and read replicas; the migrations already target the shared SQL subset |
| Analytics | One aggregate query, no rollups; events may be lost after the shutdown drain window | Simplicity and redirect isolation over exactly-once accounting | Durable queue with at-least-once delivery and idempotent consumers; hourly rollup tables with a watermark |
| Validation | No DNS resolution, so a hostname that later resolves to a private address passes | Latency and abuse exposure of resolving at create time | Resolve at create against the deny-list with a short cache, and re-check at redirect for links older than the cache window |
| Security | One shared API key; no per-user ownership or audit of who deleted what | Scope | Per-client keys or OAuth2 client credentials, an owner column on `links`, an audit log of deletes |
| Rate limiting | Per-IP in process; shared NAT users share a bucket; resets on restart | No external infrastructure | Bucket4j Redis backend or gateway limiting, keyed on the API key once authenticated and on IP before |
| Expiry | No active purge; expired rows accumulate | Correct 410 and preserved history over disk economy | Scheduled retention job deleting expired rows in batches after a grace period, select ids then delete, on a separate transactional bean |
| Delete | Hard delete; alias may be reclaimed | Timebox; recorded as RK-12 | Soft delete with `deleted_at` and alias tombstones so an alias cannot be reclaimed |
| Threads | Platform threads | H2 driver pinning on Java 21; a one-property switch later | Virtual threads once the JDBC driver is PostgreSQL's, or Java 25 where JEP 491 removes pinning |
| HSTS | Not visible on local HTTP | Documented | TLS termination at the load balancer with HSTS and preload |
| Perf numbers | Local smoke only; no capacity claims | Honesty over invented numbers | Load tests in a staging environment with Gatling or k6; SLOs with error budgets |
| AI sandboxing | Native Windows runs the assistant unsandboxed | Stated in policy; deny rules and manual approval are the controls | Devcontainer or VM with no egress by default and a bot identity separate from the engineer's |
| Harness | Validates presence, consistency, and computed kept ratios, not the quality of a rationale | Judgement is the reviewer's job | Pull request review by a second engineer; the harness stays a consistency check |
| Dependency CVEs | Dependabot alerts only; no NVD-backed scan in the build | Proxy-hostile and slow for reviewers; documented how it would be added | OWASP dependency-check or an equivalent scanner in CI with a mirrored NVD feed behind the proxy |
| Idempotency | No idempotency key on create; a retried POST creates a second link | Cut after feasibility review | `Idempotency-Key` header stored with a short TTL, returning the first response on replay |
| Tracing | No distributed tracing | Timebox | Micrometer Tracing bridge with OpenTelemetry export |
