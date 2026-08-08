package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog;

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

@DisplayName("Sliding Window Log Rate Limiter Complete Test Suite")
class SlidingWindowLogRateLimiterTest {

    private RateLimiterProperties properties;
    private MutableClock clock;
    private SlidingWindowLogRateLimiter rateLimiter;
    private final Instant baseTime = Instant.parse("2026-08-05T12:00:00.000Z");

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(10);
        properties.setDefaultWindow(Duration.ofMinutes(1)); // 60,000 ms window

        clock = new MutableClock(baseTime);
        rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);
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
            assertTrue(eleventhDecision.retryAfter().toMillis() > 0);
        }
    }

    @Nested
    @DisplayName("2. Exact Sliding Log & Boundary Semantics")
    class SlidingWindowLogBoundarySemantics {

        @Test
        @DisplayName("Timestamp exactly at lower boundary (now - window) is ACTIVE (not expired)")
        void testExactLowerBoundaryIsActive() {
            RateLimitContext context = createContext("client-boundary");
            properties.setDefaultCapacity(2);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            // Request 1 at t = 0 ms
            assertTrue(rateLimiter.allowRequest(context).allowed());

            // Advance clock to t = 60,000 ms (window = 60,000 ms)
            // Cutoff = 60,000 - 60,000 = 0 ms. Timestamp 0 ms is EQUAL to cutoff, so it is ACTIVE.
            clock.advance(Duration.ofMinutes(1));

            // Request 2 at t = 60,000 ms. Active count should be 1 (from t=0) + 1 (new) = 2.
            RateLimitDecision decision2 = rateLimiter.allowRequest(context);
            assertTrue(decision2.allowed());
            assertEquals(0L, decision2.remainingRequests());

            // Request 3 at t = 60,000 ms should be REJECTED because active count is 2 (at t=0 and t=60000).
            RateLimitDecision decision3 = rateLimiter.allowRequest(context);
            assertFalse(decision3.allowed());
        }

        @Test
        @DisplayName("Timestamp one unit outside lower boundary (now - window - 1 ms) is EXPIRED")
        void testTimestampOneUnitOutsideBoundaryIsExpired() {
            RateLimitContext context = createContext("client-boundary-expired");
            properties.setDefaultCapacity(2);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            // Request 1 at t = 0 ms
            assertTrue(rateLimiter.allowRequest(context).allowed());

            // Advance clock to t = 60,001 ms (cutoff = 1 ms).
            // Timestamp 0 ms is < cutoff (1 ms), so it is EXPIRED!
            clock.advance(Duration.ofMillis(60001));

            // Request 2 at t = 60,001 ms. t=0 is expired, so active count is 1.
            RateLimitDecision decision2 = rateLimiter.allowRequest(context);
            assertTrue(decision2.allowed());
            assertEquals(1L, decision2.remainingRequests());
        }

        @Test
        @DisplayName("Boundary burst prevention: Fixed Window 2x burst attack is strictly REJECTED")
        void testPreventsBoundarySpikeBurst() {
            RateLimitContext context = createContext("client-burst");
            properties.setDefaultCapacity(5);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            // Send 5 requests at end of window 1 (t = 59,900 ms)
            clock.advance(Duration.ofMillis(59900));
            for (int i = 0; i < 5; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }

            // Advance clock by 200 ms to start of window 2 (t = 60,100 ms)
            clock.advance(Duration.ofMillis(200));

            // In Fixed Window, this would reset and allow 5 more requests.
            // In Sliding Window Log, all 5 previous requests are within the 60,000 ms rolling frame [100 ms, 60100 ms].
            // Therefore, immediate next request MUST BE REJECTED!
            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertFalse(decision.allowed(), "Sliding window log must prevent 2x boundary burst attack");
        }
    }

    @Nested
    @DisplayName("3. Eviction & State Cleanup")
    class EvictionAndCleanup {

        @Test
        @DisplayName("Old timestamps should evict gradually as rolling window moves forward")
        void testGradualEviction() {
            RateLimitContext context = createContext("client-evict");
            properties.setDefaultCapacity(2);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            // Request 1 at t = 0 ms
            assertTrue(rateLimiter.allowRequest(context).allowed());

            // Request 2 at t = 10,000 ms
            clock.advance(Duration.ofSeconds(10));
            assertTrue(rateLimiter.allowRequest(context).allowed());

            // Capacity full (2/2). Request 3 at t = 20,000 ms rejected.
            clock.advance(Duration.ofSeconds(10));
            assertFalse(rateLimiter.allowRequest(context).allowed());

            // Advance to t = 60,001 ms. Request 1 (t=0) is now expired (> 60,000 ms ago).
            // Cutoff = 60,001 - 60,000 = 1 ms. Request 2 (t=10,000) is still active.
            // So active count drops to 1. New request should be ALLOWED.
            clock.advance(Duration.ofMillis(40001)); // t = 60,001 ms
            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
        }

        @Test
        @DisplayName("Active window count reflects memory state and storage cleanup")
        void testActiveWindowCountAndClearStorage() {
            rateLimiter.allowRequest(createContext("client-1"));
            rateLimiter.allowRequest(createContext("client-2"));

            assertEquals(2, rateLimiter.getActiveWindowCount());

            rateLimiter.clearStorage();
            assertEquals(0, rateLimiter.getActiveWindowCount());
        }
    }

    @Nested
    @DisplayName("4. Multi-Client Isolation")
    class MultiClientIsolation {

        @Test
        @DisplayName("Quota limits should be tracked independently per client key")
        void testClientIsolation() {
            RateLimitContext clientA = createContext("client-A");
            RateLimitContext clientB = createContext("client-B");

            // Exhaust client A quota (10 requests)
            for (int i = 0; i < 10; i++) {
                assertTrue(rateLimiter.allowRequest(clientA).allowed());
            }

            // Client A 11th request is rejected
            assertFalse(rateLimiter.allowRequest(clientA).allowed());

            // Client B should still have full quota available
            RateLimitDecision decisionB = rateLimiter.allowRequest(clientB);
            assertTrue(decisionB.allowed());
            assertEquals(9L, decisionB.remainingRequests());
        }
    }

    @Nested
    @DisplayName("5. Concurrency & Thread Safety")
    class ConcurrencySafety {

        @Test
        @DisplayName("Concurrent request burst should enforce exact capacity without race conditions")
        void testConcurrentBurst() throws Exception {
            int threadCount = 20;
            int capacity = 10;
            properties.setDefaultCapacity(capacity);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            AtomicInteger allowedCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);

            RateLimitContext context = createContext("concurrent-client");

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        RateLimitDecision decision = rateLimiter.allowRequest(context);
                        if (decision.allowed()) {
                            allowedCount.incrementAndGet();
                        } else {
                            rejectedCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            assertEquals(capacity, allowedCount.get(), "Allowed count must match capacity exactly under concurrency");
            assertEquals(threadCount - capacity, rejectedCount.get(), "Excess requests must be rejected under concurrency");
        }
    }

    @Nested
    @DisplayName("6. High Request Volume & Long Idle Periods")
    class HighRequestVolumeAndIdle {

        @Test
        @DisplayName("High volume across multiple clients maintains strict isolation and quota limits")
        void testHighVolumeMultipleClients() {
            int totalClients = 50;
            int requestsPerClient = 15; // Capacity is 10
            properties.setDefaultCapacity(10);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            for (int c = 0; c < totalClients; c++) {
                String clientId = "high-vol-client-" + c;
                RateLimitContext context = createContext(clientId);

                int allowed = 0;
                int rejected = 0;

                for (int req = 0; req < requestsPerClient; req++) {
                    if (rateLimiter.allowRequest(context).allowed()) {
                        allowed++;
                    } else {
                        rejected++;
                    }
                }

                assertEquals(10, allowed, "Client " + clientId + " must have allowed count equal to capacity");
                assertEquals(5, rejected, "Client " + clientId + " must have rejected count equal to excess requests");
            }

            assertEquals(totalClients, rateLimiter.getActiveWindowCount());
        }

        @Test
        @DisplayName("Long idle period completely resets client log state")
        void testLongIdlePeriodReset() {
            RateLimitContext context = createContext("client-idle");
            properties.setDefaultCapacity(5);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            // Exhaust capacity
            for (int i = 0; i < 5; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }
            assertFalse(rateLimiter.allowRequest(context).allowed());

            // Long idle period: advance clock by 24 hours
            clock.advance(Duration.ofHours(24));

            // Client should have full capacity available again
            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            assertEquals(4L, decision.remainingRequests());
        }
    }

    @Nested
    @DisplayName("7. Edge Cases & Validation")
    class EdgeCasesAndValidation {

        @Test
        @DisplayName("Null context should throw NullPointerException")
        void testNullContext() {
            assertThrows(NullPointerException.class, () -> rateLimiter.allowRequest(null));
        }

        @Test
        @DisplayName("Zero capacity should immediately reject all requests")
        void testZeroCapacity() {
            properties.setDefaultCapacity(0);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            RateLimitDecision decision = rateLimiter.allowRequest(createContext("client-zero-capacity"));
            assertFalse(decision.allowed());
            assertEquals(0L, decision.limit());
            assertEquals(0L, decision.remainingRequests());
        }

        @Test
        @DisplayName("Zero window duration should throw IllegalArgumentException")
        void testZeroWindowDuration() {
            properties.setDefaultWindow(Duration.ZERO);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            assertThrows(IllegalArgumentException.class, () -> rateLimiter.allowRequest(createContext("client-invalid-window")));
        }

        @Test
        @DisplayName("Backward clock drift should reset log to preserve monotonic timestamp ordering, and handle subsequent clock advancement correctly")
        void testBackwardClockDriftMonotonicOrderingAndAdvancement() {
            RateLimitContext context = createContext("client-drift");
            properties.setDefaultCapacity(2);
            rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);

            // Request 1 at t = 1000 ms (relative to baseTime)
            clock.advance(Duration.ofMillis(1000));
            assertTrue(rateLimiter.allowRequest(context).allowed());

            // Clock moves backward to t = 900 ms
            clock.advance(Duration.ofMillis(-100));
            RateLimitDecision decisionDrift = rateLimiter.allowRequest(context);
            assertTrue(decisionDrift.allowed());
            // Log was reset at t = 900 ms to preserve monotonic ordering, so remaining is 1
            assertEquals(1L, decisionDrift.remainingRequests());

            // Advance clock by 60,001 ms to t = 60,901 ms (cutoff = 60,901 - 60,000 = 901 ms).
            // Timestamp 900 ms is < 901 ms, so it is evicted correctly!
            clock.advance(Duration.ofMillis(60001));
            RateLimitDecision decisionAdv = rateLimiter.allowRequest(context);
            assertTrue(decisionAdv.allowed());
            assertEquals(1L, decisionAdv.remainingRequests());
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
