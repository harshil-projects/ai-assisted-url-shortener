package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.harshil.shortener.api.ProblemWriter;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RequestSizeFilterTest {

  private static final int MAX_BYTES = 64;
  private static final byte[] SMALL_JSON =
      "{\"url\":\"https://example.com/a\"}".getBytes(StandardCharsets.UTF_8);

  private final RequestSizeFilter filter =
      new RequestSizeFilter(properties(MAX_BYTES), new ProblemWriter(JsonMapper.builder().build()));

  @Test
  void doFilter_declaredLengthOverLimit_returns413WithoutReadingTheBody() throws Exception {
    HttpServletRequest request = unreadable(apiPost(new byte[MAX_BYTES + 1]));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(response.getContentAsString())
        .contains("\"type\":\"/problems/payload-too-large\"")
        .contains("\"title\":\"Request body too large\"")
        .contains("\"status\":413")
        .contains("64 bytes");
  }

  @Test
  void doFilter_declaredLengthAtLimit_passesTheOriginalRequestThrough() throws Exception {
    MockHttpServletRequest request = apiPost(new byte[MAX_BYTES]);
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void doFilter_unknownLengthWithinLimit_replaysTheBodyThroughGetInputStream() throws Exception {
    AtomicReference<byte[]> seenByChain = new AtomicReference<>();

    filter.doFilter(
        unknownLength(apiPost(SMALL_JSON)),
        new MockHttpServletResponse(),
        (req, res) -> seenByChain.set(req.getInputStream().readAllBytes()));

    assertThat(seenByChain.get()).isEqualTo(SMALL_JSON);
  }

  @Test
  void doFilter_unknownLengthWithinLimit_replaysTheBodyThroughGetReaderInRequestCharset()
      throws Exception {
    MockHttpServletRequest request =
        apiPost("{\"url\":\"https://exämple.com\"}".getBytes(StandardCharsets.UTF_8));
    request.setCharacterEncoding(StandardCharsets.UTF_8.name());
    AtomicReference<String> seenByChain = new AtomicReference<>();

    filter.doFilter(
        unknownLength(request),
        new MockHttpServletResponse(),
        (req, res) -> seenByChain.set(req.getReader().readLine()));

    assertThat(seenByChain).hasValue("{\"url\":\"https://exämple.com\"}");
  }

  @Test
  void doFilter_unknownLengthAtLimit_isAccepted() throws Exception {
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(
        unknownLength(apiPost(new byte[MAX_BYTES])), new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(chain.getRequest().getContentLengthLong()).isEqualTo(MAX_BYTES);
  }

  @Test
  void doFilter_unknownLengthOverLimit_returns413() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(unknownLength(apiPost(new byte[MAX_BYTES + 1])), response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("\"type\":\"/problems/payload-too-large\"");
  }

  @Test
  void doFilter_outsideTheApiPrefix_isNotApplied() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/abcdefg");
    request.setContent(new byte[MAX_BYTES + 1]);
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isSameAs(request);
  }

  @Test
  void bufferedBodyRequest_getReaderAfterGetInputStream_isRejected() throws Exception {
    RequestSizeFilter.BufferedBodyRequest request =
        new RequestSizeFilter.BufferedBodyRequest(apiPost(SMALL_JSON), SMALL_JSON);
    ServletInputStream first = request.getInputStream();
    first.readAllBytes();

    // A second stream replays the buffer from the start; the reader stays off limits.
    assertThat(request.getInputStream().readAllBytes()).isEqualTo(SMALL_JSON);
    assertThatThrownBy(request::getReader).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void bufferedBodyRequest_getInputStreamAfterGetReader_isRejected() throws Exception {
    RequestSizeFilter.BufferedBodyRequest request =
        new RequestSizeFilter.BufferedBodyRequest(apiPost(SMALL_JSON), SMALL_JSON);
    BufferedReader first = request.getReader();

    assertThat(first.readLine()).isEqualTo(new String(SMALL_JSON, StandardCharsets.UTF_8));
    assertThatThrownBy(request::getInputStream).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void bufferedBodyRequest_withoutCharacterEncoding_readsUtf8() throws Exception {
    byte[] body = "ü".getBytes(StandardCharsets.UTF_8);
    RequestSizeFilter.BufferedBodyRequest request =
        new RequestSizeFilter.BufferedBodyRequest(apiPost(body), body);

    assertThat(request.getReader().readLine()).isEqualTo("ü");
    assertThat(request.getContentLength()).isEqualTo(body.length);
  }

  @Test
  void byteArrayServletInputStream_reportsReadinessAndCompletionAndRejectsAsyncReads()
      throws Exception {
    RequestSizeFilter.ByteArrayServletInputStream stream =
        new RequestSizeFilter.ByteArrayServletInputStream(new byte[] {1, 2});

    assertThat(stream.isReady()).isTrue();
    assertThat(stream.isFinished()).isFalse();
    assertThat(stream.available()).isEqualTo(2);
    assertThat(stream.read()).isEqualTo(1);
    assertThat(stream.read(new byte[4], 0, 4)).isEqualTo(1);
    assertThat(stream.isFinished()).isTrue();
    assertThatThrownBy(() -> stream.setReadListener(null))
        .isInstanceOf(IllegalStateException.class);
  }

  private static MockHttpServletRequest apiPost(byte[] body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/links");
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(body);
    return request;
  }

  /** Simulates a chunked request: the body is there but no length was declared. */
  private static HttpServletRequest unknownLength(HttpServletRequest request) {
    return new HttpServletRequestWrapper(request) {
      @Override
      public int getContentLength() {
        return -1;
      }

      @Override
      public long getContentLengthLong() {
        return -1;
      }
    };
  }

  /** Fails the test if anything touches the body. */
  private static HttpServletRequest unreadable(HttpServletRequest request) {
    return new HttpServletRequestWrapper(request) {
      @Override
      public ServletInputStream getInputStream() {
        throw new AssertionError("the body must not be read when Content-Length is over the limit");
      }

      @Override
      public BufferedReader getReader() {
        throw new AssertionError("the body must not be read when Content-Length is over the limit");
      }
    };
  }

  private static AppProperties properties(long maxBytes) {
    return new AppProperties(
        "key",
        null,
        new AppProperties.RateLimit(new AppProperties.RateLimit.Create(20, Duration.ofMinutes(1))),
        new AppProperties.Request(maxBytes),
        new AppProperties.Security(true));
  }
}
