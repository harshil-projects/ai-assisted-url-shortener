package dev.harshil.shortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.harshil.shortener.domain.ClickAttribution;
import dev.harshil.shortener.domain.ClickEvent;
import dev.harshil.shortener.domain.ClickRecorded;
import dev.harshil.shortener.repository.ClickEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class ClickRecorderTest {

  private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
  private static final ClickRecorded EVENT = new ClickRecorded(7L, NOW, "req-7");

  @Mock private ClickEventRepository clickEvents;

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private ClickRecorder recorder;

  @BeforeEach
  void createRecorder() {
    recorder = new ClickRecorder(clickEvents, registry);
  }

  @Test
  void on_event_savesOneClickEventCarryingLinkIdAndInstant() {
    recorder.on(EVENT);

    ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
    verify(clickEvents).save(saved.capture());
    assertThat(saved.getValue().getLinkId()).isEqualTo(7L);
    assertThat(saved.getValue().getOccurredAt()).isEqualTo(NOW);
  }

  @Test
  void on_eventWithAttribution_copiesEveryAttributionValueOntoTheClickEvent() {
    ClickAttribution attribution =
        new ClickAttribution("news.example.com", "newsletter", "email", "oct");

    recorder.on(new ClickRecorded(7L, NOW, "req-7", attribution));

    ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
    verify(clickEvents).save(saved.capture());
    assertThat(saved.getValue())
        .extracting(
            ClickEvent::getReferrerHost,
            ClickEvent::getUtmSource,
            ClickEvent::getUtmMedium,
            ClickEvent::getUtmCampaign)
        .containsExactly("news.example.com", "newsletter", "email", "oct");
  }

  @Test
  void on_eventWithoutAttribution_savesNullAttributionColumns() {
    recorder.on(EVENT);

    ArgumentCaptor<ClickEvent> saved = ArgumentCaptor.forClass(ClickEvent.class);
    verify(clickEvents).save(saved.capture());
    assertThat(saved.getValue())
        .extracting(
            ClickEvent::getReferrerHost,
            ClickEvent::getUtmSource,
            ClickEvent::getUtmMedium,
            ClickEvent::getUtmCampaign)
        .containsOnlyNulls();
  }

  @Test
  void on_successfulSave_doesNotCountADrop() {
    recorder.on(EVENT);

    assertThat(droppedForError()).isZero();
  }

  @Test
  void on_repositoryThrows_swallowsTheExceptionAndCountsOneDropWithReasonError() {
    when(clickEvents.save(any(ClickEvent.class)))
        .thenThrow(new DataAccessResourceFailureException("database gone"));

    assertThatCode(() -> recorder.on(EVENT)).doesNotThrowAnyException();

    assertThat(droppedForError()).isEqualTo(1.0);
  }

  @Test
  void on_repositoryThrowsTwice_countsEachDropSeparately() {
    when(clickEvents.save(any(ClickEvent.class))).thenThrow(new IllegalStateException("boom"));

    recorder.on(EVENT);
    recorder.on(new ClickRecorded(8L, NOW, null));

    assertThat(droppedForError()).isEqualTo(2.0);
  }

  private double droppedForError() {
    return registry
        .counter(ClickRecorder.DROPPED_METRIC, ClickRecorder.REASON_TAG, ClickRecorder.REASON_ERROR)
        .count();
  }
}
