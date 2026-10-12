# Impact analysis: R-02 link expiry (task T-101)

Engineer-authored before any expiry code was written. Task T-102 must stay inside the predicted
main-source file list below; harness-check rule HC-12 compares this list with the files T-102's
commits actually change and fails the build on an unpredicted main-source file. Tests and
documentation are exempt from the rule but are listed so the plan is complete.

| Field | Value |
|---|---|
| Requirement | R-02: "Links must be able to expire. Expired links must stop redirecting and must show as expired. Tighten the test suite and docs while there." |
| Baseline analysed | `main` after T-008, greenfield complete (327 service tests) |
| Analysed by | Harshil, reading the code; the assistant was asked afterwards to list omissions and found none |
| Written at | 2026-10-12T09:05:00+05:30 |

## The change in one paragraph

A link gains an optional `expiresAt` instant supplied at creation. Creation rejects a past or
zoneless value. A redirect for an expired link answers 410 instead of 302 and records no click.
Metadata and statistics report an `expired` flag and the `expiresAt` value. Expired rows are kept so
history survives; nothing is purged (design 3.6, decision in `docs/decisions/0004` and `0005`).

## Data flow walk-through

| Flow | Today | After the change |
|---|---|---|
| Create | `LinkController.create` → Bean Validation → `LinkService.create` → `UrlValidator` → `LinkAllocator.insert(code, url, now)` → `Link(code, url, createdAt)` | `CreateLinkRequest` carries `expiresAt` (boxed `Instant`); `LinkService.create` rejects `expiresAt <= now` with a 400 field error; `LinkAllocator.insert` takes the instant; `Link` stores it in the new nullable column |
| Redirect | `RedirectController.redirect` → `LinkService.resolve` → 302, click published | `LinkService.resolve` throws `LinkExpiredException` when `expiresAt` is before `clock.instant()`; `ApiExceptionHandler` maps it to 410 `/problems/link-expired` with `Cache-Control: no-store`; no click is published; `shortener.redirects{outcome=expired}` is counted |
| Metadata | `LinkController.get` → `LinkResponse` | `LinkResponse` gains `expiresAt` and `expired`, computed against the clock at response time |
| Stats | `StatsController.stats` → `AnalyticsService.stats` → `StatsResponse` | `StatsResponse` gains `expiresAt` and `expired`; counts are unchanged because expired rows and their events stay |
| Delete | unchanged | unchanged |
| Schema | V1 | V2 adds `links.expires_at TIMESTAMP WITH TIME ZONE` (nullable), one `ALTER TABLE ... ADD COLUMN` statement; `ddl-auto=validate` forces the entity and the column to agree |
| Documentation | `docs/reference/openapi.yaml`, `api.md` | Export regenerated through `OpenApiIT`; the error catalogue row for 410 becomes live |

## Impacted modules and the reason

| Module or class | Why it changes | Risk | Mitigation |
|---|---|---|---|
| `V2__add_link_expiry.sql` (new) | New nullable column | Low; additive migration | `MigrationTest` asserts the column and type; startup validation |
| `domain/Link` | New field, constructor, accessor, `isExpired(Instant)` | Medium; entity validated against the schema at startup | `LinkTest` extended; `ddl-auto=validate` |
| `domain/LinkExpiredException` (new) | One domain exception per problem type (design 4.3) | Low | `ApiExceptionHandlerTest` |
| `api/dto/CreateLinkRequest` | Optional `expiresAt` | Medium; Jackson 3 parsing of RFC 3339 with offset; a zoneless string must be a 400, not a 500 | `LinkControllerTest` cases for past, zoneless, future, absent |
| `api/dto/LinkResponse`, `api/dto/StatsResponse` | Two new members | Low; additive | Slice tests, OpenAPI drift test |
| `api/LinkController`, `api/StatsController` | Build responses with the clock | Low | Slice tests |
| `service/LinkService` | Future check at create; expiry check in `resolve`; `get` keeps returning expired links | Medium; the create check uses the same clock as the redirect check | `LinkServiceTest` with a fixed clock |
| `service/LinkAllocator` | Signature gains the instant | Low | `LinkAllocatorTest` |
| `service/AnalyticsService` | Expired flag in the response | Low | `AnalyticsServiceTest` |
| `api/RedirectController` | Counts `outcome=expired`; never publishes a click for an expired link; 410 carries `no-store` | Medium; the public path | `RedirectControllerTest`, `RedirectIT`, `ExpiryIT` |
| `api/ApiExceptionHandler` | 410 mapping with `Cache-Control: no-store` | Low | `ApiExceptionHandlerTest` |
| `config/ClockConfig` | Unchanged; tests override the `Clock` bean | None | Fixed clock in `ExpiryIT` |
| `OpenApiErrorCustomizer` | 410 added to the redirect operation's catalogue | Low | `OpenApiIT` |

