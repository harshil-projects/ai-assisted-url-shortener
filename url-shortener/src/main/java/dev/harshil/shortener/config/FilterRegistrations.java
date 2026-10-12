package dev.harshil.shortener.config;

import dev.harshil.shortener.api.ProblemWriter;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the three request filters exactly once and in a fixed order (design 3.6, 3.7).
 *
 * <p>The filters are plain classes, not {@code @Component}s: Boot registers every {@code Filter}
 * bean it finds, so a filter that is both a bean and registered here would run twice. Explicit
 * registration also makes the order a reviewable constant instead of an annotation side effect:
 *
 * <ol>
 *   <li>{@link RequestIdFilter} first, so every later response, including rejections, carries the
 *       correlation id;
 *   <li>{@link RequestSizeFilter}, so an oversized body is refused before anything reads it;
 *   <li>{@link RateLimitFilter}, last before Spring Security's chain, so unauthenticated floods and
 *       key guessing are throttled too.
 * </ol>
 */
@Configuration(proxyBeanMethods = false)
public class FilterRegistrations {

  /** Runs after the request id filter and before the rate limiter. */
  static final int REQUEST_SIZE_ORDER = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 20;

  /** The last filter before the Spring Security chain. */
  static final int RATE_LIMIT_ORDER = SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10;

  @Bean
  FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
    FilterRegistrationBean<RequestIdFilter> registration =
        new FilterRegistrationBean<>(new RequestIdFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
  }

  @Bean
  FilterRegistrationBean<RequestSizeFilter> requestSizeFilterRegistration(
      AppProperties properties, ProblemWriter problemWriter) {
    FilterRegistrationBean<RequestSizeFilter> registration =
        new FilterRegistrationBean<>(new RequestSizeFilter(properties, problemWriter));
    registration.setOrder(REQUEST_SIZE_ORDER);
    return registration;
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
      AppProperties properties, ProblemWriter problemWriter) {
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(new RateLimitFilter(properties, problemWriter));
    registration.setOrder(RATE_LIMIT_ORDER);
    return registration;
  }
}
