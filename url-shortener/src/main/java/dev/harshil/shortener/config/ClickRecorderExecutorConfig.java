package dev.harshil.shortener.config;

import dev.harshil.shortener.service.ClickRecorder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The bounded executor that records clicks (design 3.6, 3.10).
 *
 * <p>Sizing is deliberate and fixed in code rather than in properties: two core threads cover the
 * steady state, four is the ceiling so the recorder never holds more than four of the ten pooled
 * database connections, and a queue of a thousand absorbs bursts. When even the queue is full the
 * rejection handler drops the click and increments {@code
 * shortener.clicks.dropped{reason=saturated}} instead of blocking the request thread or throwing
 * into it. On shutdown the executor drains for up to ten seconds, inside Boot's twenty-second
 * graceful window, so in-flight clicks are kept.
 *
 * <p>{@code defaultCandidate = false} keeps this executor out of by-type autowiring, so Boot's
 * {@code applicationTaskExecutor} remains the default for everything else; the recorder selects it
 * by name.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class ClickRecorderExecutorConfig {

  /** Bean name the recorder's {@code @Async} refers to. */
  public static final String EXECUTOR_BEAN = "clickRecorderExecutor";

  static final int CORE_POOL_SIZE = 2;
  static final int MAX_POOL_SIZE = 4;
  static final int QUEUE_CAPACITY = 1000;
  static final int AWAIT_TERMINATION_SECONDS = 10;
  static final String THREAD_NAME_PREFIX = "click-recorder-";

  /**
   * Builds the executor; Spring initialises it through {@code afterPropertiesSet}.
   *
   * @param registry metrics registry for the saturation counter
   * @return the configured, not yet initialised, executor
   */
  @Bean(name = EXECUTOR_BEAN, defaultCandidate = false)
  ThreadPoolTaskExecutor clickRecorderExecutor(MeterRegistry registry) {
    Counter droppedOnSaturation =
        registry.counter(
            ClickRecorder.DROPPED_METRIC, ClickRecorder.REASON_TAG, ClickRecorder.REASON_SATURATED);
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(CORE_POOL_SIZE);
    executor.setMaxPoolSize(MAX_POOL_SIZE);
    executor.setQueueCapacity(QUEUE_CAPACITY);
    executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(AWAIT_TERMINATION_SECONDS);
    executor.setRejectedExecutionHandler((task, pool) -> droppedOnSaturation.increment());
    return executor;
  }
}
