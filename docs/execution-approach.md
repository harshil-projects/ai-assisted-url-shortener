# Execution approach

How the work was done, explained for a reviewer. AI drafts inside task boundaries; the engineer decides at every step. The ten-step lifecycle and its control-flow diagram are in [architecture.md](architecture.md#execution-approach-and-control-flow). This document explains the harness that makes that lifecycle checkable.

| Who | Does |
|---|---|
| Engineer | Writes requirements and task specs, writes prompts, reviews every diff, records decisions, runs gates, signs off, commits |
| AI (Claude Code) | Drafts code, tests, docs, option lists, and omission checks on engineer-written analyses |
| Build | Runs every gate and the harness-check rules; regenerates the traceability report from data |

## Principles

| Principle | Meaning in practice |
|---|---|
| AI output is untrusted input | Nothing generated lands without engineer review and green gates; the raw output is kept so the claim is checkable |
| Intent is written before code | No prompt without a task spec; no task spec without a normalized requirement; prompts are committed before they are used |
| Rejections are evidence | Rejected and edited entries with rationale are the strongest signal of judgment and are summarised in the report |
| Enforced where possible, attested where not | Deny rules, build gates, and harness-check tests are enforced; account settings and reviewing discipline are attested and labelled so |
| Minimal, auditable, portable | YAML and Markdown, one small JUnit module, two PowerShell scripts, no new runtime services |

## Artifacts

| Artifact | Path | Produced by | Checked by |
|---|---|---|---|
| Secure AI usage policy | `harness/policy/secure-ai-usage.md` | Engineer | Each control labelled enforced or attested |
| Playbooks | `harness/playbooks/{implement,debug,refactor,test-gen,document,review-prep}.md` | Engineer | Used in prompts |
| Requirement records | `harness/requirements/R-01.md`, `R-02.md`, `R-03.md` with dated stakeholder Q and A for R-03 | Engineer | Referenced by tasks |
| Task specs | `harness/tasks/T-NNN.yaml` | Engineer | harness-check |
| Prompt iterations | `harness/prompts/T-NNN/vN.md` | Engineer | harness-check presence |
| Raw AI output | `harness/evidence/T-NNN/ai-raw.patch` | `scripts/snap.ps1` | harness-check presence and kept-ratio computation |
| Gate logs | `harness/evidence/T-NNN/verify-N.log` | `scripts/gate.ps1` | harness-check presence |
| Ledger | `harness/ledger.yaml` | Engineer plus scripts | harness-check |
| Review artifacts | `harness/reviews/T-NNN.md` | Fresh-context review plus the engineer's checklist | harness-check for high-impact tasks |
| Custom instructions | `AGENTS.md`, `CLAUDE.md` importing it, `.github/copilot-instructions.md` | Engineer | Hidden-Unicode scan |
| Deterministic guardrails | `.claude/settings.json` with deny rules | Engineer | Committed, reviewed |
| PR template | `.github/PULL_REQUEST_TEMPLATE.md` | Engineer | Review |
| Traceability report | `harness-check/target/traceability.md`, snapshot at `docs/reports/traceability.md` via `scripts/report.ps1` | harness-check | Deterministic, no timestamps |

## Task spec format

One file per task. Validation is implemented in the harness-check Java tests; no JSON Schema files are kept.

```yaml
id: T-004
title: Create link endpoint
requirement: R-01
activities: [implement]            # one or more of implement, debug, refactor, test-gen, document, review-prep
impact: normal                     # high | normal
impact_reasons: []                 # when high: schema | security | dependency | data-deletion
status: done                       # todo | in-progress | done | dropped
depends_on: [T-002, T-003]
intent: >
  Expose POST /api/v1/links so clients can create short links from validated URLs.
constraints:
  - All errors are RFC 9457 Problem Details through the shared writer
  - No new dependencies
acceptance_criteria:
  - WHEN a valid URL is posted THE SYSTEM SHALL respond 201 with Location
  - IF a custom alias is taken THEN THE SYSTEM SHALL respond 409
technical_context:
  files: [api/LinkController.java, service/LinkService.java, service/LinkAllocator.java]
  interfaces: ["LinkService.create(CreateLinkRequest) -> Link"]
  patterns: [one transaction per allocation attempt, records for DTOs]
  references: [docs/design/2026-10-12-system-design.md#3.6]
verification: [LinkControllerTest, LinkAllocatorTest, LinkFlowIT]
```

## Ledger format

One file for the project with three blocks. Angle brackets mark values filled per task by the scripts or the engineer. Statuses mirror the assignment's words: `generated` means kept as generated, `edited` means changed by the engineer, `rejected` means discarded. The kept ratio per file is computed by harness-check from the raw patch against the committed diff; the status and rationale are engineer-asserted and labelled as such in the report.

```yaml
entries:
  - task: T-004
    tool: Claude Code
    model: <model id as reported by the tool>
    prompts: [harness/prompts/T-004/v1.md, harness/prompts/T-004/v2.md]
    raw_output: harness/evidence/T-004/ai-raw.patch
    files:
      - { path: url-shortener/src/main/java/dev/harshil/shortener/api/LinkController.java, status: edited }
      - { path: url-shortener/src/main/java/dev/harshil/shortener/service/LinkAllocator.java, status: generated }
      - { path: url-shortener/src/test/java/dev/harshil/shortener/api/LinkControllerTest.java, status: rejected }
    rationale: >
      Engineer-asserted. Controller draft used @RequestParam for the body; rewrote to @Valid @RequestBody.
      Allocator kept as generated. Test draft asserted on exception messages; rejected and rewritten against status and problem type.
    rejected_notes:
      - Test asserted on exception message text, brittle; replaced with status and type assertions
    debug:
      - { hypothesis: "400 instead of 201 because Jackson 3 FAIL_ON_NULL_FOR_PRIMITIVES on an int field", finding: "confirmed via MockMvcTester output", fix: "boxed Integer with @NotNull" }
    reviewer: Harshil
    started_at: 2026-10-13T13:40:00Z
    finished_at: 2026-10-13T15:10:00Z
gates:
  - task: T-004
    commit: <sha>
    run_at: 2026-10-13T15:02:11Z
    overall: pass
    results: { format: pass, style: pass, sast: pass, tests: pass, coverage: pass, traceability: pass, secrets: pass, perf: "skipped: opt-in profile not run locally" }
    log: harness/evidence/T-004/verify-2.log
    ci_run: <URL of the CI run for this commit>
signoffs:
  - task: T-002
    reasons: [schema]
    review: harness/reviews/T-002.md
    signed_by: Harshil
    signed_at: 2026-10-13T11:30:00Z
    statement: Migration V1 reviewed against section 3.5; constraint names and time zone types confirmed.
```

## Playbooks

A prompt is a playbook plus a task spec. Each playbook adds the rules for its activity.

| Activity | Playbook adds | Must appear in a scenario |
|---|---|---|
| implement | Files and interfaces to touch, patterns to follow, the relevant security control rows, "write the failing test first", "do not add dependencies", "state anything you could not verify" | Greenfield |
| debug | Symptom, reproduction command, "state a hypothesis before changing code", "produce a regression test", "do not modify existing tests to pass" | Red gates; T-105 |
| refactor | Behaviour-preservation contract, "tests must not change", the named smell, size limit per step | Brownfield T-104 |
| test-gen | Behaviour list, naming rule, "no logic in tests", edge-case table, coverage target | Greenfield and brownfield |
| document | Audience, Diataxis type, "derive from code and tests, do not invent features" | All |
| review-prep | Diff summary, the definition-of-done checklist, "report gaps against the task spec, not style", run in a fresh context | High-impact tasks and final |

## High-impact classification

The changes that would go to a senior engineer on a real team. A high-impact task needs a review artifact in `harness/reviews/` and a sign-off record that points to it before it is done.

| Reason code | Triggers sign-off when the change touches |
|---|---|
| `schema` | Any Flyway migration or entity mapping |
| `security` | `SecurityConfig`, the API key filter, `UrlValidator`, headers, reserved words, rate limiter |
| `dependency` | Any new or changed coordinate in a pom |
| `data-deletion` | Delete endpoint behaviour |

Public API and CI changes are reviewed through the normal lifecycle and the OpenAPI drift gate rather than sign-off, so sign-off stays meaningful.

## Secure AI usage policy

The full policy with each control's mechanism and reference is at `harness/policy/secure-ai-usage.md`. Each control states whether a mechanism enforces it or the engineer attests it.

| ID | Control | Enforced or attested |
|---|---|---|
| SAI-01 | Only public or synthetic data enters prompts, fixtures, or logs; the assignment brief and transcripts are never committed | Attested; gitignore enforced |
| SAI-02 | AI account has training off; feedback and transcript uploads disabled | Account setting attested; env enforced |
| SAI-03 | AI cannot read secrets or run exfiltration commands (deny rules on `.env*`, `~/.ssh`, `~/.aws`, `curl`, `wget`, `ssh`, `scp`, `nc`; bypass mode disabled) | Enforced |
| SAI-04 | AI output is untrusted until snapshotted, gated, and reviewed | Presence enforced by harness-check; review quality attested |
| SAI-05 | Every AI-suggested dependency is verified on Maven Central and the URL recorded in the ledger | Attested, record checked |
| SAI-06 | Test diffs reviewed for deleted, skipped, or weakened tests; tests and implementation not changed in one prompt unless the spec says so | Coverage enforced; review attested |
| SAI-07 | Prompts state security requirements explicitly | Enforced by the playbook template |
| SAI-08 | AI configuration files are treated as executable; hidden-Unicode scan | Enforced (HC-09) |
| SAI-09 | No untrusted web, issue, or MCP content pulled into a build session without approval | Attested |
| SAI-10 | High-impact changes require a review artifact and sign-off | Enforced (HC-05) |
| SAI-11 | Native Windows runs the assistant unsandboxed | Stated |
| SAI-12 | Engineer accountability statement in README and final summary | Stated |

## Harness-check rules

Implemented as JUnit tests in `harness-check`; each failure message names the rule and the offending file.

| Rule | Violation when |
|---|---|
| HC-01 | A task file is missing a required field or has an unknown enum value |
| HC-02 | `depends_on` references a missing task or forms a cycle |
| HC-03 | A task with status `done` has no ledger entry |
| HC-04 | A task with status `done` has no gate record with `overall: pass`, or any required key is missing, or a `skipped` value lacks a reason |
| HC-05 | A task with `impact: high` and status `done` has no sign-off whose `review` file exists |
| HC-06 | A ledger entry references a missing task, prompt file, raw-output patch (for tasks with activities other than `document`), log file, or changed file |
| HC-07 | A non-merge, non-bot commit after the `baseline` tag lacks a `Task:` trailer or references a missing task; skipped with a notice when `.git` or the tag is absent |
| HC-08 | A task with activities other than `document` and status `done` has no `v1.md` prompt |
| HC-09 | A file in the AI-configuration set contains zero-width or bidirectional Unicode |
| HC-10 | A tracked file matches a secret pattern |
| HC-11 | A file marked `rejected` has no corresponding note in `rejected_notes` |
| HC-12 | For a task whose spec names an impact analysis, a changed main-source file is not in the analysis's predicted list; tests and docs are exempt |

The report lists per task: spec, activities, impact, prompt versions, generated, edited, and rejected counts, computed kept ratio, gate results, sign-off, commits. A summary table gives acceptance, edit, and rejection ratios across the project, labelled with which numbers are computed and which are asserted. At v0.1.0 the report shows 255 files kept as generated, 22 edited and 0 rejected (acceptance 0.92, edit 0.08, both from engineer-asserted statuses) and a computed mean kept ratio of 0.81.

## Scripts

| Script | Does |
|---|---|
| `scripts/snap.ps1 T-NNN` | Stages everything, writes `harness/evidence/T-NNN/ai-raw.patch`, unstages, appends a file list stub to the ledger entry |
| `scripts/gate.ps1 T-NNN [-Perf]` | Runs `mvnw spotless:apply` then `mvnw -ntp verify`, tees the log to `harness/evidence/T-NNN/verify-N.log`, appends a gate record with real timestamp, overall result, the failing plugin mapped to a gate key on red, and the current commit |
| `scripts/report.ps1` | Runs the harness-check tests and copies `target/traceability.md` to `docs/reports/` |
| `scripts/run.ps1` | `mvnw -pl url-shortener spring-boot:run` with an optional port |
| `scripts/demo.ps1` | Health, create, redirect, stats, 401, 404, 429 through `Invoke-RestMethod`, printing status codes and headers |

Custom instructions: `AGENTS.md` is the single source under 150 lines with setup, run, and test commands, the lifecycle in ten lines, conventions including the Boot 4 and Spring Security 7 gotchas, the security rules in one list, "verify every Maven coordinate on Maven Central and say which URL", and the out-of-scope list. `CLAUDE.md` contains `@AGENTS.md` plus Claude-specific notes. `.claude/settings.json` carries the deny rules and env from SAI-02 and SAI-03 and no hooks, because a per-edit Maven run costs ten to twenty seconds on Windows; formatting runs inside the gate script.

## Definition of done for a task

| Check | Evidence |
|---|---|
| Task spec exists with intent, constraints, acceptance criteria, technical context, verification | `harness/tasks/T-NNN.yaml` |
| Prompt v1 committed before generation; later versions carry a critique | `harness/prompts/T-NNN/` |
| Raw AI output snapshot exists for non-document tasks | `harness/evidence/T-NNN/ai-raw.patch` |
| Ledger entry lists every changed file with status and a rationale; rejections have notes | `harness/ledger.yaml` |
| Gate record appended by the gate script with real results, log path, and commit | Ledger gate block, `harness/evidence/T-NNN/verify-N.log` |
| Review artifact and sign-off exist if high impact | `harness/reviews/T-NNN.md`, ledger sign-off block |
| Commit carries the `Task:` trailer | Git |
| Docs touched by the change updated in the same task | Diff |
| Harness check clean | Build |

Order matters. A task stays `in-progress` while its gate runs; it is set to `done` only after a gate record with `overall: pass` exists, and the commit follows. Marking a task done first makes HC-04 fail inside the very run that would produce the record. This was learned on T-000: the first gate run (`harness/evidence/T-000/verify-1.log`) is red for exactly that reason and is kept as evidence.

## Git conventions

| Rule | Detail |
|---|---|
| Branching | `main` protected; short-lived branch per task or scenario; squash merge |
| Commit format | Conventional Commits 1.0.0, for example `feat(redirect): return 410 for expired links` |
| Trailer | `Task: T-104` on every non-merge, non-bot commit after the `baseline` tag; the squash message carries it; validated by harness-check |
| No AI references in commits | Repository convention; provenance lives in the ledger entry the trailer resolves to ([ADR 0009](decisions/0009-task-trailer-and-ledger-provenance.md)) |
| Hooks | `scripts/hooks/commit-msg` regex check, enabled with `git config core.hooksPath scripts/hooks` |
| Changelog | Keep a Changelog 1.1.0, assembled at the end of each day |
| Versioning | SemVer starting at 0.1.0; stated as pre-1.0 |
