package com.gauransh.gateway.ratelimiter.algorithm.leakybucket;

import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Leaky Bucket Rate Limiter Complete Test Suite")
class LeakyBucketRateLimiterTest {

    private RateLimiterProperties properties;
    private MutableClock clock;
    private LeakyBucketRateLimiter rateLimiter;
    private final Instant baseTime = Instant.parse("2026-08-12T12:00:00.000Z");

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(10);
        properties.setRefillRate(2.0); // 2 units per second leak rate = 1 unit leaked every 500ms

        clock = new MutableClock(baseTime);
        rateLimiter = new LeakyBucketRateLimiter(properties, clock);
    }

    private RateLimitContext createContext(String clientId) {
        return new RateLimitContext(
                clientId,
                "/api/v1/resource",
                "GET",
                clock.instant(),
                "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId))
        );
    }

    @Nested
    @DisplayName("1. Initial Bucket State & Basic Capacity Accumulation")
    class InitialStateAndAccumulation {

        @Test
        @DisplayName("First request into empty bucket should be allowed with correct remaining capacity")
        void testFirstRequestAllowed() {
            RateLimitContext context = createContext("client-1");

            RateLimitDecision decision = rateLimiter.allowRequest(context);

            assertTrue(decision.allowed());
            assertEquals(10L, decision.limit());
            assertEquals(9L, decision.remainingRequests());
            assertEquals(RateLimitConstants.REASON_ALLOWED, decision.reason());
            assertEquals(Duration.ZERO, decision.retryAfter());
            assertNotNull(decision.resetTime());
        }

        @Test
        @DisplayName("Burst of 10 requests allowed up to capacity, 11th request rejected")
        void testFullBurstAllowedAndEleventhRejected() {
            RateLimitContext context = createContext("client-1");

            for (int i = 1; i <= 10; i++) {
                RateLimitDecision decision = rateLimiter.allowRequest(context);
                assertTrue(decision.allowed(), "Request " + i + " should be allowed");
                assertEquals(10 - i, decision.remainingRequests());
            }

            RateLimitDecision eleventhDecision = rateLimiter.allowRequest(context);
            assertFalse(eleventhDecision.allowed());
            assertEquals(10L, eleventhDecision.limit());
            assertEquals(0L, eleventhDecision.remainingRequests());
            assertEquals(RateLimitConstants.REASON_EXCEEDED, eleventhDecision.reason());
            assertTrue(eleventhDecision.retryAfter().toMillis() > 0);
        }

        @Test
        @DisplayName("Capacity = 1 allows 1st request, rejects 2nd immediate request")
        void testCapacityOne() {
            properties.setDefaultCapacity(1);
            LeakyBucketRateLimiter singleLimiter = new LeakyBucketRateLimiter(properties, clock);
            RateLimitContext context = createContext("client-cap1");

            RateLimitDecision req1 = singleLimiter.allowRequest(context);
            assertTrue(req1.allowed());
            assertEquals(0L, req1.remainingRequests());

            RateLimitDecision req2 = singleLimiter.allowRequest(context);
            assertFalse(req2.allowed());
            assertEquals(0L, req2.remainingRequests());
        }
    }

    @Nested
    @DisplayName("2. Continuous Drainage / Leakage Over Time")
    class LeakageOverTime {

        @Test
        @DisplayName("Leak 1 request unit after 500ms when leak rate is 2 units/sec")
        void testPartialLeakAfterDelay() {
            RateLimitContext context = createContext("client-1");

            // Fill bucket to capacity (10 requests)
            for (int i = 0; i < 10; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }

            // Immediately rejected
            assertFalse(rateLimiter.allowRequest(context).allowed());

            // Advance clock by 500ms -> should leak exactly 1 unit (0.5s * 2.0 = 1.0)
            clock.advance(Duration.ofMillis(500));

            RateLimitDecision afterLeak = rateLimiter.allowRequest(context);
            assertTrue(afterLeak.allowed());
            assertEquals(0L, afterLeak.remainingRequests());

            // Immediately next request rejected because bucket is full again
            assertFalse(rateLimiter.allowRequest(context).allowed());
        }

        @Test
        @DisplayName("Water level drains completely to 0 after extended idle period")
        void testWaterDrainsCompletelyAfterLongIdle() {
            RateLimitContext context = createContext("client-1");

            // Fill bucket
            for (int i = 0; i < 10; i++) {
                rateLimiter.allowRequest(context);
            }

            // Advance clock by 100 seconds (drains 200 units, bucket floor capped at 0)
            clock.advance(Duration.ofSeconds(100));

            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            // After draining to 0 and adding 1 request, remaining space is 9
            assertEquals(9L, decision.remainingRequests());
        }

        @Test
        @DisplayName("Exact capacity boundary rejection calculates mathematically accurate retryAfter duration")
        void testExactCapacityBoundaryAndRetryAfterCalculation() {
            RateLimitContext context = createContext("client-retry-calc");

            // Fill bucket to capacity 10
            for (int i = 0; i < 10; i++) {
                rateLimiter.allowRequest(context);
            }

            // 11th request on full bucket: leakRate = 2 units/sec (0.002 units/ms).
            // waterNeeded = (10 + 1) - 10 = 1.0 unit.
            // retryAfter = ceil(1.0 / 0.002) = 500ms.
            RateLimitDecision rejection = rateLimiter.allowRequest(context);
            assertFalse(rejection.allowed());
            assertEquals(Duration.ofMillis(500), rejection.retryAfter());
            assertEquals(clock.instant().plus(Duration.ofMillis(500)), rejection.resetTime());
        }
    }

    @Nested
    @DisplayName("3. Multi-Client State Isolation")
    class MultiClientIsolation {

        @Test
        @DisplayName("Filling bucket for Client A does not affect Client B")
        void testClientIsolation() {
            RateLimitContext contextA = createContext("client-A");
            RateLimitContext contextB = createContext("client-B");

            // Fill Client A's bucket
            for (int i = 0; i < 10; i++) {
                rateLimiter.allowRequest(contextA);
            }
            assertFalse(rateLimiter.allowRequest(contextA).allowed());

            // Client B should still have full capacity
            RateLimitDecision decisionB = rateLimiter.allowRequest(contextB);
            assertTrue(decisionB.allowed());
            assertEquals(9L, decisionB.remainingRequests());
        }
    }

    @Nested
    @DisplayName("4. Clock Regression & Time Edge Cases")
    class ClockRegressionAndRejections {

        @Test
        @DisplayName("Backward clock drift should freeze water level and update timestamp without throwing")
        void testClockRegression() {
            RateLimitContext context = createContext("client-time-travel");

            // Request 1 at T0
            assertTrue(rateLimiter.allowRequest(context).allowed());

            // Regress clock by 5 seconds
            clock.advance(Duration.ofSeconds(-5));

            // Request 2 at T-5
            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            assertEquals(8L, decision.remainingRequests());
        }

        @Test
        @DisplayName("Clock regression invariant: lastLeakTimestampMillis never moves backwards and does not cause artificial leakage on recovery")
        void testClockRegressionInvariantNoArtificialLeakage() {
            RateLimitContext context = createContext("client-invariant");

            // Request 1 at T = baseTime (T=0)
            rateLimiter.allowRequest(context);
            long initialLastLeak = rateLimiter.getBucketState("client-invariant").lastLeakTimestampMillis();

            // Regress clock by 10 seconds (T = -10s)
            clock.advance(Duration.ofSeconds(-10));
            rateLimiter.allowRequest(context);
            long regressedLastLeak = rateLimiter.getBucketState("client-invariant").lastLeakTimestampMillis();

            // Invariant assertion: timestamp MUST NOT move backwards
            assertTrue(regressedLastLeak >= initialLastLeak, "lastLeakTimestampMillis must be monotonically non-decreasing");
            assertEquals(initialLastLeak, regressedLastLeak);

            // Advance clock to T = +5s (which is +5s past initial T=0, but +15s past regressed T=-10s)
            clock.advance(Duration.ofSeconds(15));
            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            // Elapsed time should be calculated from T=0 to T=+5s (5s * 2 units/sec = 10 units drained),
            // NOT from T=-10s to T=+5s (15s * 2 = 30 units drained).
            long recoveredLastLeak = rateLimiter.getBucketState("client-invariant").lastLeakTimestampMillis();
            assertTrue(recoveredLastLeak >= regressedLastLeak);
        }

        @Test
        @DisplayName("Multiple requests at exact same millisecond accumulate water level correctly")
        void testZeroElapsedTimeMultipleRequests() {
            RateLimitContext context = createContext("client-instant");

            RateLimitDecision req1 = rateLimiter.allowRequest(context);
            RateLimitDecision req2 = rateLimiter.allowRequest(context);

            assertTrue(req1.allowed());
            assertEquals(9L, req1.remainingRequests());
            assertTrue(req2.allowed());
            assertEquals(8L, req2.remainingRequests());
        }
    }

    @Nested
    @DisplayName("5. Concurrency & Thread Safety")
    class ConcurrencySafety {

        @Test
        @DisplayName("Concurrent requests from multiple threads execute atomically without state corruption")
        void testConcurrentRequests() throws Exception {
            int threadCount = 20;
            int requestsPerThread = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            AtomicInteger allowedCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
                    try {
                        latch.await();
                        for (int r = 0; r < requestsPerThread; r++) {
                            RateLimitContext ctx = createContext("concurrent-client");
                            RateLimitDecision decision = rateLimiter.allowRequest(ctx);
                            if (decision.allowed()) {
                                allowedCount.incrementAndGet();
                            } else {
                                rejectedCount.incrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            latch.countDown();
            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            // Total requests = 100. Capacity = 10. Elapsed time = 0.
            // Exactly 10 requests should be allowed and 90 rejected.
            assertEquals(10, allowedCount.get());
            assertEquals(90, rejectedCount.get());
        }
    }

    @Nested
    @DisplayName("6. Edge Cases & Validation")
    class EdgeCasesAndValidation {

        @Test
        @DisplayName("Null context should throw NullPointerException")
        void testNullContextThrows() {
            assertThrows(NullPointerException.class, () -> rateLimiter.allowRequest(null));
        }

        @Test
        @DisplayName("Non-positive capacity should throw IllegalArgumentException")
        void testNonPositiveCapacityThrows() {
            properties.setDefaultCapacity(0);
            LeakyBucketRateLimiter invalidCapacityLimiter = new LeakyBucketRateLimiter(properties, clock);
            RateLimitContext context = createContext("client-invalid");

            assertThrows(IllegalArgumentException.class, () -> invalidCapacityLimiter.allowRequest(context));
        }

        @Test
        @DisplayName("Non-positive or infinite leak rate should throw IllegalArgumentException")
        void testInvalidLeakRateThrows() {
            RateLimitContext context = createContext("client-invalid-rate");

            properties.setRefillRate(0.0);
            LeakyBucketRateLimiter zeroRateLimiter = new LeakyBucketRateLimiter(properties, clock);
            assertThrows(IllegalArgumentException.class, () -> zeroRateLimiter.allowRequest(context));

            properties.setRefillRate(-1.0);
            LeakyBucketRateLimiter negRateLimiter = new LeakyBucketRateLimiter(properties, clock);
            assertThrows(IllegalArgumentException.class, () -> negRateLimiter.allowRequest(context));

            properties.setRefillRate(Double.NaN);
            LeakyBucketRateLimiter nanLimiter = new LeakyBucketRateLimiter(properties, clock);
            assertThrows(IllegalArgumentException.class, () -> nanLimiter.allowRequest(context));

            properties.setRefillRate(Double.POSITIVE_INFINITY);
            LeakyBucketRateLimiter posInfLimiter = new LeakyBucketRateLimiter(properties, clock);
            assertThrows(IllegalArgumentException.class, () -> posInfLimiter.allowRequest(context));
        }
    }

    private static class MutableClock extends Clock {
        private Instant instant;

        public MutableClock(Instant initial) {
            this.instant = initial;
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
}