## Impacted APIs

| Endpoint | Change | Backwards compatible |
|---|---|---|
| `POST /api/v1/links` | Accepts optional `expiresAt`; new 400 cases | Yes; the field is optional |
| `GET /{code}` | New 410 outcome | Yes for live links |
| `GET /api/v1/links/{code}` | Two new members | Yes; additive |
| `GET /api/v1/links/{code}/stats` | Two new members | Yes; additive |

## Impacted data

| Table | Change | Existing rows |
|---|---|---|
| `links` | `expires_at` nullable | Null means never expires; no backfill |
| `click_events` | none | none |

## Tests to add or tighten (R-02 asks for it)

| Test | Purpose |
|---|---|
| `ExpiryIT` | Fixed clock: create with future expiry → 302; move the clock → 410 with `no-store`, metadata `expired: true`, stats still return the counts |
| `ExpiryIT.rejectsPastAndZoneless` | 400 with `errors[0].field == "expiresAt"` for a past value and for a zoneless string |
| `MigrationTest.v2AddsNullableExpiresAt` | Column present, type with time zone, nullable |
| `RedirectIT` | Expired link publishes no click; `outcome=expired` counter increments |
| Existing suites | Every `Link` construction updated; no assertion weakened |

## Risks and failure scenarios

| Risk | Consequence | Control |
|---|---|---|
| Clock skew between create and redirect | A link expires early or late by the skew | One injected `Clock`, UTC everywhere; documented |
| Expired links still occupy aliases | An alias cannot be reused while its link exists | By design; delete reclaims it; documented |
| Caches holding a 302 past expiry | A client never sees the 410 | `Cache-Control: no-store` on 302 since T-005 |
| Jackson 3 parsing a zoneless timestamp | 500 instead of 400 | `HttpMessageNotReadable` maps to a 400 with a fixed detail; a dedicated test |

## Smells observed while reading (input for T-104)

| # | Smell | Measurement today | Why it matters for expiry | Refactor proposed |
|---|---|---|---|---|
| 1 (top) | `api/ApiExceptionHandler` mixes two responsibilities: mapping domain exceptions and re-shaping framework bodies. It is the largest production class and the 410 mapping makes it grow again | 305 lines, 6 domain handlers plus 6 framework overrides plus 7 private helpers in one class | Every new problem type lands here; the file-length gate (400) will be hit within two more features | Extract the body construction and the "standardise a framework ProblemDetail" logic into a small `ProblemResponses` helper so the advice keeps only the mapping table. Behaviour preserving; the test tree stays unchanged; measured by lines per class and by `ApiExceptionHandlerTest` still green |
| 2 | `LinkService.get` delegates to `resolve`; after expiry the two must diverge (metadata shows expired links, redirect refuses them) | 2 methods, 1 body | The divergence point exists by design; no refactor, just the change itself | none |
| 3 | `RedirectController` and `StatsController` will each compute `expired` from the clock | duplicated expression in two places after T-102 | Drift between metadata and redirect semantics | Put `isExpired(Instant)` on `Link` once and call it from both; part of T-102, not a separate refactor |

## Predicted files

- url-shortener/src/main/resources/db/migration/V2__add_link_expiry.sql
- url-shortener/src/main/java/dev/harshil/shortener/domain/Link.java
- url-shortener/src/main/java/dev/harshil/shortener/domain/LinkExpiredException.java
- url-shortener/src/main/java/dev/harshil/shortener/api/dto/CreateLinkRequest.java
- url-shortener/src/main/java/dev/harshil/shortener/api/dto/LinkResponse.java
- url-shortener/src/main/java/dev/harshil/shortener/api/dto/StatsResponse.java
- url-shortener/src/main/java/dev/harshil/shortener/api/LinkController.java
- url-shortener/src/main/java/dev/harshil/shortener/api/StatsController.java
- url-shortener/src/main/java/dev/harshil/shortener/api/RedirectController.java
- url-shortener/src/main/java/dev/harshil/shortener/api/ApiExceptionHandler.java
- url-shortener/src/main/java/dev/harshil/shortener/api/OpenApiErrorCustomizer.java
- url-shortener/src/main/java/dev/harshil/shortener/service/LinkService.java
- url-shortener/src/main/java/dev/harshil/shortener/service/LinkAllocator.java
- url-shortener/src/main/java/dev/harshil/shortener/service/AnalyticsService.java
- url-shortener/src/main/java/dev/harshil/shortener/repository/LinkRepository.java
- docs/reference/openapi.yaml
- docs/reference/api.md
