# Final engineering summary

The one-page account the assignment brief asks for: plan and rationale, artifacts, risks, trade-offs
and validation, assumptions, limitations, and what the AI got wrong. Numbers marked with the
traceability report come from `docs/reports/traceability.md`, which `harness-check` generates from
the ledger; everything else links to the file that proves it.

## What was built

A production-shaped URL shortener on Java 21 and Spring Boot 4.1.1: create, redirect, statistics
and delete behind an API key, with link expiry (brownfield scenario) and click attribution by
referring host and campaign parameters (ambiguous scenario). It runs from one command on a laptop
with JDK 21 and nothing else, and `mvnw verify` runs every quality gate plus the checks over the
engineering process itself.

## Plan and rationale

| Decision | Why | Where it is argued |
|---|---|---|
| Engineer-led, AI inside task boundaries | The brief asks for AI-accelerated execution, not orchestration; every change needed an owner who could defend it | `docs/execution-approach.md`, ADR 0008 |
| Process as data checked by the build | A claim such as "every file was reviewed" is worthless unless something fails when it is false | `harness/`, `harness-check/`, ADR 0008 |
| Prompt committed before use, raw output snapshotted before edits | Makes generated, edited and rejected checkable, not asserted | `harness/prompts/`, `harness/evidence/*/ai-raw.patch` |
| Boot 4.1.1 with Boot 4 idioms only | Supported line; the AI's Boot 3 habits became visible, recorded rejections | ADR 0001, `AGENTS.md` gotchas |
| H2 file plus Flyway, SQL in the shared subset | Reviewers have no Docker; schema changes stay reviewable files | ADR 0002 |
| Random Base62 codes, one transaction per attempt | Non-enumerable; the database constraint is the arbiter; a collision never poisons a request | ADR 0003 |
| Events table, asynchronous recording, no IP or user agent | Time windows need events; redirects must not wait for analytics; data minimisation for a regulated context | ADR 0005, ADR 0006 |
| Security chain with the rate limiter before authentication | Key guessing is throttled before it reaches the key check | ADR 0007 |

## Artifacts

| Artifact | Path |
|---|---|
| Runnable service | `url-shortener/` (`.\mvnw.cmd -pl url-shortener spring-boot:run`) |
| Architecture, tools, execution approach, control flow, key decisions | `docs/architecture.md`, `docs/decisions/0001` to `0009` |
| Three scenarios with decomposition, execution and validation | `docs/scenarios/greenfield.md`, `brownfield.md`, `ambiguous.md` |
| Requirement records with ambiguities and dated clarification | `harness/requirements/R-01.md`, `R-02.md`, `R-03.md` |
| Task specs with dependencies | `harness/tasks/` |
| Prompts, raw AI output, gate logs | `harness/prompts/`, `harness/evidence/` |
| Ledger with per-file generated, edited, rejected and rationale; gate records; sign-offs | `harness/ledger.yaml` |
| Fresh-context reviews and the engineer's impact analysis | `harness/reviews/` |
| API reference and committed OpenAPI 3.1 export | `docs/reference/api.md`, `docs/reference/openapi.yaml` |
| Testing approach, threat model, risks and limitations | `docs/testing.md`, `docs/threat-model.md`, `docs/risks-and-limitations.md` |
| Traceability report | `docs/reports/traceability.md` |
| Fresh-clone transcript | `docs/fresh-clone-transcript.md` |
| Secure AI usage policy and custom instructions | `harness/policy/secure-ai-usage.md`, `AGENTS.md`, `.claude/settings.json` |

## Validation

| Check | Result | Evidence |
|---|---|---|
| Every gate green on the final commit | see the traceability report | latest `harness/evidence/*/verify-N.log`, CI Actions tab |
| Service tests | count and coverage in the traceability report; no production class untested | JaCoCo `check` in `url-shortener/pom.xml` |
| Harness checks HC-01 to HC-12 | pass | `harness-check` tests inside `mvnw verify` |
| CI on Windows and Ubuntu, gitleaks history scan | green | `.github/workflows/ci.yml`, Actions tab |
| OpenAPI export matches runtime | pass | `OpenApiIT` |
| Perf smoke (opt-in) | figures in `docs/scenarios/greenfield.md`; a smoke threshold, not a capacity claim | `RedirectLatencySmokeTest` |
| Fresh clone on a clean folder | timed walkthrough | `docs/fresh-clone-transcript.md` |

## Risks, trade-offs and failure scenarios

The register with likelihood, impact and response is `docs/risks-and-limitations.md`; the risks that
actually materialised during the build, and what was done about each, are listed there with links
to the ledger. The STRIDE table with accepted risks is `docs/threat-model.md`. The failure
scenarios and degradation behaviour (database unavailable, analytics failure, executor saturation,
collision, kill mid-flight, clock skew) are in `docs/architecture.md`.

## Assumptions

- Reviewers run on a locked-down Windows laptop with JDK 21, no Docker, no admin rights, possibly a
  corporate proxy, and about an hour of attention. Linux and macOS behave the same with `./mvnw`.
- A single node with embedded storage is acceptable for a prototype; the scaling path is documented,
  not built.
- The engineer plays the marketing stakeholder for the ambiguous requirement; the dated
  question-before-answer record is the evidence of clarification.
- Reviewers may evaluate the repository with AI tools; the documentation therefore uses the brief's
  own vocabulary and maps every clause to a file in the README's evidence matrix.

## Limitations

Listed with the trade-off accepted and what production would do in `docs/risks-and-limitations.md`.
The ones a reviewer should know before reading code: single shared API key, hard delete with alias
reclaim, no DNS resolution at create time, in-process rate limiting keyed on the socket address,
PostgreSQL path documented but not executed, no performance claims beyond a local smoke test, and
the harness checking presence and consistency of the process data rather than the quality of a
rationale.

## What the AI got wrong, and what caught it

Every item below is a `debug` entry in `harness/ledger.yaml` with the hypothesis, the finding and
the fix.

| Draft | Caught by | Outcome |
|---|---|---|
| Boot 3 names (`SecurityProperties.DEFAULT_FILTER_ORDER`, `HttpStatus.PAYLOAD_TOO_LARGE`) | Compiler and the Boot 4 gotchas list | Replaced; gotchas extended |
| Null dereference in `ProblemDetails.toMap` | Unit test, then SpotBugs | Fixed before commit |
| A URL validator that let `http://2130706433/` through | The AI's own test table, reviewed and accepted as a security improvement | Numeric shorthand hosts rejected |
| A Checkstyle suppression that only worked on Linux | The Ubuntu CI runner | Portable pattern; Javadoc added |
| A default Spring Security user nobody asked for | The gitleaks job seeing its generated password in a gate log | Auto-configuration excluded |
| An OpenAPI export with the wrong success codes | The engineer comparing the export with the controllers | Explicit status annotations; drift test |
| A harness rule that flagged planned tasks | The first CI run | Rule scoped to done tasks; synthetic test |

## Engineer ownership

Harshil is accountable for every line. The ledger names the tool and model per task; the review
artifacts record what was read, what was found and what was decided; commits carry only a
`Task:` trailer by repository convention (ADR 0009). The statuses generated, edited and rejected
and every rationale are engineer-asserted and labelled as such in the traceability report; the kept
ratios are computed from the raw patches.
