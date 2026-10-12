package dev.harshil.shortener.config;

import dev.harshil.shortener.api.ProblemDetails;
import dev.harshil.shortener.api.ProblemTypes;
import dev.harshil.shortener.api.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates management requests by a shared API key in the {@code X-API-Key} header.
 *
 * <p>Constructed by {@link SecurityConfig} and inserted into the security chain; it is deliberately
 * not a Spring bean, otherwise Boot would also register it as a servlet filter and it would run
 * twice. A missing header leaves the request anonymous so the authorization rules decide; a wrong
 * key is rejected immediately. The comparison is constant-time (ADR 0007).
 */
final class ApiKeyFilter extends OncePerRequestFilter {

  static final String HEADER = "X-API-Key";
  static final String CHALLENGE = "ApiKey realm=\"url-shortener\"";

  private static final RequestMatcher PROTECTED_PATHS =
      new OrRequestMatcher(
          PathPatternRequestMatcher.withDefaults().matcher("/api/**"),
          PathPatternRequestMatcher.withDefaults().matcher("/actuator/**"));

  private final byte[] expectedKey;
  private final ProblemWriter problemWriter;

  ApiKeyFilter(String expectedKey, ProblemWriter problemWriter) {
    this.expectedKey = expectedKey.getBytes(StandardCharsets.UTF_8);
    this.problemWriter = problemWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !PROTECTED_PATHS.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String presented = request.getHeader(HEADER);
    if (presented == null) {
      chain.doFilter(request, response);
      return;
    }
    if (!MessageDigest.isEqual(expectedKey, presented.getBytes(StandardCharsets.UTF_8))) {
      reject(response);
      return;
    }
    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(apiClient());
    SecurityContextHolder.setContext(context);
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  /**
   * Writes the 401 Problem Details body with a {@code WWW-Authenticate} challenge. Also used as the
   * authentication entry point for anonymous requests to protected paths, so both paths produce an
   * identical response.
   *
   * @param response the response to reject
   * @throws IOException if the response cannot be written
   */
  void reject(HttpServletResponse response) throws IOException {
    problemWriter.write(
        response,
        ProblemDetails.of(
            HttpStatus.UNAUTHORIZED,
            ProblemTypes.UNAUTHORIZED,
            "Missing or invalid API key",
            "Provide a valid X-API-Key header."),
        Map.of(HttpHeaders.WWW_AUTHENTICATE, CHALLENGE));
  }

  private static Authentication apiClient() {
    return new PreAuthenticatedAuthenticationToken(
        "api-client", "", AuthorityUtils.createAuthorityList("ROLE_API_CLIENT"));
  }
}
