package com.gauransh.gateway.ratelimiter.benchmark;

import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Deterministic workload generator for rate limiter algorithm benchmarks.
 *
 * <p>Pre-allocates request metadata objects ({@link RateLimitContext}) and defines
 * simulated logical-time progression via a controllable {@link MutableClock} abstraction.</p>
 */
public class BenchmarkWorkloadGenerator {

    private static final String DEFAULT_CLIENT_ID = "client-benchmark-1";
    private static final String DEFAULT_PATH = "/api/v1/benchmark";
    private static final String DEFAULT_METHOD = "GET";
    private static final String DEFAULT_IP = "127.0.0.1";

    /**
     * Controllable logical clock implementation avoiding wall-clock sleeping.
     */
    public static class MutableClock extends Clock {
        private Instant instant;

        public MutableClock(Instant initial) {
            this.instant = Objects.requireNonNull(initial, "initial instant must not be null");
        }

        public void setInstant(Instant newInstant) {
            this.instant = Objects.requireNonNull(newInstant, "newInstant must not be null");
        }

        public void advance(Duration duration) {
            this.instant = this.instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /**
     * Pre-allocated workload context sequence with corresponding timestamps.
     *
     * @param name scenario descriptive name
     * @param requestCount total number of request context steps
     * @param contexts array of pre-allocated {@link RateLimitContext} instances
     * @param timeIncrementsMillis array of time step advances in milliseconds before evaluating each request
     */
    public record WorkloadScenario(
            String name,
            int requestCount,
            RateLimitContext[] contexts,
            long[] timeIncrementsMillis
    ) {}

    /**
     * Creates a sustained traffic workload scenario with incremental logical time.
     *
     * @param name scenario name
     * @param requestCount number of requests
     * @param stepMillis logical time increment per request (e.g. 10ms)
     * @param baseTime starting instant
     * @return workload scenario
     */
    public static WorkloadScenario createSustainedWorkload(String name, int requestCount, long stepMillis, Instant baseTime) {
        RateLimitContext[] contexts = new RateLimitContext[requestCount];
        long[] increments = new long[requestCount];
        Instant current = baseTime;

        for (int i = 0; i < requestCount; i++) {
            contexts[i] = new RateLimitContext(
                    DEFAULT_CLIENT_ID,
                    DEFAULT_PATH,
                    DEFAULT_METHOD,
                    current,
                    DEFAULT_IP,
                    Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(DEFAULT_CLIENT_ID))
            );
            increments[i] = stepMillis;
            current = current.plusMillis(stepMillis);
        }

        return new WorkloadScenario(name, requestCount, contexts, increments);
    }

    /**
     * Creates an instantaneous burst workload scenario with time frozen at t0.
     *
     * @param name scenario name
     * @param burstSize number of instantaneous requests
     * @param baseTime starting instant
     * @return workload scenario
     */
    public static WorkloadScenario createBurstWorkload(String name, int burstSize, Instant baseTime) {
        RateLimitContext[] contexts = new RateLimitContext[burstSize];
        long[] increments = new long[burstSize];

        for (int i = 0; i < burstSize; i++) {
            contexts[i] = new RateLimitContext(
                    DEFAULT_CLIENT_ID,
                    DEFAULT_PATH,
                    DEFAULT_METHOD,
                    baseTime,
                    DEFAULT_IP,
                    Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(DEFAULT_CLIENT_ID))
            );
            increments[i] = 0L; // Frozen time
        }

        return new WorkloadScenario(name, burstSize, contexts, increments);
    }

    /**
     * Creates a recovery workload scenario: initial burst -> time advancement -> second burst.
     *
     * @param name scenario name
     * @param initialBurstSize initial burst count (e.g. 150)
     * @param recoveryGapSeconds time advancement between bursts (e.g. 5 seconds)
     * @param secondBurstSize second burst count (e.g. 50)
     * @param baseTime starting instant
     * @return workload scenario
     */
    public static WorkloadScenario createRecoveryWorkload(
            String name,
            int initialBurstSize,
            long recoveryGapSeconds,
            int secondBurstSize,
            Instant baseTime
    ) {
        int totalRequests = initialBurstSize + secondBurstSize;
        RateLimitContext[] contexts = new RateLimitContext[totalRequests];
        long[] increments = new long[totalRequests];

        // Phase 1: Initial Burst at t0
        for (int i = 0; i < initialBurstSize; i++) {
            contexts[i] = new RateLimitContext(
                    DEFAULT_CLIENT_ID,
                    DEFAULT_PATH,
                    DEFAULT_METHOD,
                    baseTime,
                    DEFAULT_IP,
                    Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(DEFAULT_CLIENT_ID))
            );
            increments[i] = 0L;
        }

        // Phase 2: Second Burst at t0 + recoveryGapSeconds
        Instant recoveredTime = baseTime.plusSeconds(recoveryGapSeconds);
        long gapMillis = recoveryGapSeconds * 1000L;

