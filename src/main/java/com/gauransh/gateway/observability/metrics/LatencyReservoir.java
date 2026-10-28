package com.gauransh.gateway.observability.metrics;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe, bounded-memory reservoir sampler for latency measurements.
 *
 * <p>Uses a circular ring buffer with a fixed capacity to bound heap footprint,
 * paired with a LongAdder for accurate cumulative count. Quantile calculation
 * operates on a snapshot copy of the ring buffer. Because writes proceed concurrently
 * without acquiring a global array lock, the snapshot represents an approximate
 * point-in-time sample distribution rather than a globally synchronized atomic instant.
 * This trade-off ensures zero lock contention and non-blocking writes on the critical request path.</p>
 */
public class LatencyReservoir {

    private final int capacity;
    private final double[] buffer;
    private final AtomicInteger index = new AtomicInteger(0);
    private final LongAdder totalCount = new LongAdder();
    private final LongAdder totalSumNanos = new LongAdder();

    public LatencyReservoir() {
        this(2048);
    }

    public LatencyReservoir(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.buffer = new double[capacity];
    }

    /**
     * Records a latency sample in milliseconds.
     *
     * @param latencyMs latency in milliseconds
     */
    public void record(double latencyMs) {
        if (Double.isNaN(latencyMs) || latencyMs < 0.0) {
            return;
        }
        totalCount.increment();
        totalSumNanos.add((long) (latencyMs * 1_000_000.0));

        int raw = index.getAndIncrement();
        int pos = (raw & 0x7fffffff) % capacity;
        buffer[pos] = latencyMs;
    }

    /**
     * Returns total number of samples recorded over the application lifetime.
     */
    public long getTotalCount() {
        return totalCount.sum();
    }

    /**
     * Returns the mean latency of all recorded samples over the application lifetime.
     * Returns 0.0 if no samples have been recorded.
     */
    public double getMean() {
        long count = totalCount.sum();
        if (count == 0) {
            return 0.0;
        }
        double sumMs = totalSumNanos.sum() / 1_000_000.0;
        return Math.round((sumMs / count) * 100.0) / 100.0;
    }

    /**
     * Returns the 95th percentile latency from the active reservoir window.
     * Returns 0.0 if no samples are available.
     */
    public double getP95() {
        return getPercentile(0.95);
    }

    /**
     * Returns the 99th percentile latency from the active reservoir window.
     * Returns 0.0 if no samples are available.
     */
    public double getP99() {
        return getPercentile(0.99);
    }

    /**
     * Calculates the specified percentile (between 0.0 and 1.0) from a snapshot of the buffer.
     */
    public double getPercentile(double percentile) {
        if (percentile < 0.0 || percentile > 1.0) {
            throw new IllegalArgumentException("Percentile must be between 0.0 and 1.0");
        }
        long count = totalCount.sum();
        if (count == 0) {
            return 0.0;
        }

        int samples = (int) Math.min(count, capacity);
        double[] snapshot = new double[samples];
        System.arraycopy(buffer, 0, snapshot, 0, samples);
        Arrays.sort(snapshot);

        int rank = (int) Math.ceil(percentile * samples) - 1;
        if (rank < 0) {
            rank = 0;
        } else if (rank >= samples) {
            rank = samples - 1;
        }

        return Math.round(snapshot[rank] * 100.0) / 100.0;
    }

    /**
     * Resets the reservoir.
     */
    public void reset() {
        totalCount.reset();
        totalSumNanos.reset();
        index.set(0);
        Arrays.fill(buffer, 0.0);
    }
}
