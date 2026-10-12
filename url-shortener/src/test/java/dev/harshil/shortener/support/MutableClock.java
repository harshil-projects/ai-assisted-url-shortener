package dev.harshil.shortener.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A UTC clock that stands still until a test moves it. Registered as the primary {@code Clock} bean
 * by {@code ExpiryIT} so create, redirect, metadata and stats all read one controllable "now",
 * which lets a test create a link and then step past its expiry without sleeping.
 */
public final class MutableClock extends Clock {

  private final AtomicReference<Instant> now;

  /**
   * Creates the clock.
   *
   * @param start the instant the clock reports until it is moved
   */
  public MutableClock(Instant start) {
    this.now = new AtomicReference<>(start);
  }

  /** Sets the instant the clock reports. */
  public void set(Instant instant) {
    now.set(instant);
  }

  /** Moves the clock forward by the given duration. */
  public void advance(Duration duration) {
    now.updateAndGet(current -> current.plus(duration));
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    throw new UnsupportedOperationException("The test clock is UTC only");
  }

  @Override
  public Instant instant() {
    return now.get();
  }
}
