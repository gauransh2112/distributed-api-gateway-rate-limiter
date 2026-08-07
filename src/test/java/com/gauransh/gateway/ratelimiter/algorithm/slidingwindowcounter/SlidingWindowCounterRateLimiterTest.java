package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter;

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

@DisplayName("Sliding Window Counter Rate Limiter Complete Test Suite")
class SlidingWindowCounterRateLimiterTest {

    private RateLimiterProperties properties;
    private MutableClock clock;
    private SlidingWindowCounterRateLimiter rateLimiter;
    private final Instant baseTime = Instant.parse("2026-08-05T12:00:00.000Z");

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(10);
        properties.setDefaultWindow(Duration.ofMinutes(1)); // 60,000 ms window

        clock = new MutableClock(baseTime);
        rateLimiter = new SlidingWindowCounterRateLimiter(properties, clock);
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
    @DisplayName("1. Quota Threshold Enforcement")
    class QuotaEnforcement {

        @Test
        @DisplayName("First request under limit should be allowed with correct remaining count")
        void testUnderLimit() {
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
        @DisplayName("Exactly at capacity (10th request) should be allowed with zero remaining")
        void testExactlyAtLimit() {
            RateLimitContext context = createContext("client-1");

            for (int i = 1; i <= 9; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }

            RateLimitDecision tenthDecision = rateLimiter.allowRequest(context);
            assertTrue(tenthDecision.allowed());
            assertEquals(10L, tenthDecision.limit());
            assertEquals(0L, tenthDecision.remainingRequests());
        }

        @Test
        @DisplayName("Above capacity (11th request) should be rejected with Retry-After populated")
        void testAboveLimit() {
            RateLimitContext context = createContext("client-1");

            for (int i = 1; i <= 10; i++) {
                rateLimiter.allowRequest(context);
            }

            RateLimitDecision eleventhDecision = rateLimiter.allowRequest(context);
            assertFalse(eleventhDecision.allowed());
            assertEquals(10L, eleventhDecision.limit());
            assertEquals(0L, eleventhDecision.remainingRequests());
            assertEquals(RateLimitConstants.REASON_EXCEEDED, eleventhDecision.reason());
            assertTrue(eleventhDecision.retryAfter().toSeconds() > 0);
        }

        @Test
        @DisplayName("Limit = 1 should allow 1st request and block 2nd request")
        void testLimitEqualsOne() {
            properties.setDefaultCapacity(1);
            rateLimiter = new SlidingWindowCounterRateLimiter(properties, clock);
            RateLimitContext context = createContext("client-strict");

            RateLimitDecision first = rateLimiter.allowRequest(context);
            assertTrue(first.allowed());
            assertEquals(0L, first.remainingRequests());

            RateLimitDecision second = rateLimiter.allowRequest(context);
            assertFalse(second.allowed());
        }

        @Test
        @DisplayName("Large capacity limit (1,000,000) should evaluate correctly without overflow")
        void testLargeCapacityLimit() {
            properties.setDefaultCapacity(1_000_000L);
            rateLimiter = new SlidingWindowCounterRateLimiter(properties, clock);
            RateLimitContext context = createContext("client-large");

            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            assertEquals(1_000_000L, decision.limit());
            assertEquals(999_999L, decision.remainingRequests());
        }
    }

    @Nested
    @DisplayName("2. Sliding Window Calculations & Weight Decay")
    class SlidingWindowCalculations {

        @Test
        @DisplayName("50% elapsed into Window 2 should weight previous window count by 50%")
        void testFiftyPercentElapsedWeighting() {
            RateLimitContext context = createContext("client-sliding");

            // Accept 10 requests in Window 1 (12:00:00 - 12:01:00)
            for (int i = 0; i < 10; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }

            // Advance clock by 90 seconds to 12:01:30 (50% into Window 2: 12:01:00 - 12:02:00)
            // Weight of previous window (10 requests) = (60 - 30) / 60 = 0.5
            // Weighted previous count = 10 * 0.5 = 5.0
            // Available capacity in Window 2 = 10 - 5.0 = 5 requests.
            clock.advance(Duration.ofSeconds(90));

            for (int i = 0; i < 5; i++) {
                RateLimitDecision decision = rateLimiter.allowRequest(context);
                assertTrue(decision.allowed(), "Request " + (i + 1) + " in Window 2 should be allowed");
            }

            // 6th request in Window 2 (weighted sum = 5.0 + 6.0 = 11.0 > 10) should be rejected
            RateLimitDecision rejected = rateLimiter.allowRequest(context);
            assertFalse(rejected.allowed(), "6th request in Window 2 must be rejected");
        }

        @Test
        @DisplayName("75% elapsed into Window 2 should weight previous window count by 25%")
        void testSeventyFivePercentElapsedWeighting() {
            RateLimitContext context = createContext("client-decay");

            // Accept 10 requests in Window 1
            for (int i = 0; i < 10; i++) {
                rateLimiter.allowRequest(context);
            }

            // Advance clock by 105 seconds to 12:01:45 (75% into Window 2)
            // Weight of previous window = (60 - 45) / 60 = 0.25
            // Weighted previous count = 10 * 0.25 = 2.5
            // Capacity = 10. Max new requests in Window 2 before 2.5 + N > 10 is 7 requests (2.5 + 7 = 9.5 <= 10).
            clock.advance(Duration.ofSeconds(105));

            for (int i = 0; i < 7; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }

            // 8th request (2.5 + 8 = 10.5 > 10) rejected
            assertFalse(rateLimiter.allowRequest(context).allowed());
        }

