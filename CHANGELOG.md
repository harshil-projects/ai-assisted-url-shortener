# Changelog

All notable changes to this project are recorded here. Entries are assembled at the end of each build day from the task ledger in `harness/ledger.yaml`.

The format follows [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html). Before 1.0.0 the API may change between minor versions.

## [Unreleased]

### Added

### Changed

### Fixed

## [0.1.0] - 2026-10-12

### Added

- Create short links from validated `http`/`https` URLs with random 7-character Base62 codes or a custom alias; one transaction per allocation attempt; 409 on alias conflicts (T-004).
- Public redirect with `302` and `Cache-Control: no-store`; clicks recorded asynchronously on a bounded, draining executor; `HEAD` resolves without counting (T-005).
- Click statistics (total, last 24 hours, last 7 days, last click) and link deletion with cascading event removal (T-006).
- RFC 9457 Problem Details for every error from controllers, filters and the authentication entry point, each carrying `requestId` and `timestamp` (T-004, T-007).
- Stateless API-key security chain, request-id correlation, 16 KiB request-size cap, per-client rate limiting before authentication, security headers and a Swagger-scoped CSP (T-007).
- OpenAPI 3.1 document with the error catalogue and Swagger UI Authorize button; committed export drift-tested on every build (T-008).
- Flyway migration V1 for `links` and `click_events` validated at startup; H2 in PostgreSQL mode (T-002).
- Health, info, metrics and Prometheus endpoints with custom counters for creates, redirect outcomes and dropped clicks (T-005, T-008).
- Quality gates inside `mvnw verify`: Spotless, Checkstyle, SpotBugs with FindSecBugs, JaCoCo thresholds, enforcer, harness-check (T-001).
- Process-as-code harness: requirement records, task specs, prompts, raw AI patches, ledger, gate logs, reviews and sign-offs, validated by the `harness-check` module (T-000).
- Optional link expiry: `POST /api/v1/links` accepts `expiresAt` as an RFC 3339 timestamp with an offset; a value at or before now is a 400 field error, a zoneless string is the generic 400 for an unreadable body. Flyway migration `V2__add_link_expiry.sql` adds the nullable `links.expires_at` column; existing rows never expire (T-102).
- `GET` and `HEAD /{code}` answer 410 `/problems/link-expired` with `Cache-Control: no-store` once `expiresAt` has passed; no click is recorded and the redirect counter gains `outcome=expired`. The row and its click history are kept (T-102).
- `expiresAt` (nullable) and `expired` (boolean, computed when the response is built) on `LinkResponse` and `StatsResponse`; metadata and statistics of an expired link stay readable with unchanged counts (T-102).
- Expiry regression suite: `ExpiryIT` with a controllable clock, `ExpiryEdgeCasesTest` fixing the boundary as "expired when `expiresAt <= now`", and `MigrationTest.v2AddsNullableExpiresAt` (T-103).
- Click attribution (R-03): each click records the lower-cased host of the `Referer` header and the `utm_source`, `utm_medium` and `utm_campaign` parameters of the short link request, cleaned of control characters and bounded to the column widths; migration V3 adds the four nullable columns to `click_events` one `ALTER TABLE ... ADD COLUMN` at a time. No IP address, user agent, full referrer URL, geography or device value is stored; ADR 0006 unchanged (T-202).
- `GET /api/v1/links/{code}/stats` returns `referrers` (host, clicks) and `campaigns` (source, medium, campaign, clicks), each at most ten rows ordered by clicks descending then name over all clicks of the link (T-202).
- Edge-case table for `ClickAttribution.from`, `AttributionIT` over HTTP, and the attribution section of `docs/reference/api.md` (T-203).

### Changed

- Removed Boot's auto-configured default user so the API key is the only credential (T-007).
- `LinkAllocator.insert(code, url, now)` became `insert(code, url, now, expiresAt)`; `Link` and `CreateLinkRequest` gained the `expiresAt` component (T-102).
- `ApiExceptionHandler` keeps only the exception-to-problem mapping; body construction and the standardisation of framework-produced bodies moved to the package-private `ProblemResponses` helper. No change in behaviour or in the OpenAPI export (T-104).
- Test contexts each get their own in-memory database name, removing an interaction between a cached context's asynchronous click recorder and the next context's Flyway run (T-009).

### Fixed

- `ProblemDetails.toMap` handled a null problem type; the OpenAPI export declared 200 for operations that return 201, 204 and 302 (T-000, T-008).
- Hardening headers were missing on the 413 and 429 responses written by the request-size and rate-limit filters; `ProblemWriter` now sets them (T-105).

[Unreleased]: https://github.com/harshil-projects/ai-assisted-url-shortener/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/harshil-projects/ai-assisted-url-shortener/releases/tag/v0.1.0
