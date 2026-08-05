package com.gauransh.gateway.ratelimiter.algorithm.fixedwindow;

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

@DisplayName("Fixed Window Counter Rate Limiter Complete Test Suite")
class FixedWindowRateLimiterTest {

    private RateLimiterProperties properties;
    private MutableClock clock;
    private FixedWindowRateLimiter rateLimiter;
    private final Instant baseTime = Instant.parse("2026-08-05T12:00:00.000Z");

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(5);
        properties.setDefaultWindow(Duration.ofMinutes(1)); // 60,000 ms window

        clock = new MutableClock(baseTime);
        rateLimiter = new FixedWindowRateLimiter(properties, clock);
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
            assertEquals(5L, decision.limit());
            assertEquals(4L, decision.remainingRequests());
            assertEquals(RateLimitConstants.REASON_ALLOWED, decision.reason());
            assertEquals(Duration.ZERO, decision.retryAfter());
            assertNotNull(decision.resetTime());
        }

        @Test
        @DisplayName("Exactly at limit (5th request) should be allowed with zero remaining")
        void testExactlyAtLimit() {
            RateLimitContext context = createContext("client-1");

            for (int i = 1; i <= 4; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }

            RateLimitDecision fifthDecision = rateLimiter.allowRequest(context);
            assertTrue(fifthDecision.allowed());
            assertEquals(5L, fifthDecision.limit());
            assertEquals(0L, fifthDecision.remainingRequests());
        }

        @Test
        @DisplayName("Above limit (6th request) should be rejected with Retry-After populated")
        void testAboveLimit() {
            RateLimitContext context = createContext("client-1");

            for (int i = 1; i <= 5; i++) {
                rateLimiter.allowRequest(context);
            }

            RateLimitDecision sixthDecision = rateLimiter.allowRequest(context);
            assertFalse(sixthDecision.allowed());
            assertEquals(5L, sixthDecision.limit());
            assertEquals(0L, sixthDecision.remainingRequests());
            assertEquals(RateLimitConstants.REASON_EXCEEDED, sixthDecision.reason());
            assertTrue(sixthDecision.retryAfter().toSeconds() > 0);
        }

        @Test
        @DisplayName("Limit = 1 should allow 1st request and block 2nd request")
        void testLimitEqualsOne() {
            properties.setDefaultCapacity(1);
            rateLimiter = new FixedWindowRateLimiter(properties, clock);
            RateLimitContext context = createContext("client-strict");

            RateLimitDecision first = rateLimiter.allowRequest(context);
            assertTrue(first.allowed());
            assertEquals(0L, first.remainingRequests());

            RateLimitDecision second = rateLimiter.allowRequest(context);
            assertFalse(second.allowed());
        }

        @Test
        @DisplayName("Large limit capacity (1,000,000) should evaluate correctly without overflow")
        void testLargeCapacityLimit() {
            properties.setDefaultCapacity(1_000_000L);
            rateLimiter = new FixedWindowRateLimiter(properties, clock);
            RateLimitContext context = createContext("client-large");

            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            assertEquals(1_000_000L, decision.limit());
            assertEquals(999_999L, decision.remainingRequests());
        }
    }

    @Nested
    @DisplayName("2. Window Resets & Time Transitions")
    class WindowTransitions {

        @Test
        @DisplayName("Counter should reset automatically when advancing past window duration")
        void testWindowReset() {
            RateLimitContext context = createContext("client-reset");

            // Exhaust limit
            for (int i = 1; i <= 5; i++) {
                rateLimiter.allowRequest(context);
            }
            assertFalse(rateLimiter.allowRequest(context).allowed());

            // Advance clock past 1-minute window
            clock.advance(Duration.ofSeconds(61));

            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            assertEquals(4L, decision.remainingRequests());
        }

        @Test
        @DisplayName("Repeated resets over 5 consecutive window transitions should function seamlessly")
        void testRepeatedResets() {
            RateLimitContext context = createContext("client-repeat");

            for (int window = 0; window < 5; window++) {
                // Exhaust capacity
                for (int i = 0; i < 5; i++) {
                    assertTrue(rateLimiter.allowRequest(context).allowed());
                }
                assertFalse(rateLimiter.allowRequest(context).allowed());

                // Move to next window
                clock.advance(Duration.ofMinutes(1));
            }
        }

        @Test
        @DisplayName("Retry-After calculation should decrease accurately as window reset time approaches")
        void testRetryAfterProgression() {
            RateLimitContext context = createContext("client-retry");

            for (int i = 0; i < 5; i++) {
                rateLimiter.allowRequest(context);
            }

            // Immediately after exhaustion -> Retry-After ~60s
            RateLimitDecision d1 = rateLimiter.allowRequest(context);
            assertEquals(60L, d1.retryAfter().toSeconds());

            // Advance time by 45 seconds -> Retry-After ~15s
            clock.advance(Duration.ofSeconds(45));
            RateLimitDecision d2 = rateLimiter.allowRequest(context);
            assertEquals(15L, d2.retryAfter().toSeconds());
        }
    }

    @Nested
    @DisplayName("3. Multi-Client & Key Isolation")
    class MultiClientIsolation {

        @Test
        @DisplayName("Different client keys should track counters independently")
        void testIndependentClientKeys() {
            RateLimitContext userA = createContext("user-A");
            RateLimitContext userB = createContext("user-B");

            for (int i = 0; i < 5; i++) {
                rateLimiter.allowRequest(userA);
            }
            assertFalse(rateLimiter.allowRequest(userA).allowed());

            // User B is unaffected
            RateLimitDecision userBDecision = rateLimiter.allowRequest(userB);
            assertTrue(userBDecision.allowed());
            assertEquals(4L, userBDecision.remainingRequests());
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
            rateLimiter = new FixedWindowRateLimiter(properties, clock);

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

            readyLatch.await(); // Ensure all 100 threads are created and waiting at startLatch
            startLatch.countDown(); // Dispatch all 100 threads concurrently

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
            rateLimiter = new FixedWindowRateLimiter(properties, clock);

            RateLimitDecision decision = rateLimiter.allowRequest(createContext("client-zero"));
            assertFalse(decision.allowed());
            assertEquals(0L, decision.limit());
        }

        @Test
        @DisplayName("Negative capacity should immediately reject requests")
        void testNegativeCapacity() {
            properties.setDefaultCapacity(-10);
            rateLimiter = new FixedWindowRateLimiter(properties, clock);

            RateLimitDecision decision = rateLimiter.allowRequest(createContext("client-negative"));
            assertFalse(decision.allowed());
            assertEquals(0L, decision.limit());
        }

        @Test
        @DisplayName("Zero window duration should throw IllegalArgumentException")
        void testZeroWindowDuration() {
            properties.setDefaultWindow(Duration.ZERO);
            rateLimiter = new FixedWindowRateLimiter(properties, clock);

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
            assertEquals(4L, decision.remainingRequests());
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
