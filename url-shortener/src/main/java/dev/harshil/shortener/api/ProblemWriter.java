package dev.harshil.shortener.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a {@link ProblemDetail} straight to the servlet response. Filters and the authentication
 * entry point run before Spring MVC's message converters, so they need this to produce the same
 * {@code application/problem+json} body as the controller advice.
 */
@Component
public class ProblemWriter {

  /** The hardening headers Spring Security's header writer adds to every response (design 3.3). */
  private static final Map<String, String> SECURITY_HEADERS =
      Map.of(
          "X-Content-Type-Options",
          "nosniff",
          "X-Frame-Options",
          "DENY",
          "Referrer-Policy",
          "no-referrer",
          HttpHeaders.CACHE_CONTROL,
          "no-store");

  private final JsonMapper jsonMapper;

  /**
   * Creates a writer that serialises with the application's Jackson 3 mapper.
   *
   * @param jsonMapper the Boot-configured {@link JsonMapper}
   */
  public ProblemWriter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  /**
   * Writes the problem with no extra headers.
   *
   * @param response the response to write to; ignored if already committed
   * @param problem the problem body
   * @throws IOException if the response stream fails
   */
  public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    write(response, problem, Map.of());
  }

  /**
   * Writes the problem and the given headers, for example {@code Retry-After} or {@code
   * WWW-Authenticate}.
   *
   * <p>Also sets {@code X-Content-Type-Options}, {@code X-Frame-Options}, {@code Referrer-Policy}
   * and {@code Cache-Control: no-store} when absent, because {@code RequestSizeFilter} and {@code
   * RateLimitFilter} run before Spring Security's header writer, so their 413 and 429 responses
   * would otherwise go out without them.
   *
   * @param response the response to write to; ignored if already committed
   * @param problem the problem body
   * @param headers headers to set before the body
   * @throws IOException if the response stream fails
   */
  public void write(
      HttpServletResponse response, ProblemDetail problem, Map<String, String> headers)
      throws IOException {
    if (response.isCommitted()) {
      return;
    }
    response.resetBuffer();
    response.setStatus(problem.getStatus());
    headers.forEach(response::setHeader);
    SECURITY_HEADERS.forEach((name, value) -> setWhenAbsent(response, name, value));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    jsonMapper.writeValue(response.getOutputStream(), ProblemDetails.toMap(problem));
    response.flushBuffer();
  }

  private static void setWhenAbsent(HttpServletResponse response, String name, String value) {
    if (!response.containsHeader(name)) {
      response.setHeader(name, value);
    }
  }
}
