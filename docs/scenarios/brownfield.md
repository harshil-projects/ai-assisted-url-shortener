# Brownfield scenario: link expiry (R-02)

How an enhancement, a regression suite, a refactor and a defect fix were delivered on the existing
codebase with AI assistance under engineer control. Every claim links to the file that proves it.

## Requirement

> "Links must be able to expire. Expired links must stop redirecting and must show as expired.
> Tighten the test suite and docs while there."

Record: `harness/requirements/R-02.md`.

## Requirement understanding

| Ambiguity | Reading adopted | Where decided |
|---|---|---|
| "Stop redirecting": 404 or 410 | 410 Gone with `Cache-Control: no-store`; the resource existed and is intentionally gone | design section 3.3, `R-02.md` |
| "Show as expired": where | `expired: true` plus the `expiresAt` value in link metadata and in statistics | design section 3.3 |
| Keep or purge expired rows | Keep; history and the 410 survive; purge is a documented follow-up | ADR 0005, `docs/risks-and-limitations.md` |
| Expiry input format | RFC 3339 with an offset; a past value is a field error, a zoneless string fails parsing and is the generic unreadable-body 400 | FR-13, `docs/reference/api.md` |
| Boundary | Expired when `expiresAt <= now`, fixed by `ExpiryEdgeCasesTest` | T-103 |
| Clock | One injected UTC `Clock` for create, redirect and response building | design section 3.13 |

## Codebase reasoning (brief requirement 3)

Before any code changed, the engineer wrote `harness/reviews/T-101-impact-analysis.md`: the data
flow before and after, every impacted module with its risk and mitigation, the impacted APIs and
data, the tests to add, the risks, the smells for the refactor, and the predicted list of main-source
files. Harness-check rule HC-12 compares that prediction with the files the T-102 commit actually
touched and fails the build on an unpredicted main-source file.

Outcome: every main-source file changed by T-102 was in the predicted list. Two predicted files
(`StatsController`, `LinkRepository`) turned out not to need a change, which is the acceptable
direction of error.

## Task decomposition

| Task | Title | Depends on | Impact | Activities | State |
|---|---|---|---|---|---|
| T-101 | Impact analysis with predicted file list | T-010 | normal | document | done |
| T-102 | Migration V2 and expiry on create, redirect, metadata, stats | T-101 | high (schema) | implement | see ledger |
| T-103 | Regression suite and documentation | T-102 | normal | test-gen, document | see ledger |
| T-104 | Refactor the top smell, tests unchanged | T-103 | normal | refactor | see ledger |
| T-105 | Fix one real defect from the fresh-context review with a failing-first test | T-104 | normal | debug | pending the T-010 review |

Specs: `harness/tasks/T-101.yaml` to `T-105.yaml`.

## AI-assisted execution

| Step | Evidence |
|---|---|
| Prompt committed before use | `harness/prompts/T-102/v1.md` (batch for T-102, T-103 tests, T-104), commit `f72b282` |
| Raw AI output | `harness/evidence/T-102/ai-raw.patch` (38 files, taken from the drafting worktree before any engineer edit) |
| Engineer review | Ledger entries T-102, T-103, T-104 in `harness/ledger.yaml`: per-file generated or edited with the rationale; the edited files are the three merged with the parallel attribution slice |
| Debug entries | Zoneless timestamps cannot reach a field-level error under Jackson 3 (accepted, documented); a transient context failure traced to the shared test database (fixed in T-009) |
| Gate logs | `harness/evidence/T-102/`, `T-103/`, `T-104/` |
| Review and sign-off (schema) | `harness/reviews/T-102.md`, `signoffs` in the ledger |
| Commits | `git log --grep "Task: T-102"`, `T-103`, `T-104` |

What the AI did inside the task boundary: wrote the V2 migration, the entity, DTO, service,
controller and handler changes, 31 new or extended tests including `ExpiryIT` with a mutable clock
and the boundary table, the `ProblemResponses` extraction, and the reference and changelog text. What
the engineer did: wrote the impact analysis first, set the constraints, merged the result with the
attribution slice (three files), reviewed every file, signed off the schema change.

## Refactor (T-104)

Smell 1 from the impact analysis: `ApiExceptionHandler` mixed domain mapping with framework body
re-shaping and was the largest production class. Measured: 324 lines after the 410 mapping, 279 after
the refactor; six methods moved to the 93-line `ProblemResponses` helper; no test file changed; the
OpenAPI export is byte-identical; SpotBugs stayed at zero.

## Validation

| Check | Result |
|---|---|
| Tests | 327 before the brownfield work; 358 after T-102 and T-103 in the drafting session; the merged main tree is reported in the gate logs |
| Coverage (bundle) | lines 97.62 percent and branches 93.75 percent before T-102; 97.74 and 94.12 after T-104 |
| HC-12 | predicted versus actual files: no violation |
| Behaviour | `ExpiryIT.expiredLinkIs410AndStatsRemain`, `ExpiryIT.rejectsPastAndZoneless`, `MigrationTest.v2AddsNullableExpiresAt`, `OpenApiIT.committedSpecMatchesRuntime` |
| CI | Actions tab, workflow CI, on the commits above |

## Still open

T-105 waits for the T-010 fresh-context review to name a real defect.
