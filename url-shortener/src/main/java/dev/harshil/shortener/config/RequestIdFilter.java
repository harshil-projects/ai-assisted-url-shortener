package dev.harshil.shortener.config;

import dev.harshil.shortener.api.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request a correlation id (design section 3.8).
 *
 * <p>A client may supply its own {@code X-Request-Id}. It is accepted only when it is 1 to 64
 * characters of {@code [A-Za-z0-9-]}; that rules out header injection and log forging (CWE-113,
 * CWE-117) before the value is echoed or written to the MDC. Anything else is replaced by a random
 * UUID. The id is put on the response before the chain runs so it is present on every response,
 * including rejections written by later filters, and it is removed from the MDC in {@code finally}
 * so a pooled thread never carries it into the next request.
 *
 * <p>Registered first through {@link FilterRegistrations}; deliberately not a Spring bean,
 * otherwise Boot would register it a second time.
 */
public final class RequestIdFilter extends OncePerRequestFilter {

  /** Request and response header carrying the correlation id. */
  public static final String HEADER = "X-Request-Id";

  private static final Pattern WELL_FORMED_CLIENT_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requestId = resolve(request.getHeader(HEADER));
    MDC.put(ProblemDetails.REQUEST_ID, requestId);
    response.setHeader(HEADER, requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      MDC.remove(ProblemDetails.REQUEST_ID);
    }
  }

  private static String resolve(String clientValue) {
    if (clientValue != null && WELL_FORMED_CLIENT_ID.matcher(clientValue).matches()) {
      return clientValue;
    }
    return UUID.randomUUID().toString();
  }
}
