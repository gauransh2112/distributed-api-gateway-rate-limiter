package com.gauransh.gateway.ratelimiter.concurrency;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Deterministic clock for concurrency tests.
 *
 * <p>Time advances only when a test explicitly advances it, so a concurrent burst observes a
 * single fixed instant. This removes wall-clock timing from the assertions: a result that depends
 * on how long the scheduler took to release the worker threads would not be a deterministic test.</p>
 *
 * <p>The instant is {@code volatile} because tests advance it from the main thread while worker
 * threads read it.</p>
 */
final class MutableTestClock extends Clock {

    private volatile Instant instant;
    private final ZoneId zone;

    MutableTestClock(Instant initial) {
        this(initial, ZoneOffset.UTC);
    }

    private MutableTestClock(Instant initial, ZoneId zone) {
        this.instant = initial;
        this.zone = zone;
    }

    void advance(java.time.Duration amount) {
        this.instant = this.instant.plus(amount);
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableTestClock(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
