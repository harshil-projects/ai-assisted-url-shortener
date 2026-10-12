# API reference

| Field | Value |
|---|---|
| Base path for management | `/api/v1` |
| Redirects | At the root, `/{code}` |
| Authentication | `X-API-Key` header on `/api/v1/**`; dev default key is on the README's first screen; prod reads `APP_API_KEY` |
| Error format | `application/problem+json` (RFC 9457) from controllers, filters, and the authentication entry point alike |
| Machine-readable source | [openapi.yaml](openapi.yaml), exported from the running service and drift-tested by `OpenApiIT` |

## Endpoints

| Method and path | Auth | Request | Success | Errors |
|---|---|---|---|---|
| `POST /api/v1/links` | API key | `application/json` only; `{ "url": string, "customAlias"?: string, "expiresAt"?: RFC 3339 with offset }` | 201, `Location: /api/v1/links/{code}`, body `LinkResponse` | 400 validation, 401, 409 alias taken, 413 body too large, 415 wrong content type, 429, 503 |
| `GET /{code:[A-Za-z0-9_-]{4,32}}` | none | path code; the `Referer` header's host and the `utm_source`, `utm_medium`, `utm_campaign` query parameters are recorded for attribution, never forwarded | 302, `Location` is the stored URL exactly, `Cache-Control: no-store` | 404, 410 expired, 503 |
| `HEAD /{code}` | none | path code | Same status as GET; not counted as a click | 404, 410 |
| `GET /api/v1/links/{code}` | API key | path code | 200 `LinkResponse` | 401, 404 |
| `GET /api/v1/links/{code}/stats` | API key | path code | 200 `StatsResponse` | 401, 404 |
| `DELETE /api/v1/links/{code}` | API key | path code | 204 | 401, 404 |
| `GET /actuator/health`, `/health/liveness`, `/health/readiness` | none | | 200 or 503 | |
| `GET /actuator/info`, `/metrics`, `/prometheus` | API key in prod, open in dev | | 200 | |
| `GET /v3/api-docs`, `/v3/api-docs.yaml`, `/swagger-ui.html` | none | | 200 | |

Request rules for create:

| Field | Rule |
|---|---|
| `url` | Absolute, scheme `http` or `https`, non-empty host, no user info, at most 2048 characters; host must not be loopback, RFC 1918, link-local, `0.0.0.0/8`, multicast, `::1`, `fc00::/7`, `fe80::/10`, `169.254.169.254`, `localhost`, or `metadata.google.internal`; scheme and host are lowercased; no DNS resolution |
| `customAlias` | Optional; `^[A-Za-z0-9_-]{4,32}$`, case-sensitive; must not be in the reserved list (`api`, `actuator`, `health`, `metrics`, `prometheus`, `swagger-ui`, `v3`, `api-docs`, `error`, `favicon.ico`, `robots.txt`, `.well-known`, `static`, `assets`, `stats`, `admin`, `login`, `h2-console`); numeric shorthand hosts such as `2130706433` or `0x7f000001` are rejected because browsers resolve them to addresses the block list would otherwise miss |
| `expiresAt` | Optional; RFC 3339 timestamp with an offset, for example `2026-10-12T16:30:00+05:30`; must be strictly in the future at create time, judged by the service clock in UTC: a value at or before now is a 400 `validation-failed` with `errors[0].field == "expiresAt"` and message `expiresAt must be in the future`; a zoneless string such as `2026-10-13T10:00:00` cannot be parsed into an instant and is the generic 400 `validation-failed` with detail `Request body is missing or is not valid JSON` and an empty `errors` list; absent or `null` means the link never expires |

## Response records

| Record | Fields |
|---|---|
| `LinkResponse` | `code`, `shortUrl`, `url`, `createdAt`, `expiresAt` (nullable), `expired` (boolean) |
| `StatsResponse` | `code`, `totalClicks`, `clicksLast24h`, `clicksLast7d`, `lastClickedAt`, `expiresAt`, `expired`, `referrers[]` of `ReferrerStats`, `campaigns[]` of `CampaignStats`; both arrays are always present, at most ten rows each, empty when nothing was recorded |
| `ReferrerStats` | `host` (lower-cased referring host), `clicks` |
| `CampaignStats` | `source`, `medium`, `campaign` (each nullable when the link was shared with only some of the `utm_*` parameters), `clicks` |
| Problem Details | `type`, `title`, `status`, `detail`, `instance`, `requestId`, `timestamp`; `errors[]` with field and message on 400 |

`shortUrl` is built from `app.base-url` when set, otherwise from the current request's scheme, host, and port, so a reviewer on another port sees correct values.

`expiresAt` is echoed in UTC (`Z`) whatever offset was supplied, and is `null` for a link that never expires. `expired` is computed when the response is built, as `expiresAt <= now` against the service clock, so the instant itself already counts as expired. Metadata (`GET /api/v1/links/{code}`) and statistics keep answering 200 for an expired link: the row and its click events are kept, so the counts do not change when a link expires; only the redirect refuses it with 410. Deleting an expired link works as for any other and frees its code.

## Click attribution

Decided in `harness/requirements/R-03.md` ("where clicks come from" means the referring site first and the campaign second); implemented by T-202, proven by `ClickAttributionTest`, `ClickEventRepositoryTest`, `MigrationTest` and `AttributionIT`.

