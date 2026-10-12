package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class ProblemDetailsTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void of_populatesTypeTitleStatusDetailRequestIdAndTimestamp() {
    MDC.put(ProblemDetails.REQUEST_ID, "req-123");

    ProblemDetail problem =
        ProblemDetails.of(
            HttpStatus.NOT_FOUND, ProblemTypes.LINK_NOT_FOUND, "Link not found", "No link abc");

    assertThat(problem.getStatus()).isEqualTo(404);
    assertThat(problem.getType()).isEqualTo(URI.create("/problems/link-not-found"));
    assertThat(problem.getTitle()).isEqualTo("Link not found");
    assertThat(problem.getDetail()).isEqualTo("No link abc");
    assertThat(problem.getProperties()).containsEntry(ProblemDetails.REQUEST_ID, "req-123");
    Object timestamp = problem.getProperties().get(ProblemDetails.TIMESTAMP);
    assertThat(Instant.parse(timestamp.toString())).isBeforeOrEqualTo(Instant.now());
  }

  @Test
  void of_withoutRequestIdInMdc_leavesRequestIdNull() {
    ProblemDetail problem =
        ProblemDetails.of(HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED, "Unauthorized", "x");

    assertThat(problem.getProperties()).containsEntry(ProblemDetails.REQUEST_ID, null);
  }

  @Test
  void toMap_keepsRfc9457MemberOrderAndPromotesProperties() {
    MDC.put(ProblemDetails.REQUEST_ID, "req-9");
    ProblemDetail problem =
        ProblemDetails.of(HttpStatus.CONFLICT, ProblemTypes.ALIAS_TAKEN, "Alias taken", "demo");
    problem.setInstance(URI.create("/api/v1/links"));

    Map<String, Object> body = ProblemDetails.toMap(problem);

    assertThat(body.keySet())
        .containsExactly("type", "title", "status", "detail", "instance", "requestId", "timestamp");
    assertThat(body)
        .containsEntry("type", "/problems/alias-taken")
        .containsEntry("status", 409)
        .containsEntry("instance", "/api/v1/links")
        .containsEntry("requestId", "req-9");
  }

  @Test
  void toMap_omitsAbsentOptionalMembers() {
    ProblemDetail bare = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);

    Map<String, Object> body = ProblemDetails.toMap(bare);

    // Spring fills title with the reason phrase; detail, instance and properties stay absent.
    assertThat(body.keySet()).containsExactly("type", "title", "status");
    assertThat(body)
        .containsEntry("type", "about:blank")
        .containsEntry("title", "Internal Server Error")
        .containsEntry("status", 500);
  }
}
