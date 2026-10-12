# Playbook: implement

| Field | Value |
|---|---|
| Input | A task spec from `harness/tasks/{{task.id}}.yaml` |
| Output | A filled prompt committed as `harness/prompts/{{task.id}}/vN.md` before it is run |
| Guardrails | Failing test first, no new dependencies, state what was not verified, security checklist, Boot 4 gotchas |

Fill every placeholder from the spec. Never paste the assignment brief or any secret.

```text
Task {{task.id}}: implement. Read AGENTS.md before writing anything.
Module url-shortener, package dev.harshil.shortener. Spring Boot 4.1, Spring Security 7, Java 21.

Intent:
{{task.intent}}

Constraints:
{{task.constraints}}

Acceptance criteria (EARS):
{{task.acceptance_criteria}}

Technical context (files, interfaces, patterns, references):
{{task.technical_context}}

Verification (tests that must pass):
{{task.verification}}

Guardrails:
1. Write the failing test named in verification first, then the smallest implementation that passes it.
2. Touch only the files listed in technical context. Name any other file you believe must change, and stop.
3. Do not add, remove, or change any Maven dependency or plugin. If one seems needed, say which and why.
4. Do not modify existing tests. Report any test that blocks you and why.
5. Constructor injection only. Methods under 50 lines, files under 400 lines. Records for DTOs.
6. State anything you could not verify, including any API you are not certain exists in Boot 4.1.

Security checklist, design section 3.7. Apply every row this change touches:
- Redirect only to the stored URL validated at create; never reflect request input; schemes http and https only.
- UrlValidator rejects loopback, RFC 1918, link-local, 0.0.0.0/8, multicast, ::1, fc00::/7, fe80::/10, 169.254.169.254, localhost, metadata.google.internal. No DNS resolution.
- Codes are random 7-character Base62 from SecureRandom. The rate limiter runs before authentication.
- JPA parameter binding only. No string-built queries.
- Every user-controlled value passes LogSanitizer before logging. Never log full target URLs.
- API key on /api/v1/** through the filter built inside SecurityConfig. Public: redirect, health, docs.
- Bounds: URL 2048 chars, body 16 KiB, bounded executor and queue, 2-second connection timeout.
- No secrets in code or config. The key comes from APP_API_KEY. No stack traces in responses.
- No IP address or raw user agent is persisted. Errors are RFC 9457 Problem Details.

Boot 4 and Spring Security 7 gotchas, not negotiable:
- spring-boot-starter-webmvc, never starter-web. Jackson 3 is tools.jackson, never com.fasterxml.
- @MockitoBean from org.springframework.test.context.bean.override.mockito, never @MockBean.
- Slices: org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest with MockMvcTester, and org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest.
- Integration: org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient with org.springframework.test.web.servlet.client.RestTestClient.
- Security DSL: lambdas only. No and(), no authorizeRequests(), no AntPathRequestMatcher.
- DTO fields are boxed with @NotNull; Jackson 3 turns a missing primitive into a generic 400.
- Instant fields on TIMESTAMP WITH TIME ZONE columns. One ALTER TABLE ... ADD COLUMN per statement.
- The click executor bean is declared with defaultCandidate = false. The API key filter is never a @Component.
Output: the diff per file, the exact command that runs the named tests, then the list of things not verified.
```