        for (int i = initialBurstSize; i < totalRequests; i++) {
            contexts[i] = new RateLimitContext(
                    DEFAULT_CLIENT_ID,
                    DEFAULT_PATH,
                    DEFAULT_METHOD,
                    recoveredTime,
                    DEFAULT_IP,
                    Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(DEFAULT_CLIENT_ID))
            );
            // The first request of Phase 2 incurs the time advancement jump
            increments[i] = (i == initialBurstSize) ? gapMillis : 0L;
        }

        return new WorkloadScenario(name, totalRequests, contexts, increments);
    }

    /**
     * Creates a front-loaded boundary transition workload to demonstrate Sliding Window Counter approximation variance
     * against Sliding Window Log oracle.
     *
     * <p>Front-loads 100 requests early in Window 1 (t = 0.1s), then advances to t = 11.0s (1s into Window 2)
     * and sends 20 requests. Log Oracle evicts all 100 Window 1 requests (outside rolling window [1.0s, 11.0s]),
     * whereas Sliding Window Counter applies a 90% weighting (estimating 90 active requests) and rejects requests 11..20.</p>
     *
     * @param name scenario name
     * @param windowSeconds evaluation window duration in seconds (10s)
     * @param baseTime starting instant
     * @return workload scenario
     */
    public static WorkloadScenario createBoundaryTransitionWorkload(String name, long windowSeconds, Instant baseTime) {
        int window1FrontLoad = 100;
        int window2TestRequests = 20;
        int totalRequests = window1FrontLoad + window2TestRequests;

        RateLimitContext[] contexts = new RateLimitContext[totalRequests];
        long[] increments = new long[totalRequests];

        Instant t1 = baseTime.plusMillis(100L); // t = 0.1s in Window 1
        Instant t2 = baseTime.plusSeconds(windowSeconds).plusMillis(1000L); // t = 11.0s (1s into Window 2)

        // Phase 1: 100 requests front-loaded in Window 1
        for (int i = 0; i < window1FrontLoad; i++) {
            contexts[i] = new RateLimitContext(
                    DEFAULT_CLIENT_ID, DEFAULT_PATH, DEFAULT_METHOD, t1, DEFAULT_IP,
                    Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(DEFAULT_CLIENT_ID))
            );
            increments[i] = (i == 0) ? 100L : 0L;
        }

        // Phase 2: 20 requests at t = 11.0s in Window 2
        for (int i = window1FrontLoad; i < totalRequests; i++) {
            contexts[i] = new RateLimitContext(
                    DEFAULT_CLIENT_ID, DEFAULT_PATH, DEFAULT_METHOD, t2, DEFAULT_IP,
                    Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(DEFAULT_CLIENT_ID))
            );
            // Advance logical clock to 11.0s on first request of Phase 2
            increments[i] = (i == window1FrontLoad) ? 10900L : 0L;
        }

        return new WorkloadScenario(name, totalRequests, contexts, increments);
    }

    /**
     * Creates a Fixed Window boundary spike scenario (burst near end of Window 1 + burst at start of Window 2).
     *
     * @param name scenario name
     * @param windowSeconds window duration in seconds
     * @param baseTime starting instant
     * @return workload scenario
     */
    public static WorkloadScenario createFixedWindowBoundarySpikeWorkload(String name, long windowSeconds, Instant baseTime) {
        int burstPerSide = 80;
        int totalRequests = burstPerSide * 2;
        RateLimitContext[] contexts = new RateLimitContext[totalRequests];
        long[] increments = new long[totalRequests];

        long windowMillis = windowSeconds * 1000L;
        Instant endOfWindow1 = baseTime.plusMillis(windowMillis - 50L);
        Instant startOfWindow2 = baseTime.plusMillis(windowMillis + 50L);

        // Side 1: 80 requests at end of Window 1 (t = 9.95s)
        for (int i = 0; i < burstPerSide; i++) {
            contexts[i] = new RateLimitContext(
                    DEFAULT_CLIENT_ID, DEFAULT_PATH, DEFAULT_METHOD, endOfWindow1, DEFAULT_IP,
                    Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(DEFAULT_CLIENT_ID))
            );
            increments[i] = (i == 0) ? (windowMillis - 50L) : 0L;
        }

        // Side 2: 80 requests at start of Window 2 (t = 10.05s)
        for (int i = burstPerSide; i < totalRequests; i++) {
            contexts[i] = new RateLimitContext(
                    DEFAULT_CLIENT_ID, DEFAULT_PATH, DEFAULT_METHOD, startOfWindow2, DEFAULT_IP,
                    Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(DEFAULT_CLIENT_ID))
            );
            increments[i] = (i == burstPerSide) ? 100L : 0L;
        }

        return new WorkloadScenario(name, totalRequests, contexts, increments);
    }
}
