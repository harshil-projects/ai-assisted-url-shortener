package dev.harshil.shortener.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.harshil.shortener.api.ProblemDetails;
import dev.harshil.shortener.api.ProblemTypes;
import dev.harshil.shortener.api.ProblemWriter;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Throttles {@code POST /api/v1/links} per client address (design 3.6, 3.7; FR-11, NFR-05).
 *
 * <p>One Bucket4j token bucket per {@code remoteAddr}, held in a bounded Caffeine cache so a client
 * rotating source addresses cannot grow memory without limit; idle buckets expire. The filter runs
 * before the Spring Security chain, so unauthenticated floods, including API key guessing, are
 * throttled too. Capacity and period come from {@code app.ratelimit.create.*}; the refill is greedy
 * (tokens trickle back continuously rather than all at once). Every handled request carries {@code
 * X-RateLimit-Limit} and {@code X-RateLimit-Remaining}; a rejected one gets a 429 Problem Details
 * body with {@code Retry-After} and {@code X-RateLimit-Reset}, both in whole seconds.
 *
 * <p>The client address is the socket peer because {@code server.forward-headers-strategy=NONE};
 * behind a trusted proxy that property, not this class, is what changes. Registered through {@link
 * FilterRegistrations}; deliberately not a Spring bean.
 */
public final class RateLimitFilter extends OncePerRequestFilter {

  static final String LIMIT_HEADER = "X-RateLimit-Limit";
  static final String REMAINING_HEADER = "X-RateLimit-Remaining";
  static final String RESET_HEADER = "X-RateLimit-Reset";

  private static final RequestMatcher CREATE_LINK =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/v1/links");
  private static final long MAX_TRACKED_CLIENTS = 10_000;
  private static final Duration IDLE_CLIENT_EXPIRY = Duration.ofMinutes(10);
  private static final long MIN_WAIT_SECONDS = 1;
  private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

  private final AppProperties.RateLimit.Create limit;
  private final ProblemWriter problemWriter;
  private final TimeMeter timeMeter;
  private final Cache<String, Bucket> bucketsByClient;

  public RateLimitFilter(AppProperties properties, ProblemWriter problemWriter) {
    this(properties.ratelimit().create(), problemWriter, TimeMeter.SYSTEM_MILLISECONDS);
  }

  /** Test seam: a controllable {@link TimeMeter} makes refill timing deterministic. */
  RateLimitFilter(
      AppProperties.RateLimit.Create limit, ProblemWriter problemWriter, TimeMeter timeMeter) {
    this.limit = limit;
    this.problemWriter = problemWriter;
    this.timeMeter = timeMeter;
    this.bucketsByClient =
        Caffeine.newBuilder()
            .maximumSize(MAX_TRACKED_CLIENTS)
            .expireAfterAccess(IDLE_CLIENT_EXPIRY)
            .build();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !CREATE_LINK.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Bucket bucket = bucketsByClient.get(request.getRemoteAddr(), address -> newBucket());
    ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
    response.setHeader(LIMIT_HEADER, Integer.toString(limit.capacity()));
    response.setHeader(REMAINING_HEADER, Long.toString(probe.getRemainingTokens()));
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }
    reject(response, probe);
  }

  private Bucket newBucket() {
    Bandwidth bandwidth =
        Bandwidth.builder()
            .capacity(limit.capacity())
            .refillGreedy(limit.capacity(), limit.period())
            .build();
    return Bucket.builder().addLimit(bandwidth).withCustomTimePrecision(timeMeter).build();
  }

  private void reject(HttpServletResponse response, ConsumptionProbe probe) throws IOException {
    long retryAfterSeconds = wholeSeconds(probe.getNanosToWaitForRefill());
    long resetSeconds = wholeSeconds(probe.getNanosToWaitForReset());
    problemWriter.write(
        response,
        ProblemDetails.of(
            HttpStatus.TOO_MANY_REQUESTS,
            ProblemTypes.RATE_LIMITED,
            "Too many requests",
            "The limit of "
                + limit.capacity()
                + " link creations per "
                + limit.period().toSeconds()
                + " seconds was exceeded; retry after "
                + retryAfterSeconds
                + " seconds."),
        Map.of(
            HttpHeaders.RETRY_AFTER,
            Long.toString(retryAfterSeconds),
            RESET_HEADER,
            Long.toString(resetSeconds)));
  }

  /** Rounds a wait up to whole seconds and never below one, as {@code Retry-After} requires. */
  private static long wholeSeconds(long nanos) {
    long roundedUp = (nanos + NANOS_PER_SECOND - 1) / NANOS_PER_SECOND;
    return Math.max(MIN_WAIT_SECONDS, roundedUp);
  }
}
