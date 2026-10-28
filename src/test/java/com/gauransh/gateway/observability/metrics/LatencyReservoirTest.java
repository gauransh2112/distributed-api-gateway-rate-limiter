package com.gauransh.gateway.observability.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LatencyReservoirTest {

    @Test
    @DisplayName("Empty reservoir returns zero values without throwing")
    void emptyReservoirReturnsZero() {
        LatencyReservoir reservoir = new LatencyReservoir(100);

        assertThat(reservoir.getTotalCount()).isZero();
        assertThat(reservoir.getMean()).isZero();
        assertThat(reservoir.getP95()).isZero();
        assertThat(reservoir.getP99()).isZero();
    }

    @Test
    @DisplayName("Records samples and computes accurate mean, P95, and P99 percentiles")
    void computesAccuratePercentiles() {
        LatencyReservoir reservoir = new LatencyReservoir(100);

        // Record 1 to 100 ms
        for (int i = 1; i <= 100; i++) {
            reservoir.record(i);
        }

        assertThat(reservoir.getTotalCount()).isEqualTo(100);
        assertThat(reservoir.getMean()).isEqualTo(50.5);
        assertThat(reservoir.getP95()).isEqualTo(95.0);
        assertThat(reservoir.getP99()).isEqualTo(99.0);
    }

    @Test
    @DisplayName("Overwriting ring buffer bounds memory while totalCount continues to accumulate")
    void boundedCapacityOverwritesBuffer() {
        int capacity = 50;
        LatencyReservoir reservoir = new LatencyReservoir(capacity);

        for (int i = 1; i <= 200; i++) {
            reservoir.record(i);
        }

        assertThat(reservoir.getTotalCount()).isEqualTo(200);
        assertThat(reservoir.getP95()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Percentile parameter validation")
    void percentileBoundsValidation() {
        LatencyReservoir reservoir = new LatencyReservoir(10);
        assertThatThrownBy(() -> reservoir.getPercentile(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reservoir.getPercentile(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Concurrent recording under multi-threaded load is thread-safe and non-blocking")
    void concurrentRecordingThreadSafety() throws InterruptedException {
        int threads = 8;
        int recordsPerThread = 1000;
        LatencyReservoir reservoir = new LatencyReservoir(500);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            executor.submit(() -> {
                try {
                    for (int i = 0; i < recordsPerThread; i++) {
                        reservoir.record(10.0);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(reservoir.getTotalCount()).isEqualTo((long) threads * recordsPerThread);
        assertThat(reservoir.getMean()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("Reset clears counts and reservoir buffer")
    void resetClearsState() {
        LatencyReservoir reservoir = new LatencyReservoir(50);
        reservoir.record(42.0);

        assertThat(reservoir.getTotalCount()).isEqualTo(1);
        reservoir.reset();

        assertThat(reservoir.getTotalCount()).isZero();
        assertThat(reservoir.getMean()).isZero();
    }
}
