package dev.harshil.shortener.config;

import dev.harshil.shortener.api.ProblemWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * HTTP security for the service (design section 3.7, ADR 0007).
 *
 * <ul>
 *   <li>Stateless: no sessions, no cookies, no form or basic login. CSRF protection is disabled
 *       because the API is authenticated by a custom header that browsers never attach
 *       automatically, so cross-site request forgery cannot carry credentials.
 *   <li>{@code /api/**} requires a valid {@code X-API-Key}; health probes and redirects are public;
 *       the other actuator endpoints require the key when {@code
 *       app.security.actuator-requires-api-key} is true (prod) and are open in dev.
 *   <li>Spring Security's default headers apply everywhere (no-store caching, nosniff, frame
 *       denial, HSTS on HTTPS) plus {@code Referrer-Policy: no-referrer}. Swagger UI gets its own
 *       Content-Security-Policy because the strict default would blank the page.
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
@Import(ProblemWriter.class)
public class SecurityConfig {

  private static final String[] API_DOCS_PATHS = {
    "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml"
  };

  private static final String API_DOCS_CSP =
      "default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
          + "frame-ancestors 'none'; object-src 'none'";

  /**
   * Dev only: the H2 console renders itself in frames and posts forms, so it needs its own chain.
   */
  @Bean
  @Order(0)
  @ConditionalOnProperty(name = "spring.h2.console.enabled", havingValue = "true")
  SecurityFilterChain h2ConsoleChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/h2-console/**")
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
    return http.build();
  }

  /** Public API documentation with a Content-Security-Policy tuned for Swagger UI. */
  @Bean
  @Order(1)
  SecurityFilterChain apiDocsChain(HttpSecurity http) throws Exception {
    http.securityMatcher(API_DOCS_PATHS)
        .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(API_DOCS_CSP))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
    return http.build();
  }

  /** Everything else: API-key protected management API, public redirects and health. */
  @Bean
  @Order(2)
  SecurityFilterChain apiChain(
      HttpSecurity http, AppProperties properties, ProblemWriter problemWriter) throws Exception {
    ApiKeyFilter apiKeyFilter = new ApiKeyFilter(properties.apiKey(), problemWriter);
    http.authorizeHttpRequests(auth -> authorize(auth, properties))
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .requestCache(cache -> cache.disable())
        .exceptionHandling(
            handling ->
                handling.authenticationEntryPoint(
                    (request, response, exception) -> apiKeyFilter.reject(response)))
        .headers(
            headers ->
                headers.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
        .addFilterBefore(apiKeyFilter, AuthorizationFilter.class);
    return http.build();
  }

  private static void authorize(
      AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth,
      AppProperties properties) {
    auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
    if (properties.security().actuatorRequiresApiKey()) {
      auth.requestMatchers("/actuator/**").authenticated();
    } else {
      auth.requestMatchers("/actuator/**").permitAll();
    }
    auth.requestMatchers("/api/**").authenticated();
    auth.anyRequest().permitAll();
  }
}
