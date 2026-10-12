package dev.harshil.shortener.api;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 {@link ProblemDetail} bodies with the two fields every error carries: {@code
 * requestId} and {@code timestamp}. Used by the controller advice, the security entry point and the
 * filters so all error bodies share one shape (design section 3.4).
 */
public final class ProblemDetails {

  /** JSON member carrying the correlation id; also the MDC key written by RequestIdFilter. */
  public static final String REQUEST_ID = "requestId";

  public static final String TIMESTAMP = "timestamp";

  private ProblemDetails() {}

  /**
   * Creates a problem with the given status, type, title and human-readable detail.
   *
   * @param status HTTP status
   * @param type problem type from {@link ProblemTypes}
   * @param title short, stable title
   * @param detail safe, user-facing explanation; never a stack trace or internal message
   * @return a populated problem detail
   */
  public static ProblemDetail of(HttpStatusCode status, URI type, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(type);
    problem.setTitle(title);
    problem.setProperty(REQUEST_ID, MDC.get(REQUEST_ID));
    problem.setProperty(TIMESTAMP, Instant.now().toString());
    return problem;
  }

  /**
   * Flattens a problem into the JSON member order defined by RFC 9457, with custom properties
   * promoted to top-level members. Filters serialise this map directly so the output does not
   * depend on message-converter configuration.
   *
   * @param problem the problem to flatten
   * @return an insertion-ordered map ready for JSON serialisation
   */
  public static Map<String, Object> toMap(ProblemDetail problem) {
    Map<String, Object> body = new LinkedHashMap<>();
    // Spring leaves type null on a bare ProblemDetail; RFC 9457 says absent means about:blank.
    URI type = problem.getType();
    body.put("type", type == null ? ProblemTypes.ABOUT_BLANK.toString() : type.toString());
    String title = problem.getTitle();
    if (title != null) {
      body.put("title", title);
    }
    body.put("status", problem.getStatus());
    String detail = problem.getDetail();
    if (detail != null) {
      body.put("detail", detail);
    }
    URI instance = problem.getInstance();
    if (instance != null) {
      body.put("instance", instance.toString());
    }
    Map<String, Object> properties = problem.getProperties();
    if (properties != null) {
      body.putAll(properties);
    }
    return body;
  }
}
