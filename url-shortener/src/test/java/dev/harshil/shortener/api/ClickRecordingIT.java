package dev.harshil.shortener.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import dev.harshil.shortener.domain.ClickEvent;
import dev.harshil.shortener.domain.Link;
import dev.harshil.shortener.repository.ClickEventRepository;
import dev.harshil.shortener.repository.LinkRepository;
import dev.harshil.shortener.service.ClickRecorder;
import dev.harshil.shortener.support.TestProfile;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Click recording end to end: the redirect returns first, the click lands later on the recorder
 * executor, and a failing insert never reaches the client.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@TestProfile
class ClickRecordingIT {

  private static final Duration AT_MOST = Duration.ofSeconds(5);
  private static final Duration SETTLE = Duration.ofMillis(500);

  @Autowired private RestTestClient client;
  @Autowired private LinkRepository links;
  @Autowired private MeterRegistry registry;
  @MockitoSpyBean private ClickEventRepository clickEvents;

  @AfterEach
  void cleanTables() {
    clickEvents.deleteAllInBatch();
    links.deleteAllInBatch();
  }

  @Test
  void get_redirect_recordsExactlyOneClickForTheLinkAsynchronously() {
    Link link = links.save(new Link("click01", "https://example.com/c", Instant.now()));

    client.get().uri("/click01").exchange().expectStatus().isFound();

    await().atMost(AT_MOST).untilAsserted(() -> assertThat(clickEvents.count()).isEqualTo(1));
    ClickEvent recorded = clickEvents.findAll().get(0);
    assertThat(recorded.getLinkId()).isEqualTo(link.getId());
    assertThat(recorded.getOccurredAt()).isNotNull();
  }

  @Test
  void head_redirect_recordsNoClick() {
    links.save(new Link("head001", "https://example.com/h", Instant.now()));

    client.head().uri("/head001").exchange().expectStatus().isFound();
    client.get().uri("/head001").exchange().expectStatus().isFound();

    await().atMost(AT_MOST).untilAsserted(() -> assertThat(clickEvents.count()).isEqualTo(1));
    await().during(SETTLE).atMost(AT_MOST).until(() -> clickEvents.count() == 1);
  }

  @Test
  void get_redirect_whenTheInsertFails_stillReturns302AndCountsTheDrop() {
    links.save(new Link("fail001", "https://example.com/f", Instant.now()));
    doThrow(new DataAccessResourceFailureException("database gone"))
        .when(clickEvents)
        .save(any(ClickEvent.class));
    double droppedBefore = droppedForError();

    client.get().uri("/fail001").exchange().expectStatus().isFound();

    await()
        .atMost(AT_MOST)
        .untilAsserted(() -> assertThat(droppedForError()).isEqualTo(droppedBefore + 1));
    assertThat(clickEvents.count()).isZero();
  }

  private double droppedForError() {
    return registry
        .counter(ClickRecorder.DROPPED_METRIC, ClickRecorder.REASON_TAG, ClickRecorder.REASON_ERROR)
        .count();
  }
}