| Aspect | Behaviour |
|---|---|
| Inputs | Only the `Referer` request header and the `utm_source`, `utm_medium`, `utm_campaign` query parameters of the redirect request. Every other header and parameter is ignored |
| Referrer host | The header is parsed with `java.net.URI`; only the lower-cased host of an absolute URL is kept, truncated to 253 characters. Userinfo, port, path, query and fragment are discarded. An absent header, a relative reference, an opaque URI (`data:`, `mailto:`) or anything that is not a URL records `null`; the click still counts |
| Campaign values | The first value of each key; ISO control characters removed, surrounding whitespace trimmed, truncated to 64 characters; empty after cleaning records `null`. Keys are case-sensitive |
| Breakdowns | `referrers`: rows grouped by host, excluding clicks with no host. `campaigns`: rows grouped by the (`source`, `medium`, `campaign`) triple, excluding clicks where all three are null. Each at most ten rows, ordered by `clicks` descending then by name, counted over all clicks of the link; the 24-hour and 7-day totals are unchanged and no window parameter exists yet |
| Redirect | Unchanged: 302 to the stored URL exactly as validated at create time, `Cache-Control: no-store`; query parameters on the short link are recorded, never appended to `Location`. `HEAD` records nothing |
| Storage | Migration V3 adds `referrer_host VARCHAR(253)`, `utm_source`, `utm_medium`, `utm_campaign VARCHAR(64)` to `click_events`, all nullable, one `ALTER TABLE ... ADD COLUMN` per statement |

Privacy position (ADR 0006, unchanged): no IP address, no user agent, no full referrer URL, no geography and no device value is read, derived, logged or stored. `click_events` has exactly seven columns, `id`, `link_id`, `occurred_at` and the four above, and `MigrationTest` fails if another appears. The attribution values are marketing-authored labels and the public host of the previous page, bounded and cleaned on the request thread before the click event is published.

## Headers

| Header | On | Value |
|---|---|---|
| `X-Request-Id` | every response | Echoed when the client's value is at most 64 characters of `[A-Za-z0-9-]`, otherwise generated |
| `X-Content-Type-Options` | every response | `nosniff` |
| `X-Frame-Options` | every response | `DENY` |
| `Referrer-Policy` | every response | `no-referrer` |
| `Cache-Control` | every response | Spring Security's default block, which contains `no-store`; set explicitly by the controller on 302 and 410 so the intent is in code and tests |
| `Content-Security-Policy` | Swagger UI paths only | `default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; object-src 'none'` |
| `Strict-Transport-Security` | HTTPS only | Not visible on local HTTP |
| `WWW-Authenticate` | 401 | `ApiKey realm="url-shortener"` |
| `Location` | 201, 302 | Link resource path on 201; stored target URL on 302 |
| `Retry-After` | 429, 503 | Seconds until the bucket refills; `5` on 503 |
| `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` | create responses | Bucket capacity, remaining tokens, seconds to reset |

## Error catalogue

Type URIs are relative to the service base URL. Every body carries `requestId` and `timestamp`. `ProblemDetailIT` exercises the three filter-produced paths and one handler path and asserts the identical shape.

| Type URI suffix | Title | Status | Raised when | Produced by |
|---|---|---|---|---|
| `/problems/validation-failed` | Request validation failed | 400 | Bean Validation or URL rules fail; `errors[]` lists fields | handler |
| `/problems/unauthorized` | Missing or invalid API key | 401 | `X-API-Key` absent or wrong | entry point via `ProblemWriter` |
| `/problems/link-not-found` | Link not found | 404 | Unknown code, or a path no controller serves ("No resource at this path") | handler |
| `/problems/method-not-allowed` | Method not allowed | 405 | Wrong verb | handler |
| `/problems/alias-taken` | Alias already in use | 409 | Custom alias exists, including a lost race on the unique constraint | handler |
| `/problems/link-expired` | Link has expired | 410 | `GET` or `HEAD /{code}` for a link whose `expiresAt` is at or before now; `Cache-Control: no-store` is set on the response; no click is recorded and `shortener.redirects{outcome=expired}` is incremented; the row and its history stay | handler |
| `/problems/payload-too-large` | Request body too large | 413 | Body over 16 KiB on `/api/v1/**` | `RequestSizeFilter` via `ProblemWriter` |
| `/problems/unsupported-media-type` | Unsupported media type | 415 | Create without `application/json` | handler |
| `/problems/rate-limited` | Too many requests | 429 | Bucket empty; `Retry-After` set | `RateLimitFilter` via `ProblemWriter` |
| `/problems/code-generation-exhausted` | Could not allocate a unique code | 500 | Five random collisions in a row | handler |
| `/problems/service-unavailable` | Service temporarily unavailable | 503 | Database connection cannot be obtained within 2 seconds; `Retry-After` set | handler |
| `about:blank` | Internal error | 500 | Unexpected exception; no stack trace | handler |

## OpenAPI source

[openapi.yaml](openapi.yaml) in this folder is the machine-readable contract. It is exported from `/v3/api-docs.yaml` of the running service, committed, and compared byte for byte by `OpenApiIT.committedSpecMatchesRuntime` in the default build. A change to a controller, record, or the OpenAPI customizer without a matching re-export fails `mvnw verify`. Swagger UI at `/swagger-ui.html` renders the same document and carries an Authorize button for the API key.
