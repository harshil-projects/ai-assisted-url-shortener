# Playbook: refactor

| Field | Value |
|---|---|
| Activity | refactor |
| Input | A task spec from `harness/tasks/{{task.id}}.yaml` naming the smell and the measurable improvement |
| Output | A filled prompt committed as `harness/prompts/{{task.id}}/vN.md` before it is run |
| Guardrails | Behaviour-preservation contract, tests must not change, named smell, size limit per step |

Fill every placeholder. The smell must be named in the intent, with the metric that shows it.

```text
Task {{task.id}}: refactor. Read AGENTS.md before writing anything.
Module url-shortener, package dev.harshil.shortener. Spring Boot 4.1, Java 21.

Intent (the named smell, where it lives, and the measurable improvement expected):
{{task.intent}}

Behaviour-preservation contract:
- The HTTP contract, log output, metric names, and persisted data are unchanged.
- Every existing test passes unchanged.
- Test files are read-only in this task: no edits, renames, deletions, or additions. If a test must
  change for the refactor to proceed, stop, say which test and why, and change nothing.
- No new dependencies. No changes to any pom.

Constraints:
{{task.constraints}}

Acceptance criteria:
{{task.acceptance_criteria}}

Technical context (files in scope; anything else is out of scope):
{{task.technical_context}}

Verification (suites that must stay green, and the metric command):
{{task.verification}}

Step size:
- One refactoring move per step: extract method, extract class, move, rename, inline, replace conditional.
- Each step under 100 changed lines and leaving the build green on its own.
- Present the steps in order, each with its diff and the test command to run after it.

Report:
- The metric before and after (method length, file length, cyclomatic complexity, or duplicate count),
  with the command used to measure it.
- Any behaviour you were unsure was preserved, and why.
- Anything you could not verify.
```
