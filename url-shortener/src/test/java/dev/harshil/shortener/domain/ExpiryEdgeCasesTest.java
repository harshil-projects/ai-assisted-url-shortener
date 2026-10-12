package dev.harshil.shortener.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Regression table for the expiry boundary (task T-103, design 3.6). The rule under test is
 * "expired when {@code expiresAt <= now}": the boundary instant itself is expired, one second later
 * on the {@code expiresAt} side is not, {@code null} never expires, and the comparison is on the
 * absolute time line so offsets and daylight-saving transitions cannot move it.
 */
class ExpiryEdgeCasesTest {

  private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");
  private static final Instant CREATED = NOW.minusSeconds(3600);

  @ParameterizedTest(name = "{0}")
  @MethodSource("boundaryCases")
  void isExpired_atTheFixedInstant_followsExpiresAtNotAfterNow(
      String name, Instant expiresAt, boolean expected) {
    Link link = new Link("edge001", "https://example.com/edge", CREATED, expiresAt);

    assertThat(link.isExpired(NOW)).as(name).isEqualTo(expected);
  }

  static Stream<Arguments> boundaryCases() {
    return Stream.of(
        Arguments.of(
            "expiresAt exactly equal to now is expired (boundary is inclusive)", NOW, true),
        Arguments.of("expiresAt one second before now is expired", NOW.minusSeconds(1), true),
        Arguments.of("expiresAt one second after now is not expired", NOW.plusSeconds(1), false),
        Arguments.of("expiresAt one nanosecond after now is not expired", NOW.plusNanos(1), false),
        Arguments.of("null expiresAt never expires", null, false),
        Arguments.of(
            "far-future expiresAt is not expired", Instant.parse("9999-12-31T23:59:59Z"), false),
        Arguments.of("far-past expiresAt is expired", Instant.parse("1970-01-01T00:00:00Z"), true));
  }

  @Test
  void isExpired_nullExpiry_neverExpiresEvenAtTheEndOfTime() {
    Link link = new Link("forever", "https://example.com/forever", CREATED, null);

    assertThat(link.isExpired(Instant.MAX)).isFalse();
  }

  @Test
  void isExpired_farFutureExpiry_staysLiveForDecadesOfClock() {
    Link link =
        new Link(
            "decades",
            "https://example.com/decades",
            CREATED,
            Instant.parse("2126-10-12T10:00:00Z"));

    assertThat(link.isExpired(Instant.parse("2126-10-12T09:59:59Z"))).isFalse();
    assertThat(link.isExpired(Instant.parse("2126-10-12T10:00:00Z"))).isTrue();
  }

  @Test
  void isExpired_sameWallClockAcrossADaylightSavingFoldInBerlin_comparesAbsoluteInstants() {
    // On 2026-10-25 Europe/Berlin repeats 02:30 local time: first at +02:00, then at +01:00.
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    ZonedDateTime firstPass =
        ZonedDateTime.of(2026, 10, 25, 2, 30, 0, 0, berlin).withEarlierOffsetAtOverlap();
    ZonedDateTime secondPass = firstPass.withLaterOffsetAtOverlap();
    assertThat(firstPass.toLocalDateTime()).isEqualTo(secondPass.toLocalDateTime());
    Link link = new Link("dstfold", "https://example.com/dst", CREATED, firstPass.toInstant());

    assertThat(link.isExpired(firstPass.toInstant())).isTrue();
    assertThat(link.isExpired(secondPass.toInstant())).isTrue();
    assertThat(link.isExpired(firstPass.minusSeconds(1).toInstant())).isFalse();
    assertThat(secondPass.toInstant()).isEqualTo(firstPass.toInstant().plusSeconds(3600));
  }

  @Test
  void isExpired_expiryGivenWithAnOffset_isTheSameInstantAsItsUtcForm() {
    Instant fromOffset = OffsetDateTime.parse("2026-10-12T15:30:00+05:30").toInstant();
    Link link = new Link("offset1", "https://example.com/offset", CREATED, fromOffset);

    assertThat(fromOffset).isEqualTo(NOW);
    assertThat(link.isExpired(NOW)).isTrue();
    assertThat(link.isExpired(NOW.minusSeconds(1))).isFalse();
  }
}
