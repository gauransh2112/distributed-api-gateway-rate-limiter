package com.gauransh.gateway.ratelimiter.benchmark;

import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.FixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.SlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;

import java.time.Duration;
import java.time.Instant;

/**
 * Validates algorithm decision accuracy against reference oracles and boundary conditions.
 *
 * <p>Compares {@link SlidingWindowCounterRateLimiter} against the exact timestamp-based
 * {@link SlidingWindowLogRateLimiter} oracle on boundary-transition workloads to calculate
 * exact decision mismatch count and approximation error percentage.</p>
 */
public class AccuracyOracleValidator {

    /**
     * Result of comparing Sliding Window Counter against Sliding Window Log oracle.
     */
    public record AccuracyResult(
            String scenarioName,
            int totalRequests,
            int counterAllowed,
            int counterRejected,
            int oracleAllowed,
            int oracleRejected,
            int mismatchCount,
            double approximationErrorPercent
    ) {}

    /**
     * Result of Fixed Window boundary spike analysis.
     */
    public record FixedWindowBoundaryResult(
            int window1Burst,
            int window2Burst,
            int totalRequests,
            int allowedRequests,
            int rejectedRequests,
            double capacityMultiplierAccepted
    ) {}

    /**
     * Evaluates approximation error of Sliding Window Counter against Sliding Window Log oracle.
     *
     * @param scenario workload scenario targeting window boundary transitions
     * @param properties rate limiter properties (capacity = 100, window = 10s)
     * @param baseTime starting instant
     * @return accuracy result containing mismatch count and approximation error percentage
     */
    public static AccuracyResult validateSlidingWindowCounter(
            BenchmarkWorkloadGenerator.WorkloadScenario scenario,
            RateLimiterProperties properties,
            Instant baseTime
    ) {
        BenchmarkWorkloadGenerator.MutableClock counterClock = new BenchmarkWorkloadGenerator.MutableClock(baseTime);
        BenchmarkWorkloadGenerator.MutableClock oracleClock = new BenchmarkWorkloadGenerator.MutableClock(baseTime);

        SlidingWindowCounterRateLimiter counterLimiter = new SlidingWindowCounterRateLimiter(properties, counterClock);
        SlidingWindowLogRateLimiter logOracleLimiter = new SlidingWindowLogRateLimiter(properties, oracleClock);

        int total = scenario.requestCount();
        RateLimitContext[] contexts = scenario.contexts();
        long[] increments = scenario.timeIncrementsMillis();

        int counterAllowed = 0;
        int counterRejected = 0;
        int oracleAllowed = 0;
        int oracleRejected = 0;
        int mismatchCount = 0;

        for (int i = 0; i < total; i++) {
            if (increments[i] > 0) {
                counterClock.advance(Duration.ofMillis(increments[i]));
                oracleClock.advance(Duration.ofMillis(increments[i]));
            }

            RateLimitContext ctx = contexts[i];
            RateLimitDecision counterDecision = counterLimiter.allowRequest(ctx);
            RateLimitDecision oracleDecision = logOracleLimiter.allowRequest(ctx);

            if (counterDecision.allowed()) {
                counterAllowed++;
            } else {
                counterRejected++;
            }

            if (oracleDecision.allowed()) {
                oracleAllowed++;
            } else {
                oracleRejected++;
            }

            // Mismatch definition: counter decision boolean differs from oracle decision boolean
            if (counterDecision.allowed() != oracleDecision.allowed()) {
                mismatchCount++;
            }
        }

        double errorPercent = (total > 0) ? (mismatchCount / (double) total) * 100.0 : 0.0;

        return new AccuracyResult(
                scenario.name(),
                total,
                counterAllowed,
                counterRejected,
                oracleAllowed,
                oracleRejected,
                mismatchCount,
                errorPercent
        );
    }

    /**
     * Evaluates Fixed Window rate limit boundary spike behavior.
     *
     * @param scenario boundary spike workload (burst at end of W1 + burst at start of W2)
     * @param properties rate limiter properties (capacity = 100, window = 10s)
     * @param baseTime starting instant
     * @return boundary spike analysis result
     */
    public static FixedWindowBoundaryResult validateFixedWindowBoundarySpike(
            BenchmarkWorkloadGenerator.WorkloadScenario scenario,
            RateLimiterProperties properties,
            Instant baseTime
    ) {
        BenchmarkWorkloadGenerator.MutableClock clock = new BenchmarkWorkloadGenerator.MutableClock(baseTime);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(properties, clock);

        int total = scenario.requestCount();
        RateLimitContext[] contexts = scenario.contexts();
        long[] increments = scenario.timeIncrementsMillis();

        int allowed = 0;
        int rejected = 0;

        for (int i = 0; i < total; i++) {
            if (increments[i] > 0) {
                clock.advance(Duration.ofMillis(increments[i]));
            }
            RateLimitDecision decision = limiter.allowRequest(contexts[i]);
            if (decision.allowed()) {
                allowed++;
            } else {
                rejected++;
            }
        }

        long capacity = properties.getDefaultCapacity();
        double multiplier = (capacity > 0) ? (allowed / (double) capacity) : 0.0;

        return new FixedWindowBoundaryResult(
                80, // W1 burst
                80, // W2 burst
                total,
                allowed,
                rejected,
                multiplier
        );
    }
}
