package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.harshil.shortener.api.ProblemWriter;
import io.github.bucket4j.TimeMeter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

  private static final int CAPACITY = 2;
  private static final Duration PERIOD = Duration.ofMinutes(1);
  private static final String CLIENT_A = "10.0.0.1";
  private static final String CLIENT_B = "10.0.0.2";

  private final FakeTimeMeter clock = new FakeTimeMeter();
  private final ProblemWriter problemWriter = new ProblemWriter(JsonMapper.builder().build());
  private final RateLimitFilter filter =
      new RateLimitFilter(
          new AppProperties.RateLimit.Create(CAPACITY, PERIOD), problemWriter, clock);

  @Test
  void doFilter_withinCapacity_continuesAndReportsRemainingTokens() throws Exception {
    MockHttpServletResponse first = create(CLIENT_A);
    MockHttpServletResponse second = create(CLIENT_A);

    assertThat(first.getStatus()).isEqualTo(200);
    assertThat(first.getHeader(RateLimitFilter.LIMIT_HEADER)).isEqualTo("2");
    assertThat(first.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("1");
    assertThat(second.getStatus()).isEqualTo(200);
    assertThat(second.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("0");
  }

  @Test
  void doFilter_capacityExhausted_returns429ProblemWithRetryAfterAndReset() throws Exception {
    create(CLIENT_A);
    create(CLIENT_A);
    MockFilterChain chain = new MockFilterChain();

    MockHttpServletResponse rejected = create(CLIENT_A, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    // Greedy refill of 2 tokens per 60 s: one token every 30 s, a full bucket after 60 s.
    assertThat(rejected.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
    assertThat(rejected.getHeader(RateLimitFilter.RESET_HEADER)).isEqualTo("60");
    assertThat(rejected.getHeader(RateLimitFilter.LIMIT_HEADER)).isEqualTo("2");
    assertThat(rejected.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("0");
    assertThat(rejected.getContentAsString())
        .contains("\"type\":\"/problems/rate-limited\"")
        .contains("\"title\":\"Too many requests\"")
        .contains("\"status\":429")
        .contains("retry after 30 seconds");
  }

  @Test
  void doFilter_afterOneRefillInterval_acceptsExactlyOneMoreRequest() throws Exception {
    create(CLIENT_A);
    create(CLIENT_A);
    clock.advance(Duration.ofSeconds(30));

    MockHttpServletResponse refilled = create(CLIENT_A);
    MockHttpServletResponse rejected = create(CLIENT_A);

    assertThat(refilled.getStatus()).isEqualTo(200);
    assertThat(refilled.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("0");
    assertThat(rejected.getStatus()).isEqualTo(429);
  }

  @Test
  void doFilter_distinctClientAddresses_haveIndependentBuckets() throws Exception {
    create(CLIENT_A);
    create(CLIENT_A);

    MockHttpServletResponse otherClient = create(CLIENT_B);

    assertThat(otherClient.getStatus()).isEqualTo(200);
    assertThat(otherClient.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("1");
  }

  @Test
  void doFilter_subSecondRefill_roundsRetryAfterUpToOneSecond() throws Exception {
    RateLimitFilter fastFilter =
        new RateLimitFilter(
            new AppProperties.RateLimit.Create(CAPACITY, Duration.ofSeconds(1)),
            problemWriter,
            clock);
    fastFilter.doFilter(
        createRequest(CLIENT_A), new MockHttpServletResponse(), new MockFilterChain());
    fastFilter.doFilter(
        createRequest(CLIENT_A), new MockHttpServletResponse(), new MockFilterChain());
    MockHttpServletResponse rejected = new MockHttpServletResponse();

    fastFilter.doFilter(createRequest(CLIENT_A), rejected, new MockFilterChain());

    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    assertThat(rejected.getHeader(RateLimitFilter.RESET_HEADER)).isEqualTo("1");
  }

  @Test
  void doFilter_getOnTheCreatePath_isNotRateLimited() throws Exception {
    MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/links");
    get.setRemoteAddr(CLIENT_A);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(get, response, chain);

    assertThat(chain.getRequest()).isSameAs(get);
    assertThat(response.getHeader(RateLimitFilter.LIMIT_HEADER)).isNull();
  }

  @Test
  void doFilter_postOnAnotherApiPath_isNotRateLimited() throws Exception {
    MockHttpServletRequest post = new MockHttpServletRequest("POST", "/api/v1/links/abcdefg");
    post.setRemoteAddr(CLIENT_A);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(post, response, chain);

    assertThat(chain.getRequest()).isSameAs(post);
    assertThat(response.getHeader(RateLimitFilter.LIMIT_HEADER)).isNull();
  }

  @Test
  void publicConstructor_readsCapacityFromAppProperties() throws Exception {
    AppProperties properties =
        new AppProperties(
            "key",
            null,
            new AppProperties.RateLimit(new AppProperties.RateLimit.Create(7, PERIOD)),
            new AppProperties.Request(16_384),
            new AppProperties.Security(true));
    RateLimitFilter configured = new RateLimitFilter(properties, problemWriter);
    MockHttpServletResponse response = new MockHttpServletResponse();

    configured.doFilter(createRequest(CLIENT_A), response, new MockFilterChain());

    assertThat(response.getHeader(RateLimitFilter.LIMIT_HEADER)).isEqualTo("7");
    assertThat(response.getHeader(RateLimitFilter.REMAINING_HEADER)).isEqualTo("6");
  }

  private MockHttpServletResponse create(String clientAddress) throws Exception {
    return create(clientAddress, new MockFilterChain());
  }

  private MockHttpServletResponse create(String clientAddress, MockFilterChain chain)
      throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(createRequest(clientAddress), response, chain);
    return response;
  }

  private static MockHttpServletRequest createRequest(String clientAddress) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/links");
    request.setRemoteAddr(clientAddress);
    return request;
  }

  /** A clock the test moves by hand so refill arithmetic is exact. */
  private static final class FakeTimeMeter implements TimeMeter {

    private long nanos;

    void advance(Duration duration) {
      nanos += duration.toNanos();
    }

    @Override
    public long currentTimeNanos() {
      return nanos;
    }

    @Override
    public boolean isWallClockBased() {
      return false;
    }
  }
}
