package dev.harshil.shortener.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.harshil.shortener.api.dto.CreateLinkRequest;
import dev.harshil.shortener.api.dto.LinkResponse;
import dev.harshil.shortener.config.ClickRecorderExecutorConfig;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.LinkRepository;
import dev.harshil.shortener.support.TestApiKey;
import dev.harshil.shortener.support.TestProfile;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.HdrHistogram.Histogram;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;

/**
 * Opt-in latency smoke test for the public redirect, {@code GET /{code}} (NFR-02; design 3.8, 4.1,
 * 4.2).
 *
 * <p>This is a smoke threshold, not a capacity claim. One client thread drives an in-process server
 * on a random port backed by in-memory H2, with no network hop, no concurrency and no load beyond
 * the warm-up below. The numbers bound the service's own work on the redirect path and nothing
 * else; throughput, saturation and behaviour behind a proxy are listed as untested in
 * docs/testing.md.
 *
 * <p>Procedure: one link is created through the API with the test key; 200 warm-up redirects are
 * discarded; 2,000 redirects are then timed, each as one round trip through a JDK HttpClient that
 * never follows redirects, so a sample ends when the 302 and its headers have been read and the
 * target is never contacted. Samples go into an HdrHistogram; p50, p95, p99 and max are printed in
 * milliseconds and the test fails when p99 reaches 200 ms.
 *
 * <p>Timings depend on the machine and on what else it is doing. The figures recorded for T-009 in
 * docs/scenarios/greenfield.md were taken on the engineer's laptop while other Maven builds ran
 * concurrently, so they are slower than an idle machine would give; the budget leaves headroom for
 * that. The test is excluded from the default build through {@code surefire.excludedGroups=perf}
 * and enabled with {@code -Pperf}, which the Ubuntu CI job runs (design 4.5).
 *
 * <p>The loop in {@link #redirect(int)} is the measurement itself and is the one place where the
 * project rule "no logic in tests" yields to the T-009 constraint that names the warm-up and sample
 * counts.
 */
@Tag("perf")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class RedirectLatencySmokeTest {

  static final int WARM_UP_REQUESTS = 200;
  static final int MEASURED_REQUESTS = 2000;
  static final double P99_BUDGET_MILLIS = 200.0;

  private static final String TARGET = "https://example.com/perf";
  private static final long HIGHEST_TRACKABLE_NANOS = TimeUnit.MINUTES.toNanos(1);
  private static final int SIGNIFICANT_DIGITS = 3;
  private static final double NANOS_PER_MILLI = 1_000_000.0;
  private static final Duration DRAIN_AT_MOST = Duration.ofSeconds(30);

  @Autowired private RestTestClient api;
  @Autowired private LinkRepository links;
  @Autowired private ClickEventRepository clickEvents;

  @Autowired
  @Qualifier(ClickRecorderExecutorConfig.EXECUTOR_BEAN)
  private ThreadPoolTaskExecutor clickRecorderExecutor;

  @LocalServerPort private int port;

  private RestTestClient redirects;
  private String code;

  @BeforeEach
  void createLinkAndClientThatDoesNotFollowRedirects() {
    HttpClient noFollow =
        HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    redirects =
        RestTestClient.bindToServer(new JdkClientHttpRequestFactory(noFollow))
            .baseUrl("http://localhost:" + port)
            .build();
    LinkResponse created =
        api.post()
            .uri("/api/v1/links")
            .header(TestApiKey.HEADER, TestApiKey.VALUE)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateLinkRequest(TARGET, null, null))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(LinkResponse.class)
            .returnResult()
            .getResponseBody();
    assertThat(created).isNotNull();
    code = created.code();
  }

  /** Lets the click recorder finish its queue before the rows it inserts into are removed. */
  @AfterEach
  void drainRecorderAndCleanTables() {
    await().atMost(DRAIN_AT_MOST).until(this::recorderIsIdle);
    clickEvents.deleteAllInBatch();
    links.deleteAllInBatch();
  }

  @Test
  @Timeout(value = 300, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SAME_THREAD)
  void get_twoThousandWarmRedirects_p99IsUnderTwoHundredMilliseconds() {
    redirect(WARM_UP_REQUESTS);

    Histogram measured = redirect(MEASURED_REQUESTS);

    double p99 = millis(measured.getValueAtPercentile(99.0));
    System.out.printf(
        Locale.ROOT,
        "redirect latency smoke, %d samples: p50 %.2f ms, p95 %.2f ms, p99 %.2f ms, max %.2f ms%n",
        measured.getTotalCount(),
        millis(measured.getValueAtPercentile(50.0)),
        millis(measured.getValueAtPercentile(95.0)),
        p99,
        millis(measured.getMaxValue()));
    System.out.printf(
        Locale.ROOT,
        "smoke threshold p99 < %.0f ms; one client, in-process server; not a capacity claim%n",
        P99_BUDGET_MILLIS);
    assertThat(p99).as("p99 redirect latency in milliseconds").isLessThan(P99_BUDGET_MILLIS);
  }

  private Histogram redirect(int count) {
    Histogram histogram = new Histogram(HIGHEST_TRACKABLE_NANOS, SIGNIFICANT_DIGITS);
    for (int i = 0; i < count; i++) {
      long started = System.nanoTime();
      ResponseSpec response = redirects.get().uri("/{code}", code).exchange();
      histogram.recordValue(System.nanoTime() - started);
      response.expectStatus().isFound().expectHeader().valueEquals(HttpHeaders.LOCATION, TARGET);
    }
    return histogram;
  }

  private boolean recorderIsIdle() {
    return clickRecorderExecutor.getThreadPoolExecutor().getQueue().isEmpty()
        && clickRecorderExecutor.getActiveCount() == 0;
  }

  private static double millis(long nanos) {
    return nanos / NANOS_PER_MILLI;
  }
}
