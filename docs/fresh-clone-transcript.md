# Fresh-clone transcript

A timed walkthrough of the README's "Run it" and "Verify in five minutes" paths from an empty
folder, recorded for task T-009 against commit `e8d098f` of `main` (NFR-01, R-01 validation). Every
command and every response below is reproduced as it was issued and returned; nothing was fixed or
retried to make a step pass. Times are UTC.

## Machine

| Item | Value |
|---|---|
| Hardware | Dell Precision 5520, Intel Core i7-7820HQ 2.90 GHz, 8 logical cores (4 physical), 16,232 MB RAM |
| Operating system | Microsoft Windows 10 Pro 10.0.19045 |
| JDK | JDK 21.0.12.1+1 at `C:/Users/Dell/jdks/jdk-21.0.12.1+1`, selected with `JAVA_HOME`; nothing installed system-wide |
| Maven | 3.9.16 through the committed wrapper (`mvnw.cmd`) |
| Dependency cache | warm: `~/.m2` already held every artifact, so the build logged no downloads. The README's 10 to 25 minute first-build estimate applies to an empty cache and was not measured here |
| Shell | Git Bash (MSYS2) on Windows, running `mvnw.cmd` and `curl.exe` 8.18.0 (Schannel). The README shows PowerShell; the only difference is JSON quoting (single quotes instead of `curl.exe --%`) |
| Load | Other Maven builds and JVMs belonging to other agents were running on the same machine throughout, and the perf smoke test of T-009 ran in a sibling worktree while this clone was being verified; timings below are therefore slower than an idle machine would give |
| Source | Local clone of `C:/Users/Dell/ai-workspace/agentic-sdlc-url-shortener`, branch `main`, HEAD `e8d098f10c478cc9b117854f1f37a520bcc6b641` |
| Destination | `C:/Users/Dell/AppData/Local/Temp/fresh-clone-test/shortener`, created empty for this run and deleted at the end |

## Timeline

| UTC | Step | Duration |
|---|---|---|
| 03:41:07 | `git clone --branch main` from the local path | 4 s |
| 03:47:50 | `.\mvnw.cmd -B -ntp verify` started | |
| 03:52:21 | verify finished, `BUILD SUCCESS`, exit 0 | 4 min 31 s wall clock; Maven reports `Total time: 04:16 min` |
| 04:11:48 | `.\mvnw.cmd -B -ntp -pl url-shortener spring-boot:run` started with `SERVER_PORT=8099` | |
| 04:13:11 | `Tomcat started on port 8099`; application started in 37.77 s | 1 min 23 s from the command to the first `200` on `/actuator/health` |
| 04:14:06 to 04:14:09 | README verification steps 1 to 7 | 3 s |
| 04:14:47 to 04:15:57 | 429 check, two bursts | |
| 04:15:11 to 04:17:15 | Swagger UI check with the Authorize button in an automated browser | |
| 04:18:11 | `taskkill //F //T //PID 36376` stopped the Maven JVM and the forked application JVM; port 8099 released | |
| 04:20:35 | clone folder deleted | 4 s |

## 1. Clone

```
$ mkdir -p C:/Users/Dell/AppData/Local/Temp/fresh-clone-test
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-12T03:41:07Z
$ git clone --branch main C:/Users/Dell/ai-workspace/agentic-sdlc-url-shortener C:/Users/Dell/AppData/Local/Temp/fresh-clone-test/shortener
Cloning into 'C:/Users/Dell/AppData/Local/Temp/fresh-clone-test/shortener'...
done.
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-12T03:41:11Z
$ ls C:/Users/Dell/AppData/Local/Temp/fresh-clone-test/shortener
AGENTS.md  CHANGELOG.md  CLAUDE.md  config  docs  harness  harness-check  LICENSE  mvnw  mvnw.cmd  pom.xml  README.md  scripts  url-shortener
```

The clone carries the `baseline` tag, so the harness-check commit-trailer rule (HC-07) ran for real
in the build below instead of skipping with a notice.

## 2. Build with every gate

```
$ cd C:/Users/Dell/AppData/Local/Temp/fresh-clone-test/shortener
$ export JAVA_HOME="C:/Users/Dell/jdks/jdk-21.0.12.1+1"
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-12T03:47:50Z
$ ./mvnw.cmd -B -ntp verify > ../verify.log 2>&1; echo EXIT=$?
EXIT=0
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-12T03:52:21Z
```

Gate lines from `verify.log`, in build order:

