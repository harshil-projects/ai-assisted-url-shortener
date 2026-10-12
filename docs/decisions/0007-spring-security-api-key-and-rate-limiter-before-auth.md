---
status: accepted
date: 2026-10-12
decision-makers: Harshil
---

# Spring Security 7 stateless chain with an API key filter, security headers, and the rate limiter placed before the chain

In the context of protecting the management API of a prototype that enterprise reviewers will read, facing the choice between a hand-rolled filter and the framework's security chain, we decided for a Spring Security 7 stateless chain with an API key filter constructed inside `SecurityConfig`, the framework's security headers, and a Bucket4j rate limiter registered before the security chain, and neglected a plain servlet filter without Spring Security and a rate limiter placed after authentication, to achieve familiar, header-complete authentication that also throttles key guessing, accepting that the limiter can only key on client IP.

## Context and Problem Statement

`/api/v1/**` must require a valid `X-API-Key` and answer 401 with `WWW-Authenticate` otherwise (FR-10). Creates must be rate limited with 429 and `Retry-After` (FR-11). Security headers must be present and key guessing throttled (NFR-05). How is this built so reviewers recognise it, and where does the limiter sit relative to authentication?

## Decision Drivers

| Driver | Weight |
|---|---|
| Key guessing throttled, not just wrong-key rejection | High |
| Security headers present on every response | High |
| Enterprise reviewers recognise the mechanism | High |
| Constant-time key comparison; prod fails fast without a key | High |
| Rejected requests cost as little as possible | Medium |

## Considered Options

1. Spring Security chain, API key filter inside the config, limiter before the chain
2. Plain servlet filter for the key, no Spring Security
3. Spring Security chain with the limiter after authentication
4. Spring Security with HTTP Basic instead of an API key header

## Decision Outcome

Chosen option 1. The API key filter is constructed inside `SecurityConfig` and added with `addFilterBefore(..., AuthorizationFilter.class)`; it is never also a `@Component`, or it would run twice. The key is compared with `MessageDigest.isEqual` and read from `APP_API_KEY`. The dev profile ships a documented default; prod fails at startup through `@Validated` `AppProperties` with `@NotBlank`. 401 responses carry `WWW-Authenticate: ApiKey realm="url-shortener"` through the shared `ProblemWriter`. Headers are `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, the default `Cache-Control` block, and a CSP scoped to Swagger UI paths.

The rate limiter runs before authentication. An attacker guessing `X-API-Key` values sends requests that fail authentication. A limiter placed after authentication never sees them, so guessing is unlimited. Placed first, every create attempt from one IP draws from the same bucket whether or not the key is valid, so brute force is capped at the create limit (20 per minute in prod), and a rejected request costs no key comparison and no database work. The cost is that the limiter cannot key on a principal, so it keys on client IP, and `server.forward-headers-strategy=NONE` is the default so a forged `X-Forwarded-For` cannot change the bucket without trusted-proxy configuration.

### Consequences

| Kind | Consequence |
|---|---|
| Good | Key guessing shares the create budget with legitimate creates from the same IP |
| Good | Headers, entry point, and test support come from the framework |
| Good | Prod cannot start with an empty key |
| Bad | Users behind one NAT share a bucket; the limiter resets on restart |
| Bad | More configuration surface; AI drafts drift to the Boot 3 DSL (`and()`, `authorizeRequests`, `AntPathRequestMatcher`) and are rejected |

### Confirmation

| Check | Where defined |
|---|---|
| `ApiKeyIT` with wrong, missing, correct, and public-redirect cases | FR-10 |
| `RateLimitIT` and `RateLimitIT.limitsUnauthenticatedCreates` | FR-11, NFR-05 |
| `SecurityHeadersIT` | NFR-05 |
| `ProdStartupIT` boots the prod profile without a key and expects failure | Section 4.2 |
| SpotBugs with FindSecBugs clean | NFR-05 static analysis gate, section 4.1 |

## Pros and Cons of the Options

| Option | Pros | Cons |
|---|---|---|
| Spring Security chain, limiter before | Headers and entry point built in; throttles guessing; cheap rejection | IP-keyed only; larger configuration surface |
| Plain servlet filter | Smallest code | Hand-rolled headers, entry point, and tests; reviewers would ask why not Spring Security |
| Limiter after authentication | Can key on the API key | Guessing is unlimited; every rejected request still pays the key compare |
| HTTP Basic | Standard scheme with browser prompts | Credentials in a Base64 header on every call; no benefit over a key header for machine clients |

## More Information

Spring Security 7 headers and servlet configuration; OWASP API Security Top 10 2023; OWASP Secure Headers Project; OWASP ASVS 5.0; Bucket4j README; Caffeine README; RFC 6585; Stripe and Cloudflare rate limiter posts; MDN X-Forwarded-For; Spring Boot "Use behind a front-end proxy"; Tomcat 11 RemoteIpValve.
