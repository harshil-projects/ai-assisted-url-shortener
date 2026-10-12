---
status: accepted
date: 2026-10-12
decision-makers: Harshil
---

# 302 redirects with Cache-Control no-store set by the controller

In the context of serving short-link redirects whose every click must be counted and whose expiry must be enforced, facing the choice of redirect status code and caching directive, we decided for 302 Found with `Cache-Control: no-store` set explicitly by the controller, and neglected 301 Moved Permanently and 307 Temporary Redirect, to achieve a redirect that always reaches the server, accepting one extra round trip per click compared with a browser-cached 301.

## Context and Problem Statement

Analytics (FR-07, FR-08), expiry (FR-14), and delete (FR-09) only work if every click reaches the service. A redirect that a browser or intermediary caches is served without the service seeing it. Which status code and cache directive guarantee the server sees each click without surprising clients?

## Decision Drivers

| Driver | Weight |
|---|---|
| Every click reaches the server | High |
| Expired or deleted links stop redirecting immediately | High |
| Intent visible in code and asserted in tests, not left to framework defaults | Medium |
| Standard behaviour across browsers and HTTP clients | Medium |

## Considered Options

1. 302 with explicit `Cache-Control: no-store`
2. 301 Moved Permanently
3. 307 Temporary Redirect
4. 302 relying on Spring Security's default cache headers only

## Decision Outcome

Chosen option 1. RFC 9110 section 15.1 lists 301 and 308 among the status codes that are heuristically cacheable: a cache may reuse them with a heuristic freshness lifetime even when the response carries no explicit freshness information. A cached 301 means later clicks never reach the server, so clicks are undercounted and an expired or deleted link keeps redirecting from the cache. 302 and 307 are not heuristically cacheable, but a cache may still store them when explicit freshness information is present. The controller therefore sets `no-store` itself on the 302 and on the 410 for expired links, so the intent is in the code and in the tests rather than in a framework default the next engineer might change.

### Consequences

| Kind | Consequence |
|---|---|
| Good | Every click is counted; expiry and delete take effect on the next click |
| Good | 302 and 410 share one explicit header, asserted by tests |
| Good | `HEAD` resolves with the same status and is not counted, so link checkers do not inflate stats |
| Bad | Each click costs a server round trip that a cached 301 would avoid |
| Bad | No search engine link equity transfer, which does not matter for a shortener |

### Confirmation

| Check | Where defined |
|---|---|
| `RedirectIT.redirectsWith302` asserts 302, `Location`, and `no-store` | FR-05 |
| `ExpiryIT.expiredLinkIs410AndStatsRemain` asserts 410 with `no-store` | FR-14 |
| `ClickRecordingIT.recordsClickAfterRedirect` proves the click is seen | FR-07 |

## Pros and Cons of the Options

| Option | Pros | Cons |
|---|---|---|
| 302 with explicit `no-store` | Not heuristically cacheable; intent explicit; same header on 410 | One round trip per click |
| 301 | Browsers cache it, fewer requests | Heuristically cacheable under RFC 9110; undercounts clicks; blocks expiry; cannot be un-cached from the server |
| 307 | Identical to 302 for GET; preserves method on POST | Method preservation is irrelevant to GET and HEAD redirects; less familiar to some clients |
| 302 with framework defaults only | Fewer lines | Relies on Spring Security's header block; intent not visible in the controller or asserted on the redirect path |

## More Information

RFC 9110 and RFC 9111; MDN redirect status pages; Spring Security headers reference; High Scalability on bitly; Hello Interview Bitly breakdown.
