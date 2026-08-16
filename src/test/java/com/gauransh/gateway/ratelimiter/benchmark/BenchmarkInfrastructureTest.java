package com.gauransh.gateway.ratelimiter.benchmark;

import com.gauransh.gateway.ratelimiter.config.RateLimiterAlgorithm;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Benchmark Infrastructure Unit Tests")
class BenchmarkInfrastructureTest {

    private final Instant baseTime = Instant.parse("2026-08-16T12:00:00.000Z");
    private RateLimiterProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(100L);
        properties.setDefaultWindow(Duration.ofSeconds(10));
        properties.setRefillRate(10.0);
    }

    @Test
    @DisplayName("Sustained workload generator creates contexts with correct timestamps and step increments")
    void testSustainedWorkloadGenerator() {
        BenchmarkWorkloadGenerator.WorkloadScenario scenario =
                BenchmarkWorkloadGenerator.createSustainedWorkload("Test Sustained", 100, 10L, baseTime);

        assertEquals("Test Sustained", scenario.name());
        assertEquals(100, scenario.requestCount());
        assertEquals(100, scenario.contexts().length);
        assertEquals(100, scenario.timeIncrementsMillis().length);

        assertEquals(baseTime, scenario.contexts()[0].timestamp());
        assertEquals(10L, scenario.timeIncrementsMillis()[0]);

        assertEquals(baseTime.plusMillis(990L), scenario.contexts()[99].timestamp());
    }

    @Test
    @DisplayName("Burst workload generator freezes logical time at t0")
    void testBurstWorkloadGenerator() {
        BenchmarkWorkloadGenerator.WorkloadScenario scenario =
                BenchmarkWorkloadGenerator.createBurstWorkload("Test Burst", 50, baseTime);

        assertEquals(50, scenario.requestCount());
        for (int i = 0; i < 50; i++) {
            assertEquals(baseTime, scenario.contexts()[i].timestamp());
            assertEquals(0L, scenario.timeIncrementsMillis()[i]);
        }
    }

    @Test
    @DisplayName("Recovery workload generator incorporates initial burst, gap advancement, and recovery burst")
    void testRecoveryWorkloadGenerator() {
        BenchmarkWorkloadGenerator.WorkloadScenario scenario =
                BenchmarkWorkloadGenerator.createRecoveryWorkload("Test Recovery", 150, 5L, 50, baseTime);

        assertEquals(200, scenario.requestCount());
        // First request of recovery burst should jump by 5000ms
        assertEquals(5000L, scenario.timeIncrementsMillis()[150]);
        assertEquals(baseTime.plusSeconds(5L), scenario.contexts()[150].timestamp());
    }

    @Test
    @DisplayName("Single Random seed 0x42L generates deterministic per-run permutations")
    void testDeterministicShuffling() {
        List<RateLimiterAlgorithm> algorithms = List.of(
                RateLimiterAlgorithm.FIXED_WINDOW,
                RateLimiterAlgorithm.SLIDING_WINDOW_COUNTER,
                RateLimiterAlgorithm.SLIDING_WINDOW_LOG,
                RateLimiterAlgorithm.TOKEN_BUCKET,
                RateLimiterAlgorithm.LEAKY_BUCKET
        );

        Random random1 = new Random(0x42L);
        List<RateLimiterAlgorithm> run1_seq1 = new ArrayList<>(algorithms);
        Collections.shuffle(run1_seq1, random1);

        List<RateLimiterAlgorithm> run2_seq1 = new ArrayList<>(algorithms);
        Collections.shuffle(run2_seq1, random1);

        Random random2 = new Random(0x42L);
        List<RateLimiterAlgorithm> run1_seq2 = new ArrayList<>(algorithms);
        Collections.shuffle(run1_seq2, random2);

        List<RateLimiterAlgorithm> run2_seq2 = new ArrayList<>(algorithms);
        Collections.shuffle(run2_seq2, random2);

        // Sequence generated from same seed must be 100% deterministic and identical across instances
        assertEquals(run1_seq1, run1_seq2);
        assertEquals(run2_seq1, run2_seq2);
        // Sequential shuffles with the same Random instance produce distinct permutations across runs
        assertNotEquals(run1_seq1, run2_seq1);
    }

    @Test
    @DisplayName("RunMetrics percentile calculations return accurate micron values for sorted arrays")
    void testPercentileCalculations() {
        long[] latenciesNanos = new long[100];
        for (int i = 0; i < 100; i++) {
            latenciesNanos[i] = (i + 1) * 1000L; // 1µs to 100µs
        }

        BenchmarkMetricsCollector.RunMetrics metrics = new BenchmarkMetricsCollector.RunMetrics(
                100, 100, 0, 0, 10_000_000L, latenciesNanos, 1000L, 2000L
        );

        assertEquals(50.5, metrics.getAverageLatencyMicros(), 0.01);
        assertEquals(51.0, metrics.getPercentileLatencyMicros(0.50), 0.01);
        assertEquals(95.0, metrics.getPercentileLatencyMicros(0.95), 0.01);
        assertEquals(99.0, metrics.getPercentileLatencyMicros(0.99), 0.01);
    }

    @Test
    @DisplayName("AccuracyOracleValidator computes exact mismatch count and percentage error")
    void testAccuracyOracleValidation() {
        BenchmarkWorkloadGenerator.WorkloadScenario scenario =
                BenchmarkWorkloadGenerator.createBoundaryTransitionWorkload("Accuracy Test", 10L, baseTime);

        AccuracyOracleValidator.AccuracyResult result =
                AccuracyOracleValidator.validateSlidingWindowCounter(scenario, properties, baseTime);

        assertNotNull(result);
        assertEquals(scenario.requestCount(), result.totalRequests());
        assertTrue(result.mismatchCount() >= 0);
        assertTrue(result.approximationErrorPercent() >= 0.0 && result.approximationErrorPercent() <= 100.0);
    }
}