```
[INFO] You have 0 Checkstyle violations.                       (reactor)
[INFO] Tests run: 327, Failures: 0, Errors: 0, Skipped: 0      (url-shortener)
[INFO] You have 0 Checkstyle violations.                       (url-shortener)
[INFO] Analyzed bundle 'url-shortener' with 43 classes
[INFO] All coverage checks have been met.
[INFO] Done SpotBugs Analysis....
[INFO] BugInstance size is 0
[INFO] Tests run: 41, Failures: 0, Errors: 0, Skipped: 0       (harness-check)
[INFO] You have 0 Checkstyle violations.                       (harness-check)
[INFO] Analyzed bundle 'harness-check' with 23 classes
[INFO] Reactor Summary for AI-Assisted URL Shortener (reactor) 0.1.0-SNAPSHOT:
[INFO] AI-Assisted URL Shortener (reactor) ................ SUCCESS [  7.177 s]
[INFO] url-shortener ...................................... SUCCESS [03:23 min]
[INFO] harness-check ...................................... SUCCESS [ 43.987 s]
[INFO] BUILD SUCCESS
[INFO] Total time:  04:16 min
[INFO] Finished at: 2026-10-12T09:22:21+05:30
```

Per-class Surefire counts from `url-shortener/target/surefire-reports/` (parameterised tests count
once per case): ActuatorIT 5, ApiExceptionHandlerTest 14, ClickRecordingIT 3, LinkControllerTest 18,
LinkFlowIT 10, LinkManagementIT 3, OpenApiErrorCustomizerTest 7, OpenApiIT 2, ProblemDetailsTest 4,
RedirectControllerTest 6, RedirectIT 5, ShortUrlBuilderTest 4, StatsControllerTest 5, StatsIT 4,
ApiKeyIT 6, AppPropertiesTest 9, ClickRecorderExecutorConfigTest 3, OpenApiConfigTest 5,
ProblemDetailIT 3, ProdStartupIT 1, RateLimitFilterTest 8, RateLimitIT 3, RequestIdFilterTest 13,
RequestSizeFilterTest 11, SecurityHeadersIT 7, LinkTest 4, LogSanitizerTest 9, ReservedWordsTest 33,
ClickEventRepositoryTest 5, LinkRepositoryTest 7, MigrationTest 5, AnalyticsServiceTest 3,
ClickRecorderTest 4, CodeGeneratorTest 5, LinkAllocatorTest 3, LinkServiceTest 17, UrlValidatorTest
72, UrlShortenerApplicationTest 1; total 327. harness-check: GitRulesTest 2, LedgerRulesTest 6,
ParsingHelpersTest 8, RepositoryScanRulesTest 2, SyntheticScanAndHistoryTest 8,
SyntheticTaskLedgerTest 10, TaskSpecRulesTest 2, TraceabilityReportTest 3; total 41.

Coverage from `url-shortener/target/site/jacoco/jacoco.csv`: 43 classes; instructions 2,893 of
2,979 (0.971); branches 180 of 192 (0.938); lines 656 of 672 (0.976); methods 201 of 209 (0.962);
no class with zero covered lines. Thresholds are line 0.80, branch 0.70, zero untested classes.

## 3. Run the service on port 8099

The README's run command, with the port moved as its environment notes describe
(`SERVER_PORT`), started in the background with its output captured:

```
$ cd C:/Users/Dell/AppData/Local/Temp/fresh-clone-test/shortener
$ export JAVA_HOME="C:/Users/Dell/jdks/jdk-21.0.12.1+1"
$ export SERVER_PORT=8099
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-12T04:11:48Z
$ ./mvnw.cmd -B -ntp -pl url-shortener spring-boot:run > ../run.log 2>&1 &
```

Waiting for health with curl's own retry loop, then the first health body:

```
$ curl.exe -s -o /dev/null -w "health=%{http_code} at " --retry 90 --retry-delay 2 --retry-connrefused --retry-all-errors http://localhost:8099/actuator/health; date -u +%Y-%m-%dT%H:%M:%SZ
health=200 at 2026-10-12T04:13:12Z
```

Startup lines from `run.log` (local time +05:30 in the log; 09:43:11 is 04:13:11 UTC):

