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
| RK-15 | Engineer cannot defend a decision live | L | H | Mitigate | Nine ADRs with alternatives; private rehearsal notes outside the repository | Not applicable |
| RK-16 | Swagger UI blank under CSP or no way to set the API key | M | M | Mitigate | CSP scoped and relaxed for Swagger paths; `@SecurityScheme` for the Authorize button; checked in a browser on day 1 | Blank page |

Risks that materialised during the build, and how they were handled, are filled at submission from the ledger's debug entries.

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
