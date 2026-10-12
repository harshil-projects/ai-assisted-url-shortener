## Task

| Field | Value |
|---|---|
| Task id | T-NNN |
| Requirement | R-0N |
| Activities | implement, debug, refactor, test-gen, document, or review-prep |
| Impact | normal, or high with reasons from schema, security, dependency, data-deletion |
| Task spec | `harness/tasks/T-NNN.yaml` |
| Ledger entry | `harness/ledger.yaml`, entry for T-NNN |

## Summary

One short paragraph in the task's words: what changed, why, and which acceptance criteria it satisfies. Name the design section the change implements.

## Definition of done (design section 4.6)

- [ ] Task spec exists with intent, constraints, acceptance criteria, technical context, verification (`harness/tasks/T-NNN.yaml`)
- [ ] Prompt v1 was committed before generation; later versions carry a critique (`harness/prompts/T-NNN/`)
- [ ] Raw AI output snapshot exists for non-document tasks (`harness/evidence/T-NNN/ai-raw.patch`)
- [ ] Ledger entry lists every changed file with status and rationale; rejections have notes (`harness/ledger.yaml`)
- [ ] Gate record appended by `scripts/gate.ps1` with real results, log path, and commit (`harness/evidence/T-NNN/verify-N.log`)
- [ ] Review artifact and sign-off exist if high impact (`harness/reviews/T-NNN.md`, ledger sign-off block)
- [ ] Every commit carries the `Task: T-NNN` trailer and no AI product name
- [ ] Docs touched by the change are updated in this pull request
- [ ] Harness check clean: `.\mvnw.cmd verify` is green locally and in CI on Windows and Ubuntu

## Evidence

| Item | Link or path |
|---|---|
| Gate log | `harness/evidence/T-NNN/verify-N.log` |
| CI run | URL of the green run for the head commit |
| Test output for the acceptance tests | Paste the Surefire summary lines |

## Sign-off (high impact only)

| Field | Value |
|---|---|
| Reasons | schema, security, dependency, or data-deletion |
| Review artifact | `harness/reviews/T-NNN.md` |
| Signed by | |
| Signed at (UTC) | YYYY-MM-DDTHH:MM:SSZ |
| Statement | What was checked and against which design section |
