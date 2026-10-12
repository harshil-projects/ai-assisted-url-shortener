# Ambiguous scenario: "Marketing wants to know where clicks come from" (R-03)

How a one-line request became a scoped, privacy-safe feature: dated questions before dated answers,
candidates weighed, scope removed, then implementation and validation. Every claim links to the
file that proves it.

## Requirement

> "Marketing wants to know where clicks come from."

Record: `harness/requirements/R-03.md`.

## Requirement understanding: identify the ambiguity

The sentence does not say which dimension (referring site, campaign, geography, device, time),
what surface (API, export, dashboard), what window, or what personal data may be touched. The
engineer, playing the stakeholder as the design assumes (no marketing team exists), recorded three
questions with timestamps on 2026-10-12 at 06:45 (+05:30) and the answers at 08:05, in the
"Stakeholder clarification log" of `R-03.md`.

| Question | Answer (summary) | Scope effect |
|---|---|---|
| Which dimension matters most | Referring site first, campaign (`utm_*`) second | Geography and device removed |
| May IP or derived location be stored | No; ADR 0006 stands; user agent also excluded | Geography, device and full referrer URL removed |
| Which surface and window | Stats API, two top-ten breakdowns over all clicks of the link | No export, no dashboard, no window parameter in this slice |

## Decision (T-201)

Recorded in `R-03.md` under "Decision": referring host and campaign parameters stored with each
click; top-ten `referrers` and `campaigns` in the stats response; migration V3 with four nullable
columns, one statement each; redirect semantics unchanged, query parameters recorded but not
forwarded; a candidates table with the reason each was chosen, removed or deferred. The decision
became EARS criteria FR-15a to FR-15e in `harness/tasks/T-202.yaml`, which is high impact (schema).

## Task decomposition

| Task | Title | Depends on | Impact | Activities | State |
|---|---|---|---|---|---|
| T-201 | Clarify, weigh candidates, decide the slice | T-010 | normal | document | done |
| T-202 | Record attribution and expose it in stats | T-201 | high (schema) | implement, test-gen | see ledger |
| T-203 | Edge-case tests, reference and changelog, this document | T-202 | normal | test-gen, document | see ledger |

## AI-assisted execution

| Step | Evidence |
|---|---|
| Prompt committed before use | `harness/prompts/T-202/v1.md`, commit `8599728` |
| Raw AI output | `harness/evidence/T-202/ai-raw.patch` (21 files, taken from the drafting worktree before any engineer edit) |
| Engineer review | Ledger entries T-202 and T-203: per-file generated or edited with the rationale; edited files are those merged with the parallel expiry change plus constructor arguments adjusted after `Link` gained `expiresAt` |
| Debug entries | Checkstyle `OverloadMethodsDeclarationOrder` on the repository defaults; `AvoidEscapedUnicodeCharacters` in the edge table |
| Gate logs | `harness/evidence/T-202/`, `T-203/` |
| Review and sign-off (schema) | `harness/reviews/T-202.md`, `signoffs` in the ledger |
| Commits | `git log --grep "Task: T-202"`, `T-203` |

What the AI did inside the task boundary: the V3 migration, `ClickAttribution` as a pure function
with a 41-case edge table, the event and entity changes, the two grouped repository queries, the
response members, `AttributionIT` over HTTP, the reference section and changelog entries. What the
engineer did: asked and answered the clarification questions with timestamps, weighed the candidates
and removed geography, device and IP-derived data from scope, wrote the task criteria, merged the
result with the expiry change, reviewed every file and signed off the schema change.

## Validation

| Criterion | Proving test |
|---|---|
| FR-15a referrer host only, bounded, null when absent | `ClickAttributionTest`, `AttributionIT.redirectWithoutRefererRecordsNullHost` |
| FR-15b `utm_*` cleaned and truncated | `ClickAttributionTest`, `AttributionIT` |
| FR-15c no IP, user agent, full URL, geography, device | `MigrationTest` seven-column assertion on `click_events` |
| FR-15d top-ten breakdowns in stats | `ClickEventRepositoryTest`, `AttributionIT.redirectWithRefererAndUtmShowsUpInStats` |
| FR-15e query parameters not forwarded | `AttributionIT.queryParametersAreNotForwarded` |
| NFR-10 V3 validates at startup | every context start under `ddl-auto=validate` |
| Coverage | 97.8 percent lines, 94.1 percent branches in the drafting session's final run; the merged tree is reported in the gate logs |
| Contract | `OpenApiIT.committedSpecMatchesRuntime`; `docs/reference/api.md` section "Click attribution" |

## Still open

Nothing in scope. Deferred by the decision: a time-window parameter, exports or dashboards, query
parameter forwarding (needs an open-redirect analysis), geography and device (need data ADR 0006
forbids).
