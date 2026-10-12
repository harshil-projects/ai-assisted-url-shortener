package dev.harshil.shortener.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import dev.harshil.shortener.service.ClickRecorder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class ClickRecorderExecutorConfigTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final CountDownLatch gate = new CountDownLatch(1);
  private ThreadPoolTaskExecutor executor;

  @BeforeEach
  void createExecutor() {
    executor = new ClickRecorderExecutorConfig().clickRecorderExecutor(registry);
    executor.initialize();
  }

  @AfterEach
  void releaseAndShutDown() {
    gate.countDown();
    executor.shutdown();
  }

  @Test
  void clickRecorderExecutor_hasTheDesignedBounds() {
    assertThat(executor.getCorePoolSize()).isEqualTo(2);
    assertThat(executor.getMaxPoolSize()).isEqualTo(4);
    assertThat(executor.getQueueCapacity()).isEqualTo(1000);
    assertThat(executor.getThreadNamePrefix()).isEqualTo("click-recorder-");
  }

  @Test
  void clickRecorderExecutor_runsTasksOnThreadsCarryingThePrefix() throws Exception {
    CompletableFuture<String> threadName = new CompletableFuture<>();

    executor.execute(() -> threadName.complete(Thread.currentThread().getName()));

    assertThat(threadName.get(5, TimeUnit.SECONDS)).startsWith("click-recorder-");
  }

  @Test
  void clickRecorderExecutor_whenQueueAndThreadsAreFull_dropsCountsAndDoesNotBlock() {
    int accepted =
        ClickRecorderExecutorConfig.MAX_POOL_SIZE + ClickRecorderExecutorConfig.QUEUE_CAPACITY;
    for (int i = 0; i < accepted; i++) {
      executor.execute(this::blockUntilReleased);
    }
    assertThat(executor.getThreadPoolExecutor().getQueue())
        .hasSize(ClickRecorderExecutorConfig.QUEUE_CAPACITY);
    assertThat(droppedForSaturation()).isZero();

    assertTimeout(Duration.ofSeconds(2), () -> executor.execute(this::blockUntilReleased));

    assertThat(droppedForSaturation()).isEqualTo(1.0);
    assertThat(executor.getThreadPoolExecutor().getQueue())
        .hasSize(ClickRecorderExecutorConfig.QUEUE_CAPACITY);
  }

  private void blockUntilReleased() {
    try {
      gate.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private double droppedForSaturation() {
    return registry
        .counter(
            ClickRecorder.DROPPED_METRIC, ClickRecorder.REASON_TAG, ClickRecorder.REASON_SATURATED)
        .count();
  }
}
