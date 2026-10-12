# Playbook: debug

| Field | Value |
|---|---|
| Activity | debug |
| Input | A task spec from `harness/tasks/{{task.id}}.yaml` plus the exact symptom and the reproduction command |
| Output | A filled prompt committed as `harness/prompts/{{task.id}}/vN.md`; a `debug:` list in the ledger entry |
| Guardrails | Hypothesis before any change, regression test, no edits to existing tests |

Fill every placeholder. Paste the symptom verbatim from the log or the test output. Never paste secrets.

```text
Task {{task.id}}: debug. Read AGENTS.md before writing anything.
Module url-shortener, package dev.harshil.shortener. Spring Boot 4.1, Java 21.

Intent:
{{task.intent}}

Symptom and reproduction, verbatim (failing assertion, status code, stack trace head, and the one command
that shows it):
{{task.technical_context}}

Constraints:
{{task.constraints}}

Acceptance criteria (what "fixed" means, in EARS form):
{{task.acceptance_criteria}}

Verification (the regression test and the suites that must stay green):
{{task.verification}}

Rules, in this order:
1. State a hypothesis before changing any code: what is wrong, why, and which observation would confirm
   or refute it. Name the file and line you expect to be at fault.
2. Confirm or refute the hypothesis by reading code or adding a temporary log statement, not by guessing.
   If refuted, record the finding and state the next hypothesis. Never edit production code on an
   unconfirmed hypothesis.
3. Produce a regression test named after the defect that fails before the fix and passes after it.
   Show both runs.
4. Make the smallest fix that makes the regression test pass. No refactoring in the same change.
5. Do not modify, delete, skip, or weaken any existing test. Report a test that blocks you and stop.
6. Do not widen a catch block, swallow an exception, or add a retry to hide the symptom.
7. Remove any temporary logging before presenting the diff.
8. State anything you could not verify.

Output, in this exact shape so it can be pasted into the ledger:
debug:
  - { hypothesis: "...", finding: "confirmed or refuted, with the evidence", fix: "one sentence or none" }
Then: the regression test, the fix diff, the two test runs, and the list of things not verified.
```