```
2026-10-12T09:42:35.872+05:30  INFO [req=] 37856 --- [url-shortener] [           main] d.h.shortener.UrlShortenerApplication    : No active profile set, falling back to 1 default profile: "dev"
2026-10-12T09:42:49.837+05:30  INFO [req=] 37856 --- [url-shortener] [           main] org.flywaydb.core.FlywayExecutor         : Database: jdbc:h2:./data/urlshortener (H2 2.4)
2026-10-12T09:42:50.486+05:30  INFO [req=] 37856 --- [url-shortener] [           main] o.f.core.internal.command.DbMigrate      : Successfully applied 1 migration to schema "public", now at version v1
2026-10-12T09:43:11.308+05:30  INFO [req=] 37856 --- [url-shortener] [           main] o.s.boot.tomcat.TomcatWebServer          : Tomcat started on port 8099 (http) with context path '/'
2026-10-12T09:43:11.351+05:30  INFO [req=] 37856 --- [url-shortener] [           main] d.h.shortener.UrlShortenerApplication    : Started UrlShortenerApplication in 37.77 seconds (process running for 40.003)
```

The forked application JVM was PID 37856, child of the Maven JVM PID 36376; the H2 file was created
under `url-shortener/data/` inside the clone.

## 4. The README's seven verification steps

Run in order at 04:14:06 to 04:14:09 UTC against `http://localhost:8099` with
`X-API-Key: dev-only-key-do-not-use` where the README uses the key. `curl.exe -i` output is shown
with its progress meter removed and `\r` stripped; nothing else was edited.

### Step 1: health

```
$ curl.exe -s http://localhost:8099/actuator/health
{"components":{"db":{"details":{"database":"H2","validationQuery":"isValid()"},"status":"UP"},"diskSpace":{"details":{"total":255560200192,"free":2585907200,"threshold":10485760,"path":"C:\\Users\\Dell\\AppData\\Local\\Temp\\fresh-clone-test\\shortener\\url-shortener\\.","exists":true},"status":"UP"},"livenessState":{"status":"UP"},"ping":{"status":"UP"},"readinessState":{"status":"UP"},"ssl":{"details":{"expiringChains":[],"invalidChains":[],"validChains":[]},"status":"UP"}},"groups":["liveness","readiness"],"status":"UP"}
```

Expected a body that starts with `{"status":"UP"`; the dev profile sets `show-details=always`, so
the body starts with `components` and ends with `"status":"UP"`. Status 200. Pass.

### Step 2: create a link with a custom alias

```
$ curl.exe -i -X POST http://localhost:8099/api/v1/links -H "Content-Type: application/json" -H "X-API-Key: dev-only-key-do-not-use" -d '{"url":"https://example.com/docs","customAlias":"demo-link"}'
HTTP/1.1 201 
X-Request-Id: 994d5fb9-c262-4cc5-8f7d-a7196d509ae8
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
Location: /api/v1/links/demo-link
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Type: application/json
Transfer-Encoding: chunked
Date: Mon, 12 Oct 2026 04:14:07 GMT

{"code":"demo-link","shortUrl":"http://localhost:8099/demo-link","url":"https://example.com/docs","createdAt":"2026-10-12T04:14:07.018547500Z"}
```

Expected `HTTP/1.1 201`, `Location: /api/v1/links/demo-link`, a body with `code`, `shortUrl`,
`url`, `createdAt`. Pass. `shortUrl` follows the chosen port, as the README's environment notes say.

### Step 3: follow the short link

```
$ curl.exe -i http://localhost:8099/demo-link
HTTP/1.1 302 
X-Request-Id: 6df1ba60-20d7-4d07-8bc1-cd4d9ddaf289
Location: https://example.com/docs
Cache-Control: no-store
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Length: 0
Date: Mon, 12 Oct 2026 04:14:07 GMT
```

Expected `HTTP/1.1 302`, `Location: https://example.com/docs`, `Cache-Control: no-store`. Pass.
`curl.exe` does not follow redirects without `-L`, so example.com was never contacted.

### Step 4: read the stats

```
$ curl.exe -s -H "X-API-Key: dev-only-key-do-not-use" http://localhost:8099/api/v1/links/demo-link/stats
{"code":"demo-link","totalClicks":1,"clicksLast24h":1,"clicksLast7d":1,"lastClickedAt":"2026-10-12T04:14:07.878477Z"}
```

Expected `"totalClicks":1`, possibly after one repeat because clicks are recorded off the request
thread. The first call, issued about one second after the redirect, already returned 1. Pass.

### Step 5: management API without a key

