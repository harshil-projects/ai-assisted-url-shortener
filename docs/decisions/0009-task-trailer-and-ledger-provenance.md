---
status: accepted
date: 2026-10-12
decision-makers: Harshil
---

# Commits carry a Task trailer; AI tool and model provenance lives in the ledger

In the context of recording AI involvement in every change without putting tool names in git history, facing the choice between provenance trailers in commits and a separate provenance record, we decided for a single `Task: T-NNN` trailer on every non-merge, non-bot commit that resolves to a ledger entry naming the tool, the model, the prompts, the raw output, and the per-file decisions, with the accountability statement in the README, and neglected `Assisted-by:` style commit trailers, to achieve commit messages free of AI references and a richer, reviewable provenance record, accepting that provenance needs one lookup instead of being visible in `git log` alone.

## Context and Problem Statement

The repository convention forbids AI references in commit messages. Open-source policies such as the Linux kernel's `Assisted-by:` tag put provenance in trailers, which gives one line per commit and no room for prompt versions, per-file decisions, or rejection notes. The brief wants exactly those details. Where does provenance live, and how does a commit lead to it?

## Decision Drivers

| Driver | Weight |
|---|---|
| Commit messages carry no AI tool or model names | High |
| Every commit resolves to full provenance in one step | High |
| Provenance record holds per-file status, prompts, raw output, debug notes, reviewer, timestamps | High |
| Validated by the build, not by convention alone | High |

## Considered Options

1. `Task:` trailer only; provenance in the ledger entry the trailer resolves to
2. `Assisted-by:` trailers naming the tool and model per commit
3. Both a `Task:` trailer and an `Assisted-by:` trailer
4. No trailer; ledger entries list commit SHAs

## Decision Outcome

Chosen option 1. Every non-merge, non-bot commit after the `baseline` tag ends with `Task: T-NNN`, in Conventional Commits form, for example `feat(redirect): return 410 for expired links`. The trailer resolves as follows.

| Step | Where | What it yields |
|---|---|---|
| 1 | `git log --format='%h %(trailers:key=Task,valueonly)'` | The task id for each commit |
| 2 | `harness/ledger.yaml`, entry with `task: T-NNN` | `tool` (Claude Code), `model` as reported by the tool, `prompts`, `raw_output`, `files` with `generated`, `edited`, or `rejected` status, `rationale`, `rejected_notes`, `debug`, `reviewer`, `started_at`, `finished_at` |
| 3 | Same ledger, `gates` and `signoffs` blocks for the task | Gate results with log path and commit; sign-off with review artifact when high impact |
| 4 | `harness/tasks/T-NNN.yaml` | Intent, constraints, acceptance criteria, verification |

The README carries the accountability statement: the engineer is accountable for every line shipped; AI drafted inside task boundaries; every diff was reviewed; the ledger is the record of what was kept, edited, and rejected. The generated traceability report performs the mapping for every commit, so a reviewer never does it by hand.

### Consequences

| Kind | Consequence |
|---|---|
| Good | Git history reads as a normal engineering history |
| Good | Provenance is richer than any trailer could hold and is validated by HC-06 and HC-07 |
| Good | One command maps commits to tasks; the report does it for all |
| Bad | `git log` alone does not show that AI was involved; the README states where to look |
| Bad | A commit without the trailer fails the build, which adds friction to quick fixes |

### Confirmation

| Check | Where defined |
|---|---|
| HC-07 fails a non-merge, non-bot commit after `baseline` that lacks a `Task:` trailer or references a missing task | Section 5.8, process traceability gate in section 4.1, NFR-11 |
| HC-06 fails a ledger entry whose task, prompt, patch, log, or file does not exist | Section 5.8 |
| `scripts/hooks/commit-msg` rejects the commit locally when `core.hooksPath` is set | Section 4.4 |

## Pros and Cons of the Options

| Option | Pros | Cons |
|---|---|---|
| `Task:` trailer, ledger provenance | Clean history; rich record; build-validated | One lookup for provenance |
| `Assisted-by:` trailers | Visible in `git log`; matches some open-source policies | Violates the repository convention; one line cannot hold per-file decisions or prompts |
| Both trailers | Visible and rich | Violates the convention; duplicates data that can drift |
| No trailer, SHAs in the ledger | No commit rules | SHAs change on squash and rebase; the ledger must be edited after every merge |

## More Information

Conventional Commits 1.0.0; git interpret-trailers; Linux kernel AI coding assistants policy; Fedora AI-assisted contributions policy; Chromium AI coding policy; Linux Foundation generative AI guidance; Adelie Linux GenAI policy; US Copyright Office Part 2; Keep a Changelog 1.1.0.
