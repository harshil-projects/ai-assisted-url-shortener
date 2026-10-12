# R-0N: <requirement title, the stakeholder's words in quotes>

Normalized by the engineer before any task spec is written. AI may list options to consider or check this record for omissions; it does not decide. Dates are UTC.

| Field | Value |
|---|---|
| Requirement id | R-0N |
| Source | The assignment brief, scenario <greenfield, brownfield, or ambiguous> |
| Status | draft, normalized, or delivered |
| Written at | YYYY-MM-DDTHH:MM:SSZ |
| Delivered by tasks | T-NNN, T-NNN |
| Design sections | docs/design/2026-10-12-system-design.md#<section> |

## Intent

Two or three sentences. What the stakeholder needs and why, in terms that can be tested.

## Ambiguities

| # | Ambiguous phrase | Possible readings | Reading adopted | Why | Decided at |
|---|---|---|---|---|---|
| 1 | | | | | YYYY-MM-DDTHH:MM:SSZ |

## Stakeholder Q&A

Questions are written and dated before any answer is written. The engineer plays the stakeholder for this project, so the dates are the evidence that the question came first. At least one answer should remove scope.

| # | Question | Question written at | Answer | Answer written at | Effect on scope |
|---|---|---|---|---|---|
| Q1 | | YYYY-MM-DDTHH:MM:SSZ | | YYYY-MM-DDTHH:MM:SSZ | adds, removes, or none |

## Assumptions

| Assumption | Rationale | Risk if wrong |
|---|---|---|
| | | |

## Acceptance criteria

EARS form with RFC 2119 keywords. Each row names the task that delivers it and the test that proves it.

| ID | Criterion | Priority | Task | Acceptance test |
|---|---|---|---|---|
| FR-NN | WHEN <trigger> THE SYSTEM SHALL <response> | Must, Should, or Could | T-NNN | `ClassName.methodName` |
| FR-NN | IF <unwanted condition> THEN THE SYSTEM SHALL <response> | | | |
| FR-NN | WHILE <state> THE SYSTEM SHALL <response> | | | |
| NFR-NN | THE SYSTEM SHALL <quality>, measured by <measure> | | | |

## Out of scope

| Excluded | Why | Where it is recorded |
|---|---|---|
| | | docs/risks-and-limitations.md |

## Decision

| Field | Value |
|---|---|
| Decided at | YYYY-MM-DDTHH:MM:SSZ |
| Decision | The normalized requirement in one paragraph |
| Candidate directions rejected | Each with its cost or privacy reason |
| Decision record | docs/decisions/000N-<slug>.md, when the decision is architectural |
