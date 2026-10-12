package dev.harshil.shortener.config;

import dev.harshil.shortener.api.ProblemDetails;
import dev.harshil.shortener.api.ProblemTypes;
import dev.harshil.shortener.api.ProblemWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Caps request bodies on the management API at {@code app.request.max-bytes} (design 3.6, 3.7).
 *
 * <p>Two paths. When the client declares {@code Content-Length} the decision is taken from the
 * header alone and an oversized body is never read. When the length is unknown (chunked transfer)
 * the body is read up to one byte past the limit, so a client cannot stream an unbounded body past
 * the check, and an accepted body is replayed to the rest of the chain through a wrapper. The 413
 * is written through {@link ProblemWriter} so it has the same shape as every other error.
 *
 * <p>Registered through {@link FilterRegistrations} ahead of the rate limiter and the security
 * chain; deliberately not a Spring bean.
 */
public final class RequestSizeFilter extends OncePerRequestFilter {

  private static final RequestMatcher API_PATHS =
      PathPatternRequestMatcher.withDefaults().matcher("/api/v1/**");

  /** What the servlet API reports when no {@code Content-Length} header is present. */
  private static final long UNKNOWN_LENGTH = -1;

  private final long maxBytes;
  private final ProblemWriter problemWriter;

  public RequestSizeFilter(AppProperties properties, ProblemWriter problemWriter) {
    this.maxBytes = properties.request().maxBytes();
    this.problemWriter = problemWriter;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !API_PATHS.matches(request);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long declaredLength = request.getContentLengthLong();
    if (declaredLength > maxBytes) {
      reject(response);
      return;
    }
    if (declaredLength != UNKNOWN_LENGTH) {
      chain.doFilter(request, response);
      return;
    }
    byte[] body = request.getInputStream().readNBytes(limitPlusOne());
    if (body.length > maxBytes) {
      reject(response);
      return;
    }
    chain.doFilter(new BufferedBodyRequest(request, body), response);
  }

  /** One byte more than the limit, so the over-limit check is decisive; clamped to an int. */
  private int limitPlusOne() {
    return (int) Math.min(maxBytes + 1, Integer.MAX_VALUE);
  }

  private void reject(HttpServletResponse response) throws IOException {
    problemWriter.write(
        response,
        ProblemDetails.of(
            HttpStatus.CONTENT_TOO_LARGE,
            ProblemTypes.PAYLOAD_TOO_LARGE,
            "Request body too large",
            "The request body must not exceed " + maxBytes + " bytes."));
  }

  /**
   * Replays a fully buffered body. Honours the servlet contract that a request exposes either its
   * input stream or its reader, never both, and reports the buffered size as the content length so
   * downstream code sees a request with a known body.
   */
  static final class BufferedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;
    private boolean streamOpened;
    private boolean readerOpened;

    BufferedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    /** Each call starts a fresh read of the buffer, as Spring's own caching wrapper does. */
    @Override
    public ServletInputStream getInputStream() {
      if (readerOpened) {
        throw new IllegalStateException("getReader() has already been called on this request");
      }
      streamOpened = true;
      return new ByteArrayServletInputStream(body);
    }

    @Override
    public BufferedReader getReader() throws IOException {
      if (streamOpened) {
        throw new IllegalStateException("getInputStream() has already been called on this request");
      }
      readerOpened = true;
      return new BufferedReader(newBodyReader());
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }

    /** Decodes with the request's charset; UTF-8 when none is named, as JSON is decoded. */
    private InputStreamReader newBodyReader() throws IOException {
      String encoding = getCharacterEncoding();
      if (encoding == null) {
        return new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8);
      }
      return new InputStreamReader(new ByteArrayInputStream(body), encoding);
    }
  }

  /** Blocking {@link ServletInputStream} over an in-memory buffer; no async read support. */
  static final class ByteArrayServletInputStream extends ServletInputStream {

    private final ByteArrayInputStream delegate;

    ByteArrayServletInputStream(byte[] body) {
      this.delegate = new ByteArrayInputStream(body);
    }

    @Override
    public int read() {
      return delegate.read();
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      return delegate.read(buffer, offset, length);
    }

    @Override
    public int available() {
      return delegate.available();
    }

    @Override
    public boolean isFinished() {
      return delegate.available() == 0;
    }

    @Override
    public boolean isReady() {
      return true;
    }

    @Override
    public void setReadListener(ReadListener listener) {
      throw new IllegalStateException("Buffered request bodies do not support asynchronous reads");
    }
  }
}
