package com.gauransh.gateway.observability.metrics;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe sliding-window counter storing traffic statistics across fixed time buckets.
 *
 * <p>Enables accurate computation of traffic statistics for periods such as 1m, 5m, 15m, and 1h
 * with bounded memory (360 10-second buckets = 1 hour maximum history).</p>
 */
public class RollingTrafficWindow {

    private static final int BUCKET_SIZE_SECONDS = 10;
    private static final int NUM_BUCKETS = 360; // 360 * 10s = 3600s = 1 hour

    private final Bucket[] buckets;

    public RollingTrafficWindow() {
        this.buckets = new Bucket[NUM_BUCKETS];
        for (int i = 0; i < NUM_BUCKETS; i++) {
            buckets[i] = new Bucket();
        }
    }

    /**
     * Records a request occurrence and its outcome.
     *
     * @param success whether request status was 2xx or 3xx
     * @param latencyMs request processing latency in milliseconds
     */
    public void record(boolean success, double latencyMs) {
        long nowSec = System.currentTimeMillis() / 1000L;
        long bucketSec = nowSec - (nowSec % BUCKET_SIZE_SECONDS);
        int index = (int) Math.floorMod(bucketSec / BUCKET_SIZE_SECONDS, (long) NUM_BUCKETS);

        Bucket bucket = buckets[index];
        bucket.updateTimestamp(bucketSec);

        bucket.requests.increment();
        if (success) {
            bucket.successes.increment();
        } else {
            bucket.failures.increment();
        }
        if (latencyMs > 0.0) {
            bucket.latencySumNanos.add((long) (latencyMs * 1_000_000.0));
        }
    }

    /**
     * Aggregates stats for the specified historical duration (e.g. 1m, 5m, 15m, 1h).
     */
    public TrafficStats aggregate(Duration duration) {
        long nowSec = System.currentTimeMillis() / 1000L;
        long durationSec = Math.max(10, duration.toSeconds());
        long cutoffSec = nowSec - durationSec;

        long totalRequests = 0;
        long totalSuccesses = 0;
        long totalFailures = 0;
        long totalLatencyNanos = 0;

        for (Bucket bucket : buckets) {
            long bucketTime = bucket.timestampSec.get();
            if (bucketTime >= cutoffSec && bucketTime <= nowSec) {
                totalRequests += bucket.requests.sum();
                totalSuccesses += bucket.successes.sum();
                totalFailures += bucket.failures.sum();
                totalLatencyNanos += bucket.latencySumNanos.sum();
            }
        }

        double avgLatency = totalRequests > 0
                ? Math.round(((totalLatencyNanos / 1_000_000.0) / totalRequests) * 100.0) / 100.0
                : 0.0;

        return new TrafficStats(totalRequests, totalSuccesses, totalFailures, avgLatency);
    }

    public record TrafficStats(
            long requests,
            long successful,
            long failed,
            double averageLatencyMs
    ) {}

    private static final class Bucket {
        private final AtomicLong timestampSec = new AtomicLong(-1);
        private final LongAdder requests = new LongAdder();
        private final LongAdder successes = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private final LongAdder latencySumNanos = new LongAdder();

        private void updateTimestamp(long targetSec) {
            long current = timestampSec.get();
            if (current != targetSec) {
                synchronized (this) {
                    if (timestampSec.get() != targetSec) {
                        requests.reset();
                        successes.reset();
                        failures.reset();
                        latencySumNanos.reset();
                        timestampSec.set(targetSec);
                    }
                }
            }
        }
    }
}
