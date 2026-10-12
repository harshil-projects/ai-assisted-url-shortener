# Playbook: review-prep

| Field | Value |
|---|---|
| Activity | review-prep |
| Input | The task spec, the diff, `AGENTS.md`, and the design sections referenced in technical context |
| Output | `harness/reviews/{{task.id}}.md` following `harness/templates/review.template.md` |
| Guardrails | Fresh context, diff summary, definition-of-done checklist, gaps against the spec not style |

Run this in a fresh session with no prior conversation. Attach the diff or give the two commit ids.

```text
Task {{task.id}}: review preparation. You are a reviewer in a fresh session. You have not seen the prompts
or the conversation that produced this change, and you must not ask for them.

Inputs: this task spec, the diff, AGENTS.md, and the design sections named in technical context.

Intent:
{{task.intent}}

Constraints:
{{task.constraints}}

Acceptance criteria:
{{task.acceptance_criteria}}

Technical context:
{{task.technical_context}}

Verification:
{{task.verification}}

Produce, in this order, in the shape of harness/templates/review.template.md:
1. Diff summary: one row per file, what changed in one sentence.
2. Definition-of-done checklist, design section 4.6: for each check, the evidence found or "not found".
3. Gaps against the task spec: each acceptance criterion and constraint, whether the diff satisfies it, and
   the test that proves it. A criterion without a proving test is a gap.
4. Security rows touched: which design section 3.7 rules this change touches and whether each holds.
5. Test diff audit: every deleted, skipped, weakened, or renamed assertion, with the file and line.
6. Defect candidates: anything that looks wrong, with a reproduction note, marked likely or unsure.

Rules:
- Report gaps against the spec, not style. Formatting, naming, and layout are the build's job.
- Do not propose rewrites. Name the gap and the evidence.
- Do not change any file.
- State what you could not verify.
```
