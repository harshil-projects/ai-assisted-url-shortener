# Playbook: test-gen

| Field | Value |
|---|---|
| Activity | test-gen |
| Input | A task spec from `harness/tasks/{{task.id}}.yaml` with the behaviour list in acceptance criteria |
| Output | A filled prompt committed as `harness/prompts/{{task.id}}/vN.md` before it is run |
| Guardrails | Behaviour list, naming rule, no logic in tests, edge-case table, coverage target |

Fill every placeholder. The implementation under test is read-only in this task unless the spec says otherwise.

```text
Task {{task.id}}: generate tests. Read AGENTS.md before writing anything.
Module url-shortener, package dev.harshil.shortener. JUnit 6, AssertJ, Mockito, Boot 4 test starters.

Intent:
{{task.intent}}

Behaviours to cover (acceptance criteria, EARS form, one test per line at minimum):
{{task.acceptance_criteria}}

Constraints:
{{task.constraints}}

Technical context (classes under test, test layer, fixtures, profile):
{{task.technical_context}}

Verification (the test classes to produce and the coverage command):
{{task.verification}}

Rules:
1. Do not change production code. If a behaviour cannot be tested without a change, say so and stop.
2. One behaviour per test. Names read method_scenario_expectedOutcome or shouldX_whenY.
3. No logic in tests: no if, loops, or computed expectations. Use @ParameterizedTest for tables.
4. Before writing, produce an edge-case table: input, expected outcome, boundary reason. Cover empty,
   maximum length, one past maximum, reserved values, race or concurrency where relevant, and clock edges.
5. Use the Boot 4 packages: org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest with
   MockMvcTester, org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest, @MockitoBean from
   org.springframework.test.context.bean.override.mockito, RestTestClient for integration tests.
6. Every test class carries the project's @ActiveProfiles("test") meta-annotation. Integration tests clean
   their tables in @AfterEach. Timeouts use SAME_THREAD only. No Thread.sleep; use Awaitility.
7. Assert on status, Problem type, and body fields, never on exception message text.
8. Coverage target: the class under test reaches at least 80 percent lines and 70 percent branches, and no
   production class in scope is left untouched.
9. State anything you could not verify.

Output: the edge-case table, then each test file, then the command that runs them with coverage.
```
