# harness/

The engineering process as data. Everything here is YAML or Markdown, written by the engineer or by two PowerShell scripts, and validated by the `harness-check` module inside `.\mvnw.cmd verify`. A gap in the data fails the build.

## Layout

| Path | Holds | Written by | Checked by |
|---|---|---|---|
| `policy/secure-ai-usage.md` | Controls SAI-01 to SAI-12, each labelled enforced, attested, or stated | Engineer | Review |
| `playbooks/*.md` | Prompt templates per activity: implement, debug, refactor, test-gen, document, review-prep | Engineer | Used by every prompt |
| `templates/*` | Shapes for task specs, ledger records, review artifacts, requirement records | Engineer | Reference |
| `requirements/R-0N.md` | Normalized requirements: intent, ambiguities, assumptions, EARS criteria, dated Q&A | Engineer | Referenced by tasks |
| `tasks/T-NNN.yaml` | Task specs with dependencies | Engineer | HC-01, HC-02, HC-12 |
| `prompts/T-NNN/vN.md` | Filled prompts. v1 is committed before use; each later version carries a two-line critique | Engineer | HC-08 |
| `evidence/T-NNN/ai-raw.patch` | The raw AI diff, saved before any engineer edit | `scripts/snap.ps1` | HC-06, kept-ratio computation |
| `evidence/T-NNN/verify-N.log` | Gate output per run, red runs included | `scripts/gate.ps1` | HC-06 |
| `ledger.yaml` | Entries, gates, sign-offs | Engineer and `scripts/gate.ps1` | HC-03 to HC-07, HC-11 |
| `reviews/T-NNN.md` | Fresh-context review artifacts for high-impact tasks | Engineer | HC-05 |

## Lifecycle, ten steps

1. Normalize: the engineer writes intent, ambiguities, assumptions, and acceptance criteria into `requirements/R-0N.md`. AI may list options to consider.
2. Decompose: the engineer writes task specs with dependencies into `tasks/T-NNN.yaml`.
3. Prompt: playbook plus task spec becomes `prompts/T-NNN/v1.md`, committed before it is run.
4. Generate: the AI drafts into the working tree. Nothing is committed yet.
5. Snapshot: `scripts/snap.ps1 T-NNN` saves the raw diff to `evidence/T-NNN/ai-raw.patch` and prints the changed file list.
6. Review: the engineer marks every file generated, edited, or rejected with a rationale in `ledger.yaml`. Rework refines the prompt to vN with a recorded critique and returns to step 4.
7. Gate: `scripts/gate.ps1 T-NNN` formats, runs `mvnw verify`, saves `evidence/T-NNN/verify-N.log`, and appends a gate record. A red run is recorded, debugged with a hypothesis, and rerun.
8. Sign-off: a high-impact task gets `reviews/T-NNN.md` from a fresh context plus a sign-off record pointing to it.
9. Commit: a Conventional Commit with a `Task: T-NNN` trailer, checked by `scripts/hooks/commit-msg`.
10. Harness check: `harness-check` runs inside the build. A violation names the rule and the file. `scripts/report.ps1` regenerates `docs/reports/traceability.md` from the data.

## Reading the evidence in ten minutes

| Minute | Open | Look for |
|---|---|---|
| 0 to 1 | `docs/reports/traceability.md` | The per-task table: activities, prompt versions, generated, edited, and rejected counts, computed kept ratio, gate results, sign-offs |
| 1 to 3 | `ledger.yaml`, pick one high-impact task | The rationale and `rejected_notes` in the engineer's words; the gate record's `log` path; the sign-off's `review` path |
| 3 to 5 | `evidence/T-NNN/ai-raw.patch` beside `git show <commit>` | What the AI wrote against what shipped |
| 5 to 7 | `prompts/T-NNN/v1.md` and any later version | The spec inside the prompt; the critique that drove each new version |
| 7 to 9 | `reviews/T-NNN.md` | Gaps found against the spec, and the decision |
| 9 to 10 | `policy/secure-ai-usage.md` | Which controls are enforced and which are attested |

Two commands cover the rest. `.\mvnw.cmd verify` runs every check. `git log --format="%h %s%n%(trailers:key=Task,valueonly)"` maps commits to tasks.