```
$ curl.exe -i http://localhost:8099/api/v1/links/demo-link
HTTP/1.1 401 
X-Request-Id: c4edef84-cecc-4d6a-ad99-d0ed02ea47fb
WWW-Authenticate: ApiKey realm="url-shortener"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Type: application/problem+json;charset=UTF-8
Content-Length: 224
Date: Mon, 12 Oct 2026 04:14:08 GMT

{"type":"/problems/unauthorized","title":"Missing or invalid API key","status":401,"detail":"Provide a valid X-API-Key header.","requestId":"c4edef84-cecc-4d6a-ad99-d0ed02ea47fb","timestamp":"2026-10-12T04:14:08.535953600Z"}
```

Expected `HTTP/1.1 401`, `WWW-Authenticate: ApiKey realm="url-shortener"`, an
`application/problem+json` body. Pass. The body's `requestId` equals the `X-Request-Id` header.

### Step 6: a code that does not exist

```
$ curl.exe -i http://localhost:8099/no-such-code
HTTP/1.1 404 
X-Request-Id: c5451f81-6c4d-433b-834a-7db3fca5a865
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Mon, 12 Oct 2026 04:14:08 GMT

{"detail":"No link with code no-such-code","instance":"/no-such-code","status":404,"title":"Link not found","type":"/problems/link-not-found","requestId":"c5451f81-6c4d-433b-834a-7db3fca5a865","timestamp":"2026-10-12T04:14:08.818756100Z"}
```

Expected `HTTP/1.1 404` with a Problem Details body. Pass.

### Step 7: delete the link

```
$ curl.exe -i -X DELETE -H "X-API-Key: dev-only-key-do-not-use" http://localhost:8099/api/v1/links/demo-link
HTTP/1.1 204 
X-Request-Id: e2b05097-44cd-47ea-a5b9-dafdcd06df14
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Date: Mon, 12 Oct 2026 04:14:09 GMT
```

Expected `HTTP/1.1 204`. Pass.

Result: 7 of 7 steps returned the expected status and headers.

## 5. The 429 check

The dev profile allows 100 link creations per minute per client address with a greedy (continuous)
refill. The README points at `scripts/demo.ps1` for this check; the committed script's step list
stops at the 404, so the check was done directly with curl's URL globbing, which repeats one request
`N` times over a kept-alive connection.

First burst, 130 creates in 2 s, status codes in order:

```
$ curl.exe -s -o /dev/null -w "%{http_code} " -X POST "http://localhost:8099/api/v1/links?n=[1-130]" -H "Content-Type: application/json" -H "X-API-Key: dev-only-key-do-not-use" -d '{"url":"https://example.com/flood"}'
201 (x103) 429 (x27)
```

(04:14:47 to 04:14:49 UTC; the first 103 were `201`, the remaining 27 `429`. Three requests more
than the capacity succeeded because the bucket refills continuously at 100 tokens per 60 s, and the
burst itself took about two seconds.) One further create issued a second later, at 04:14:50, got
`201` with `X-RateLimit-Remaining: 0`: a single token had refilled in the gap. To capture a 429
verbatim, a second burst of 120 was issued with `-i` and all responses written to a file:

```
$ curl.exe -s -i -X POST "http://localhost:8099/api/v1/links?n=[1-120]" -H "Content-Type: application/json" -H "X-API-Key: dev-only-key-do-not-use" -d '{"url":"https://example.com/flood"}' | sed 's/\r//' > flood2.txt
$ grep -o "HTTP/1.1 [0-9]*" flood2.txt | sort | uniq -c
    101 HTTP/1.1 201
     19 HTTP/1.1 429
```

(04:15:56 to 04:15:57 UTC; the first 429 was response 102.) The last response of that burst,
verbatim:

```
HTTP/1.1 429 
X-Request-Id: 5f23ee57-4603-40f5-abd4-778b7b334e72
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
Retry-After: 1
X-RateLimit-Reset: 60
Content-Type: application/problem+json;charset=UTF-8
Content-Length: 265
Date: Mon, 12 Oct 2026 04:15:57 GMT

{"type":"/problems/rate-limited","title":"Too many requests","status":429,"detail":"The limit of 100 link creations per 60 seconds was exceeded; retry after 1 seconds.","requestId":"5f23ee57-4603-40f5-abd4-778b7b334e72","timestamp":"2026-10-12T04:15:57.686702100Z"}
```

Expected `429` with `Retry-After` as Problem Details. Pass.

Observation, not a failure of the check: the 429 is written by `RateLimitFilter`, which runs before
the Spring Security chain, and the response carries `X-Request-Id` and the rate-limit headers but
none of the three security headers (`X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`)
that every other response in this transcript carries. The design's wording is "headers on every
response". Recorded here as a candidate for the T-010 fresh-context review; no product code was
changed in this walkthrough.

