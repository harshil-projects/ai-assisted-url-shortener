# Copilot instructions

Read `AGENTS.md` at the repository root before writing anything. It is the single source of instructions for every assistant working here: project layout, the ten-step lifecycle, Boot 4 and Spring Security 7 gotchas, security rules, out-of-scope items, and commit rules. This file only restates the commands.

## Commands

| Purpose | Command |
|---|---|
| Run the service, dev profile | `.\mvnw.cmd -pl url-shortener spring-boot:run` or `scripts/run.ps1 [-Port 9090]` |
| Full build with every gate | `.\mvnw.cmd verify` |
| Full build plus the perf smoke test | `.\mvnw.cmd -Pperf verify` |
| Snapshot the raw AI diff for a task | `scripts/snap.ps1 T-NNN` |
| Format, verify, and append the gate record | `scripts/gate.ps1 T-NNN [-Perf]` |
| Regenerate the traceability report | `scripts/report.ps1` |
| Walk the API against a running service | `scripts/demo.ps1` |
| Enable the commit message hook | `git config core.hooksPath scripts/hooks` |

## Rules that bind every suggestion

| Rule | Where it is spelled out |
|---|---|
| Work only inside a task spec; touch only the files it names | `AGENTS.md`, Lifecycle |
| Spring Boot 4.1 and Spring Security 7 idioms only; no `@MockBean`, no `com.fasterxml`, no `and()` | `AGENTS.md`, Gotchas |
| Never change a test to make it pass; never change tests and implementation in one prompt unless the spec says so | `AGENTS.md`, Conventions |
| Verify every Maven coordinate on Maven Central and state the URL; do not edit a pom unless the spec names it | `AGENTS.md`, Dependencies |
| Apply the security rules to every change that touches validation, redirects, logging, auth, or limits | `AGENTS.md`, Security rules |
| Commit headers follow Conventional Commits and carry a `Task: T-NNN` trailer; no AI product names in commits | `AGENTS.md`, Commits |
| State anything you could not verify | Every playbook in `harness/playbooks/` |
