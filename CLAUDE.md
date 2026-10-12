@AGENTS.md

Claude-specific notes:

1. Plan before any multi-file change: list the files you will touch and why, check them against the task spec's technical context, and stop if the list differs.
2. Show test output as evidence: paste the Surefire summary or the failing assertion verbatim; never describe a test as passing without the run that shows it.
3. Never modify, delete, skip, or weaken a test to make it pass. Report the test that blocks you and stop.
4. State anything not verified: an API you are unsure exists in Boot 4.1, a coordinate you did not check on Maven Central, a behaviour you did not run.
5. Use the playbooks in `harness/playbooks/` for every activity; the filled prompt lives in `harness/prompts/T-NNN/vN.md` and is committed before it is run.