## 6. Swagger UI with the Authorize button

Performed with an automated Chromium session (Playwright) against the same running service, 04:15:11
to 04:17:15 UTC. This stands in for the README's `start http://localhost:8080/swagger-ui.html` step
and the T-008 constraint that the check is recorded with a date; it is an automated check, not the
engineer's own.

| Action | Observed |
|---|---|
| Navigate to `http://localhost:8099/swagger-ui.html` | Redirected to `/swagger-ui/index.html`; page title `Swagger UI`; heading `URL Shortener API 0.1.0-SNAPSHOT OAS 3.1`; description paragraph; server `/ - The origin this document was served from`; six operations (`POST /api/v1/links`, `DELETE /api/v1/links/{code}`, `GET /api/v1/links/{code}`, `GET /api/v1/links/{code}/stats`, `GET /{code}`, `HEAD /{code}`); four schemas (`CreateLinkRequest`, `LinkResponse`, `ProblemDetail`, `StatsResponse`); an `Authorize` button |
| Click `Authorize` | Dialog `Available authorizations` with `ApiKey (apiKey)`, description "Shared API key. Required on /api/v1/** and, in production, on the actuator endpoints other than health.", `Name: X-API-Key`, `In: header`, an empty `Value` box, buttons `Authorize` and `Close` |
| Type `dev-only-key-do-not-use` and press Enter | Dialog shows `Authorized`, `Value: ******`, buttons `Logout` and `Close` |
| Browser console, whole session | 0 errors, 0 warnings, so the Swagger-only Content-Security-Policy blocked none of the UI's scripts, styles or requests |

## 7. Stop and clean up

The service was started through the Maven plugin, so two JVMs existed: the Maven JVM (PID 36376,
child of the `cmd.exe` running `mvnw.cmd`) and the forked application JVM (PID 37856, the process
listening on 8099). Their relationship was read with `wmic process ... get ProcessId,ParentProcessId`
and the tree was terminated from the Maven JVM down:

```
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-12T04:18:11Z
$ taskkill //F //T //PID 36376
SUCCESS: The process with PID 33736 (child process of PID 37856) has been terminated.
SUCCESS: The process with PID 37856 (child process of PID 36376) has been terminated.
SUCCESS: The process with PID 36376 (child process of PID 43168) has been terminated.
$ netstat -ano | grep ":8099" | grep LISTENING
(no output: the port is released)
```

The backgrounded `mvnw.cmd ... spring-boot:run` returned exit code 1 at 04:18:13 UTC, which is the
plugin's normal report when the forked application is killed rather than stopped; the last lines of
`run.log` are the link creations of the second burst, with no shutdown lines because the JVM was
terminated, not signalled. The two `java.exe` processes left on the machine afterwards (PIDs 19968
and 42748) belong to other sessions and were left alone.

The clone, including the H2 file `url-shortener/data/urlshortener.mv.db` the dev profile created
inside it, was then deleted:

```
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-12T04:20:35Z
$ rm -rf C:/Users/Dell/AppData/Local/Temp/fresh-clone-test/shortener
$ date -u +%Y-%m-%dT%H:%M:%SZ
2026-10-12T04:20:39Z
```

The automated browser session had written three page snapshots to a `.playwright-mcp/` folder in the
main checkout (not in the clone or the worktree); they were removed after the session. The scratch
logs this transcript quotes (`verify.log`, `run.log`, `perf-run.log`, `flood2.txt`) lived beside the
clone in the temporary folder and were removed with it.

## Summary

| Check | Result |
|---|---|
| Clone from the local repository | 4 s |
| `mvnw.cmd -B -ntp verify`, every gate, warm cache, loaded machine | `BUILD SUCCESS` in 4 min 16 s (4 min 31 s wall clock); 327 + 41 tests; 0 Checkstyle violations; 0 SpotBugs instances; coverage lines 0.976, branches 0.938 |
| Service start on port 8099 | healthy 1 min 23 s after the command; application started in 37.77 s |
| README steps 1 to 7 | 7 of 7 as expected |
| 429 check | `429` with `Retry-After: 1`, `X-RateLimit-Reset: 60`, Problem Details body, after the 100-per-minute budget was spent |
| Swagger UI with Authorize | rendered, authorized, no console errors |
| Failures | none; one observation (security headers absent on the pre-chain 429) recorded for T-010 |
