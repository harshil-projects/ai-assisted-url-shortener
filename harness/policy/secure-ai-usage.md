# Secure AI Usage Policy

| Field | Value |
|---|---|
| Owner | Harshil, the engineer accountable for every line shipped |
| Applies to | Every AI-assisted change in this repository, in any of the six activities |
| Effective | 2026-10-12 |
| Related | `AGENTS.md`, `.claude/settings.json`, `harness/README.md`, harness-check rules HC-09 and HC-10 |

## Data classification rule

Only public or synthetic data may enter a prompt, a repository fixture, or a log line. The assignment brief is never pasted into a prompt and never committed. AI session transcripts are never committed. Secrets never appear in prompts, and the assistant is denied read access to secret files by configuration. When in doubt, the data stays out.

## Engineer accountability statement

Every line in this repository is the engineer's. AI drafts inside a task; the engineer reviews every diff, marks each file generated, edited, or rejected with a written rationale, runs the gates, signs off on high-impact changes, and commits. A rejected draft is evidence of judgment, not waste. Where a control cannot be enforced by a mechanism, the engineer attests to it below and labels it so, and a reviewer can check the record it leaves.

## Controls

| ID | Control | Mechanism | Enforced or attested | Reference |
|---|---|---|---|---|
| SAI-01 | Only public or synthetic data enters prompts, repository fixtures, or logs. The assignment brief is never pasted or committed. Transcripts are never committed | `.gitignore` entries for `*.pdf` and `*.docx`; review of every commit before push | Attested, with the gitignore enforced | OWASP Top 10 for LLM Applications 2025, LLM02 Sensitive Information Disclosure |
| SAI-02 | AI account configured with training off; feedback and transcript uploads disabled | Account setting checked by the engineer on the real account and recorded in the attestation log below; committed `.claude/settings.json` env `DISABLE_FEEDBACK_COMMAND=1` and `CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC=1` | Account setting attested; env enforced | Vendor data-usage documentation |
| SAI-03 | AI cannot read secrets or run exfiltration commands | `.claude/settings.json` `permissions.deny`: `Read(**/.env*)`, `Read(~/.ssh/**)`, `Read(~/.aws/**)`, `Bash(curl *)`, `Bash(wget *)`, `Bash(ssh *)`, `Bash(scp *)`, `Bash(nc *)` | Enforced | OWASP LLM06 Excessive Agency |
| SAI-04 | AI output is untrusted until snapshotted, gated, and reviewed | Lifecycle steps 5 to 7: `scripts/snap.ps1` saves the raw patch, `scripts/gate.ps1` appends the gate record, the ledger entry records the per-file decision | Enforced by harness-check presence rules HC-03, HC-04, HC-06; review quality attested | OWASP LLM05 Improper Output Handling |
| SAI-05 | Every AI-suggested dependency is verified on Maven Central, by the engineer or via the assistant's fetch tool against Maven Central only, and the URL is recorded | Ledger entry rationale carries the Maven Central URL; Dependabot watches versions weekly | Attested, with the record checked in review | Spracklen et al., package hallucination study, USENIX Security 2025 |
| SAI-06 | Test diffs are reviewed for deleted, skipped, or weakened tests. Tests and implementation are not changed in one prompt unless the task spec says so | Review checklist in `harness/templates/review.template.md`; playbook rule in `harness/playbooks/refactor.md` and `debug.md`; JaCoCo CLASS MISSEDCOUNT 0 | Coverage enforced; review attested | Documented test-gaming behaviour in AI coding agents |
| SAI-07 | Prompts state security requirements explicitly | `harness/playbooks/implement.md` carries the design section 3.7 checklist; every filled prompt inherits it | Enforced by template | Veracode GenAI code security reports 2025 and 2026 |
| SAI-08 | AI configuration files are treated as executable | Hidden-Unicode scan HC-09 over `CLAUDE.md`, `AGENTS.md`, `.claude/**`, `.github/copilot-instructions.md`; files reviewed like dependencies | Enforced | Rules-file backdoor research, Pillar Security 2025 |
| SAI-09 | No untrusted web, issue, or MCP content is pulled into a build session without approval | Procedural: no project-level MCP servers are committed; user-level servers are the engineer's responsibility; fetched content is read for facts only | Attested | OWASP LLM01 Prompt Injection |
| SAI-10 | High-impact changes require a review artifact and sign-off | Reason codes schema, security, dependency, data-deletion from design section 5.6; HC-05 fails the build when a done high-impact task lacks a sign-off pointing to an existing review | Enforced | The assignment brief's sign-off requirement |
| SAI-11 | Native Windows runs the assistant unsandboxed | Policy text; the deny rules of SAI-03 and manual approval of every command are the compensating controls | Stated | Vendor sandboxing documentation |
| SAI-12 | Engineer accountability statement | This policy, `README.md`, and `docs/engineering-summary.md` | Stated | Open-source AI contribution policies: Linux kernel, Fedora, Chromium |

## How to read the labels

| Label | Meaning |
|---|---|
| Enforced | A mechanism in the repository or the tool blocks the violation, or a build test fails on it |
| Attested | The engineer states the control is followed. A reviewer can check the record it leaves, not the act itself |
| Stated | A fact about the environment, recorded so a reviewer knows the residual risk |

## Attestation log

Add a row whenever an attested control is checked. Dates are UTC.

| Date | Control | Statement | By |
|---|---|---|---|
| 2026-10-12 | SAI-01 | `.gitignore` carries `*.pdf` and `*.docx`; the assignment brief is held outside the repository | Harshil |
| 2026-10-12 | SAI-03 | `.claude/settings.json` committed with the eight deny rules and the two env settings | Harshil |
