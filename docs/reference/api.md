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
| `GET /{code:[A-Za-z0-9_-]{4,32}}` | none | path code | 302, `Location`, `Cache-Control: no-store` | 404, 410 expired, 503 |
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
| `customAlias` | Optional; `^[A-Za-z0-9_-]{4,32}$`, case-sensitive; must not be in the reserved list (`api`, `actuator`, `health`, `metrics`, `prometheus`, `swagger-ui`, `v3`, `api-docs`, `error`, `favicon.ico`, `robots.txt`, `.well-known`, `static`, `assets`, `stats`, `admin`, `login`) |
| `expiresAt` | Optional; RFC 3339 timestamp with an offset; must be in the future; zoneless strings are rejected |

## Response records

| Record | Fields |
|---|---|
| `LinkResponse` | `code`, `shortUrl`, `url`, `createdAt`, `expiresAt` (nullable), `expired` (boolean) |
| `StatsResponse` | `code`, `totalClicks`, `clicksLast24h`, `clicksLast7d`, `lastClickedAt`, `expiresAt`, `expired`; any attribution fields only if the ambiguous scenario decides so |
| Problem Details | `type`, `title`, `status`, `detail`, `instance`, `requestId`, `timestamp`; `errors[]` with field and message on 400 |

`shortUrl` is built from `app.base-url` when set, otherwise from the current request's scheme, host, and port, so a reviewer on another port sees correct values.

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
| `/problems/link-not-found` | Link not found | 404 | Unknown code | handler |
| `/problems/method-not-allowed` | Method not allowed | 405 | Wrong verb | handler |
| `/problems/alias-taken` | Alias already in use | 409 | Custom alias exists, including a lost race on the unique constraint | handler |
| `/problems/link-expired` | Link has expired | 410 | `expiresAt` in the past | handler |
| `/problems/payload-too-large` | Request body too large | 413 | Body over 16 KiB on `/api/v1/**` | `RequestSizeFilter` via `ProblemWriter` |
| `/problems/unsupported-media-type` | Unsupported media type | 415 | Create without `application/json` | handler |
| `/problems/rate-limited` | Too many requests | 429 | Bucket empty; `Retry-After` set | `RateLimitFilter` via `ProblemWriter` |
| `/problems/code-generation-exhausted` | Could not allocate a unique code | 500 | Five random collisions in a row | handler |
| `/problems/service-unavailable` | Service temporarily unavailable | 503 | Database connection cannot be obtained within 2 seconds; `Retry-After` set | handler |
| `about:blank` | Internal error | 500 | Unexpected exception; no stack trace | handler |

## OpenAPI source

[openapi.yaml](openapi.yaml) in this folder is the machine-readable contract. It is exported from `/v3/api-docs.yaml` of the running service, committed, and compared byte for byte by `OpenApiIT.committedSpecMatchesRuntime` in the default build. A change to a controller, record, or the OpenAPI customizer without a matching re-export fails `mvnw verify`. Swagger UI at `/swagger-ui.html` renders the same document and carries an Authorize button for the API key.
