# Playbook: document

| Field | Value |
|---|---|
| Activity | document |
| Input | A task spec from `harness/tasks/{{task.id}}.yaml` naming the audience and the Diataxis type |
| Output | A filled prompt committed as `harness/prompts/{{task.id}}/vN.md` before it is run |
| Guardrails | Audience, Diataxis type, derive from code and tests, do not invent features |

Fill every placeholder. The document must be checkable against the repository, not against memory.

```text
Task {{task.id}}: document. Read AGENTS.md before writing anything.

Intent:
{{task.intent}}

Audience (who reads this and what they already know):
{{task.constraints}}

Diataxis type and acceptance criteria (tutorial, how-to, reference, or explanation; what the reader can do
or answer after reading):
{{task.acceptance_criteria}}

Sources (the code, tests, configuration, and design sections this document is derived from):
{{task.technical_context}}

Verification (how the document is checked: a command whose output it must match, a test it must agree with,
or a review question):
{{task.verification}}

Rules:
1. Derive every statement from the sources listed. Quote the file and line or the test name next to any
   claim a reviewer might doubt.
2. Do not invent features, flags, endpoints, or behaviour. If the sources do not show it, it does not go in.
3. Keep to the Diataxis type. A how-to does not explain design; a reference does not teach.
4. Tables over prose. Short sentences. No placeholders, no "coming soon".
5. Commands must be copy-pasteable on Windows PowerShell 5.1 and on bash where both are relevant.
6. Never name the hiring firm; say "the assignment brief". Never name an AI product in commit text.
7. State anything you could not verify against the sources.

Output: the document, then a list of every source location used, then the list of things not verified.
```
