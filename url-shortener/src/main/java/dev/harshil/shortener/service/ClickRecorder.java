package dev.harshil.shortener.service;

import dev.harshil.shortener.config.ClickRecorderExecutorConfig;
import dev.harshil.shortener.domain.ClickEvent;
import dev.harshil.shortener.domain.ClickRecorded;
import dev.harshil.shortener.domain.LogSanitizer;
import dev.harshil.shortener.repository.ClickEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Persists {@link ClickRecorded} events off the request thread (design 3.6, ADR 0005).
 *
 * <p>The listener runs on the dedicated {@code clickRecorderExecutor}, so a slow or failing insert
 * can never delay or fail a redirect. Every failure is caught here, logged once with the request id
 * and counted under {@code shortener.clicks.dropped{reason=error}}; it is never rethrown, because
 * there is no caller left to inform and an exception on the executor thread would only produce an
 * uncounted stack trace. Each save runs in the repository's own transaction.
 */
@Component
public class ClickRecorder {

  /** Counter for clicks that were not persisted, tagged with the reason. */
  public static final String DROPPED_METRIC = "shortener.clicks.dropped";

  public static final String REASON_TAG = "reason";
  public static final String REASON_ERROR = "error";
  public static final String REASON_SATURATED = "saturated";

  private static final Logger log = LoggerFactory.getLogger(ClickRecorder.class);

  private final ClickEventRepository clickEvents;
  private final Counter droppedOnError;

  /**
   * Creates the recorder.
   *
   * @param clickEvents click persistence
   * @param registry metrics registry for the dropped counter
   */
  public ClickRecorder(ClickEventRepository clickEvents, MeterRegistry registry) {
    this.clickEvents = clickEvents;
    this.droppedOnError = registry.counter(DROPPED_METRIC, REASON_TAG, REASON_ERROR);
  }

  /**
   * Stores one click. Runs asynchronously on the click executor.
   *
   * @param event the redirect that was served
   */
  @Async(ClickRecorderExecutorConfig.EXECUTOR_BEAN)
  @EventListener
  public void on(ClickRecorded event) {
    try {
      clickEvents.save(new ClickEvent(event.linkId(), event.occurredAt(), event.attribution()));
    } catch (RuntimeException e) {
      droppedOnError.increment();
      log.error(
          "Click for link {} dropped; requestId={}",
          event.linkId(),
          LogSanitizer.sanitize(event.requestId()),
          e);
    }
  }
}
