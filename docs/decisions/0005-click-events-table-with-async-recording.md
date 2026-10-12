---
status: accepted
date: 2026-10-12
decision-makers: Harshil
---

# Click events table as the single source of truth, written off the request thread

In the context of counting clicks for a redirect path that must stay fast and must never fail because of analytics, facing the choice between a counter column, rollup tables, and raw events, we decided for a `click_events` table as the only source of truth, written by a dedicated bounded executor off the request thread, with totals computed by one indexed aggregate query, and neglected a counter column per redirect, rollup tables, and a synchronous insert, to achieve analytics isolated from redirects and simple to reason about, accepting that events can be dropped under saturation or lost after the shutdown drain window and that stats are O(n) over the index at prototype scale.

## Context and Problem Statement

A redirect is one indexed read (NFR-02). Analytics failure or saturation must never fail or delay a redirect beyond a queue offer (NFR-03). Stats need total, last 24 hours, last 7 days, and last click (FR-08). How are clicks recorded so the redirect path is untouched by analytics cost and the numbers stay derivable?

## Decision Drivers

| Driver | Weight |
|---|---|
| Redirect latency and availability independent of analytics | High |
| No hot row serialising every redirect | High |
| Time-window counts derivable without a second store | High |
| Bounded resource use under a redirect flood | High |
| Simplicity for a single node | Medium |

## Considered Options

1. Events table, async insert on a dedicated bounded executor, one aggregate query
2. Counter column on `links` incremented per redirect
3. Rollup tables maintained per redirect or by a scheduler
4. Events table with a synchronous insert on the request thread

## Decision Outcome

Chosen option 1. On the request thread the controller builds an immutable `ClickRecorded(linkId, occurredAt, requestId)` and publishes it. An `@Async("clickRecorderExecutor")` `@EventListener` inserts it in its own transaction. The executor is a `ThreadPoolTaskExecutor` bean with `defaultCandidate = false` so Boot's application executor is not displaced: core 2, max 4, queue 1000, a rejection handler that increments `shortener.clicks.dropped` and drops, `waitForTasksToCompleteOnShutdown = true`, `awaitTerminationSeconds = 10`. Exceptions are caught and counted inside the listener. Nothing from `HttpServletRequest` is touched off the request thread. `ClickEvent.linkId` is a plain `Long`, so a click is one `INSERT` with no `SELECT`. Stats are one query: `count`, two conditional sums, and `max` over the `(link_id, occurred_at)` index, inside a read-only transaction.

### Consequences

| Kind | Consequence |
|---|---|
| Good | Redirect returns 302 while event persistence throws or the queue is full |
| Good | No hot row; inserts are append-only |
| Good | Any window can be computed later from raw events |
| Good | Drops are counted, so lost data is visible in metrics |
| Bad | Events are dropped under saturation and lost if a crash outlives the 10-second drain |
| Bad | Stats cost grows with clicks per link; rollups are the documented next step |
| Bad | The executor uses up to 4 of the 10 pool connections |

### Confirmation

| Check | Where defined |
|---|---|
| `ClickRecordingIT.recordsClickAfterRedirect` | FR-07 |
| `ClickRecorderExecutorTest.rejectionIsCountedNotBlocking` | FR-07, section 3.13 |
| `ClickRecordingIT.redirectSurvivesAnalyticsFailure` | NFR-03 |
| `StatsIT.returnsCounts` | FR-08 |
| `RedirectLatencySmokeTest`, p99 under 200 ms, `-Pperf` | NFR-02, section 4.1 |

## Pros and Cons of the Options

| Option | Pros | Cons |
|---|---|---|
| Events table, async, aggregate query | Isolation; no hot row; derivable windows; bounded | Drops under saturation; O(n) stats |
| Counter column | Cheapest read for totals | Every redirect locks one row; no time windows; still a write on the redirect path |
| Rollup tables | Fast stats at scale | Premature for one node; two sources of truth to reconcile; watermark logic |
| Synchronous insert | Exactly one write per click, no drops | Redirect latency and availability tied to the insert; breaks NFR-03 |

## More Information

High Scalability on bitly; Citus incremental rollups; PlanetScale slotted counters; Spring Boot reference on task execution and graceful shutdown; ByteByteGo "Design a URL Shortener"; Google SRE book.