        @Test
        @DisplayName("Window rollover with traffic gap (> 2 windows) should decay previous count to 0")
        void testTrafficGapDecay() {
            RateLimitContext context = createContext("client-gap");

            for (int i = 0; i < 10; i++) {
                rateLimiter.allowRequest(context);
            }

            // Advance clock by 5 minutes (well past 2 window frames)
            clock.advance(Duration.ofMinutes(5));

            // Entire capacity of 10 should be restored
            for (int i = 0; i < 10; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }
            assertFalse(rateLimiter.allowRequest(context).allowed());
        }

        @Test
        @DisplayName("Repeated sliding window transitions over 5 consecutive periods should operate smoothly")
        void testRepeatedTransitions() {
            RateLimitContext context = createContext("client-repeat");

            for (int period = 0; period < 5; period++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
                clock.advance(Duration.ofSeconds(30));
            }
        }
    }

    @Nested
    @DisplayName("3. Multi-Client & Key Isolation")
    class MultiClientIsolation {

        @Test
        @DisplayName("Different client keys should track sliding counters independently")
        void testIndependentClientKeys() {
            RateLimitContext userA = createContext("user-A");
            RateLimitContext userB = createContext("user-B");

            for (int i = 0; i < 10; i++) {
                rateLimiter.allowRequest(userA);
            }
            assertFalse(rateLimiter.allowRequest(userA).allowed());

            // User B is completely unaffected
            RateLimitDecision userBDecision = rateLimiter.allowRequest(userB);
            assertTrue(userBDecision.allowed());
            assertEquals(9L, userBDecision.remainingRequests());
            assertEquals(2, rateLimiter.getActiveWindowCount());
        }
    }

    @Nested
    @DisplayName("4. Multithreaded Concurrency Verification")
    class ConcurrencySafety {

        @Test
        @DisplayName("Exactly 100 concurrent threads against capacity of 50 must result in 50 allowed and 50 rejected")
        void testConcurrentRequestsStrictLimit() throws Exception {
            int capacity = 50;
            int totalThreads = 100;
            properties.setDefaultCapacity(capacity);
            properties.setDefaultWindow(Duration.ofMinutes(1));
            rateLimiter = new SlidingWindowCounterRateLimiter(properties, clock);

            ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
            CountDownLatch readyLatch = new CountDownLatch(totalThreads);
            CountDownLatch startLatch = new CountDownLatch(1);

            AtomicInteger allowedCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < totalThreads; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        RateLimitDecision decision = rateLimiter.allowRequest(createContext("concurrent-user"));
                        if (decision.allowed()) {
                            allowedCount.incrementAndGet();
                        } else {
                            rejectedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }

            readyLatch.await(); // Ensure all 100 threads are waiting at startLatch
            startLatch.countDown(); // Release all 100 threads simultaneously

            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();

            assertEquals(capacity, allowedCount.get(), "Exactly " + capacity + " requests must be permitted");
            assertEquals(totalThreads - capacity, rejectedCount.get(), "Remaining requests must be rejected");
        }
    }

    @Nested
    @DisplayName("5. Edge Cases & Fail-Safe Validation")
    class EdgeCasesAndValidation {

        @Test
        @DisplayName("Null context should throw NullPointerException")
        void testNullContextThrowsException() {
            assertThrows(NullPointerException.class, () -> rateLimiter.allowRequest(null));
        }

        @Test
        @DisplayName("Zero capacity should immediately reject requests")
        void testZeroCapacity() {
            properties.setDefaultCapacity(0);
            rateLimiter = new SlidingWindowCounterRateLimiter(properties, clock);

            RateLimitDecision decision = rateLimiter.allowRequest(createContext("client-zero"));
            assertFalse(decision.allowed());
            assertEquals(0L, decision.limit());
        }

        @Test
        @DisplayName("Negative capacity should immediately reject requests")
        void testNegativeCapacity() {
            properties.setDefaultCapacity(-10);
            rateLimiter = new SlidingWindowCounterRateLimiter(properties, clock);

            RateLimitDecision decision = rateLimiter.allowRequest(createContext("client-negative"));
            assertFalse(decision.allowed());
            assertEquals(0L, decision.limit());
        }

        @Test
        @DisplayName("Zero window duration should throw IllegalArgumentException")
        void testZeroWindowDuration() {
            properties.setDefaultWindow(Duration.ZERO);
            rateLimiter = new SlidingWindowCounterRateLimiter(properties, clock);

            assertThrows(IllegalArgumentException.class, () -> rateLimiter.allowRequest(createContext("client-invalid-window")));
        }

        @Test
        @DisplayName("Backward clock drift should safely reset the window frame")
        void testBackwardClockDrift() {
            RateLimitContext context = createContext("client-drift");
            assertTrue(rateLimiter.allowRequest(context).allowed());

            // Clock shifts backwards by 10 minutes (e.g. NTP sync)
            clock.advance(Duration.ofMinutes(-10));

            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            assertEquals(9L, decision.remainingRequests());
        }
    }

    /**
     * Controllable UTC Clock for test isolation.
     */
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
