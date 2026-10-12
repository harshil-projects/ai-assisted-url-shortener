---
status: accepted
date: 2026-10-12
decision-makers: Harshil
---

# No IP addresses or raw user-agent strings persisted

In the context of recording click events for a service built to a regulated-firm privacy posture, facing the question of which per-click attributes may be stored, we decided for persisting only `link_id` and `occurred_at` per click, and neglected truncated IP addresses and salted daily hashes, to achieve a click log that is not personal data, accepting that geography, device, and unique-visitor analytics are unavailable unless the ambiguous scenario decides otherwise under this record's constraint.

## Context and Problem Statement

GDPR Recital 30 names online identifiers such as IP addresses and cookie identifiers as data that can identify natural persons. A click log holding raw IP addresses and user-agent strings is personal data: it needs a lawful basis, a retention period, and access handling. The assignment brief's audience operates in a regulated setting. What may a click event contain so the prototype never holds personal data by accident?

## Decision Drivers

| Driver | Weight |
|---|---|
| Click log must not be personal data under GDPR Article 4 | High |
| Control must be structural, not a logging habit | High |
| Keep the door open for the ambiguous scenario to decide attribution deliberately | Medium |
| No pseudonymisation machinery to rotate or protect | Medium |

## Considered Options

1. Store only `link_id` and `occurred_at`
2. Store the IP truncated to a /24 or /48 prefix
3. Store a salted daily hash of IP plus user agent
4. Store raw IP and user agent with a retention job

## Decision Outcome

Chosen option 1. The V1 schema has no column for IP or user agent, so the control is enforced by the schema rather than by reviewers remembering a rule. `LogSanitizer` and the rule against logging full target URLs complete the posture. Anything the ambiguous scenario might add is weighed against this record in T-201 and, if adopted, recorded as an update here.

### Consequences

| Kind | Consequence |
|---|---|
| Good | The click log is not personal data; no retention or access-request handling for it |
| Good | Structural control: no column exists to misuse |
| Good | No salt to protect or rotate |
| Bad | No geography, device family, or unique-visitor counts |
| Bad | The ambiguous scenario must justify any attribution field against this record |

### Confirmation

| Check | Where defined |
|---|---|
| Schema review: `V1__create_links_and_click_events.sql` has no IP or user-agent column | NFR-06, section 3.5 |
| `ClickEventTest` asserts the event carries only `linkId`, `occurredAt`, `requestId` | NFR-06 |
| `MigrationTest` proves the committed migrations are the schema that runs | NFR-10 |

## Pros and Cons of the Options

| Option | Pros | Cons |
|---|---|---|
| `link_id` and `occurred_at` only | Not personal data; structural; nothing to rotate | No attribution analytics |
| Truncated IP | Coarse geography possible | Still an online identifier in combination with time; Recital 30 applies |
| Salted daily hash | Unique visitors per day, as privacy-focused analytics tools do | Pseudonymised data is still personal data under Article 4(5) while the salt exists; rotation routine needed |
| Raw IP and user agent | Full attribution | Personal data; lawful basis, retention, and access handling required; outside the brief |

## More Information

GDPR Recital 30 and Articles 4 and 5; Google Analytics IP masking; Plausible data policy; MDN Referrer-Policy; OWASP cheat sheet on Logging.
