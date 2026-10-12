package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harshil.shortener.api.ProblemDetails;
import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private static final String UUID_PATTERN =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private final RequestIdFilter filter = new RequestIdFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  static Stream<String> wellFormedIds() {
    return Stream.of("1", "client-ID-0042", "a".repeat(64));
  }

  static Stream<String> malformedIds() {
    return Stream.of(
        "", "has space", "with_underscore", "with/slash", "crlf\r\nX-Injected: 1", "a".repeat(65));
  }

  @ParameterizedTest
  @MethodSource("wellFormedIds")
  void doFilter_wellFormedClientId_isEchoedOnTheResponse(String clientId) throws Exception {
    MockHttpServletRequest request = request(clientId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(clientId);
  }

  @ParameterizedTest
  @MethodSource("malformedIds")
  void doFilter_malformedClientId_isReplacedByGeneratedUuid(String clientId) throws Exception {
    MockHttpServletRequest request = request(clientId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getHeader(RequestIdFilter.HEADER))
        .isNotEqualTo(clientId)
        .matches(UUID_PATTERN);
  }

  @Test
  void doFilter_noClientId_generatesUuid() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(new MockHttpServletRequest("GET", "/abcdefg"), response, new MockFilterChain());

    assertThat(response.getHeader(RequestIdFilter.HEADER)).matches(UUID_PATTERN);
  }

  @Test
  void doFilter_duringTheChain_exposesTheIdInMdcUnderTheProblemDetailsKey() throws Exception {
    AtomicReference<String> seenByChain = new AtomicReference<>();

    filter.doFilter(
        request("trace-7"),
        new MockHttpServletResponse(),
        (req, res) -> seenByChain.set(MDC.get(ProblemDetails.REQUEST_ID)));

    assertThat(seenByChain).hasValue("trace-7");
  }

  @Test
  void doFilter_afterTheChain_removesTheIdFromMdc() throws Exception {
    filter.doFilter(request("trace-8"), new MockHttpServletResponse(), new MockFilterChain());

    assertThat(MDC.get(ProblemDetails.REQUEST_ID)).isNull();
  }

  @Test
  void doFilter_whenTheChainThrows_stillRemovesTheIdFromMdc() {
    MockHttpServletRequest request = request("trace-9");
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThatThrownBy(
            () ->
                filter.doFilter(
                    request,
                    response,
                    (req, res) -> {
                      throw new ServletException("downstream failure");
                    }))
        .isInstanceOf(ServletException.class);

    assertThat(MDC.get(ProblemDetails.REQUEST_ID)).isNull();
    assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("trace-9");
  }

  private static MockHttpServletRequest request(String clientId) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/abcdefg");
    request.addHeader(RequestIdFilter.HEADER, clientId);
    return request;
  }
}
