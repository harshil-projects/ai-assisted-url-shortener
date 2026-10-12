# Review: T-NNN <task title>

Written in a fresh context from the task spec, the diff, `AGENTS.md`, and the design sections the spec references. The author of this review has not seen the prompts or the conversation that produced the change. Gaps are reported against the task spec, not against style.

| Field | Value |
|---|---|
| Task | T-NNN |
| Requirement | R-0N |
| Impact reasons | schema, security, dependency, or data-deletion |
| Diff reviewed | `git diff <base>..<head>` or the commit id |
| Reviewer context | Fresh session; inputs listed above and nothing else |
| Reviewed at (UTC) | YYYY-MM-DDTHH:MM:SSZ |

## Diff summary

| File | What changed, one sentence | Ledger status |
|---|---|---|
| `url-shortener/src/main/java/dev/harshil/shortener/...` | | generated, edited, or rejected |

## Definition-of-done checklist (design section 4.6)

| Check | Evidence found | Result |
|---|---|---|
| Task spec exists with intent, constraints, acceptance criteria, technical context, verification | `harness/tasks/T-NNN.yaml` | |
| Prompt v1 committed before generation; later versions carry a critique | `harness/prompts/T-NNN/` | |
| Raw AI output snapshot exists for non-document tasks | `harness/evidence/T-NNN/ai-raw.patch` | |
| Ledger entry lists every changed file with status and rationale; rejections have notes | `harness/ledger.yaml` | |
| Gate record appended by the gate script with real results, log path, and commit | Ledger gate block, `harness/evidence/T-NNN/verify-N.log` | |
| Review artifact and sign-off exist if high impact | This file, ledger sign-off block | |
| Commit carries the `Task:` trailer | `git log` | |
| Docs touched by the change updated in the same task | Diff | |
| Harness check clean | `mvnw verify` | |

## Gaps against the task spec

Every acceptance criterion and constraint from the spec appears here. A criterion without a proving test is a gap.

| # | Criterion or constraint | Satisfied by | Proving test | Gap | Severity |
|---|---|---|---|---|---|
| 1 | | | | none, or the gap in one sentence | blocking, should-fix, or note |

## Security rows touched (design section 3.7)

| Rule | Touched by this change | Holds | Evidence |
|---|---|---|---|
| | yes or no | yes, no, or unsure | |

## Test diff audit (SAI-06)

| File and line | Deleted, skipped, weakened, or renamed | Justified by the spec |
|---|---|---|
| none found | | |

## Defect candidates

| # | Observation | Reproduction note | Confidence |
|---|---|---|---|
| | | | likely or unsure |

## Decision

| Field | Value |
|---|---|
| Decision | approve, rework, or reject |
| Rationale | One or two sentences in the engineer's words |
| Follow-up tasks | Task ids created from the gaps above, or none |
| Not verified | What this review could not check and why |
