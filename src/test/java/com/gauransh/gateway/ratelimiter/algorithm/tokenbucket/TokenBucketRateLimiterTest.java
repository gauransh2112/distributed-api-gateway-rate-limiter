package com.gauransh.gateway.ratelimiter.algorithm.tokenbucket;

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

@DisplayName("Token Bucket Rate Limiter Complete Test Suite")
class TokenBucketRateLimiterTest {

    private RateLimiterProperties properties;
    private MutableClock clock;
    private TokenBucketRateLimiter rateLimiter;
    private final Instant baseTime = Instant.parse("2026-08-05T12:00:00.000Z");

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(10);
        properties.setRefillRate(2.0); // 2 tokens per second = 1 token every 500ms

        clock = new MutableClock(baseTime);
        rateLimiter = new TokenBucketRateLimiter(properties, clock);
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
    @DisplayName("1. Initial Bucket State & Basic Consumption")
    class InitialStateAndConsumption {

        @Test
        @DisplayName("First request under initial capacity should be allowed with correct remaining tokens")
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
    }

    @Nested
    @DisplayName("2. Token Refill & Time Advancement")
    class TokenRefillOverTime {

        @Test
        @DisplayName("Refill 1 token after 500ms when refill rate is 2 tokens/sec")
        void testPartialRefillAfterDelay() {
            RateLimitContext context = createContext("client-1");

            // Exhaust all 10 tokens
            for (int i = 0; i < 10; i++) {
                assertTrue(rateLimiter.allowRequest(context).allowed());
            }

            // Immediately rejected
            assertFalse(rateLimiter.allowRequest(context).allowed());

            // Advance clock by 500ms -> should generate exactly 1 token (0.5s * 2.0 = 1.0)
            clock.advance(Duration.ofMillis(500));

            RateLimitDecision afterRefill = rateLimiter.allowRequest(context);
            assertTrue(afterRefill.allowed());
            assertEquals(0L, afterRefill.remainingRequests());

            // Immediately next request rejected because bucket is back to 0
            assertFalse(rateLimiter.allowRequest(context).allowed());
        }

        @Test
        @DisplayName("Tokens should cap at maximum capacity after extended idle period")
        void testCapacityCappedAfterLongIdle() {
            RateLimitContext context = createContext("client-1");

            // Exhaust all tokens
            for (int i = 0; i < 10; i++) {
                rateLimiter.allowRequest(context);
            }

            // Advance clock by 100 seconds (would generate 200 tokens if uncapped)
            clock.advance(Duration.ofSeconds(100));

            RateLimitDecision decision = rateLimiter.allowRequest(context);
            assertTrue(decision.allowed());
            // Should be capped at 10 tokens total, so after consuming 1 token, remaining is 9
            assertEquals(9L, decision.remainingRequests());
        }
    }

    @Nested
    @DisplayName("3. Multi-Client State Isolation")
    class MultiClientIsolation {

        @Test
        @DisplayName("Exhausting quota for Client A does not affect Client B")
        void testClientIsolation() {
            RateLimitContext clientA = createContext("client-a");
            RateLimitContext clientB = createContext("client-b");

            // Exhaust Client A
            for (int i = 0; i < 10; i++) {
                assertTrue(rateLimiter.allowRequest(clientA).allowed());
            }
            assertFalse(rateLimiter.allowRequest(clientA).allowed());

            // Client B should still have full 10 tokens available
            RateLimitDecision decisionB = rateLimiter.allowRequest(clientB);
            assertTrue(decisionB.allowed());
            assertEquals(9L, decisionB.remainingRequests());
        }
    }

    @Nested
    @DisplayName("4. Same-Client Concurrency Safety")
    class ConcurrencySafety {

        @Test
        @DisplayName("20 concurrent threads for same client must yield exactly 10 allowed and 10 rejected")
        void testConcurrentRequestsNoOversell() throws Exception {
            int threadCount = 20;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);

            RateLimitContext context = createContext("concurrent-client");
            AtomicInteger allowedCount = new AtomicInteger(0);
            AtomicInteger rejectedCount = new AtomicInteger(0);
            List<Future<?>> futures = new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                futures.add(executor.submit(() -> {
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
                }));
            }

            readyLatch.await();
            startLatch.countDown();
            doneLatch.await();
            executor.shutdown();

            for (Future<?> future : futures) {
                future.get();
            }

            assertEquals(10, allowedCount.get(), "Exactly 10 requests should be allowed");
            assertEquals(10, rejectedCount.get(), "Exactly 10 requests should be rejected");
        }
    }

    @Nested
    @DisplayName("5. Clock Regression & Timestamp Materialization")
    class ClockRegressionAndRejections {

        @Test
        @DisplayName("Backward clock drift should not generate negative elapsed time or corrupt state")
        void testClockRegression() {
            RateLimitContext context = createContext("client-1");

            // Initial request consumes 1 token -> 9 remaining
            RateLimitDecision decision1 = rateLimiter.allowRequest(context);
            assertTrue(decision1.allowed());
            assertEquals(9L, decision1.remainingRequests());

            // Clock moves backward by 5 seconds
            clock.advance(Duration.ofSeconds(-5));

            RateLimitDecision decision2 = rateLimiter.allowRequest(context);
            assertTrue(decision2.allowed());
            // Elapsed is treated as 0, tokens were 9, now 8
            assertEquals(8L, decision2.remainingRequests());
        }

        @Test
        @DisplayName("Rejected requests materialize tokens up to nowMillis and update timestamp")
        void testTimestampMaterializedOnRejection() {
            RateLimitContext context = createContext("client-1");

            // Exhaust all 10 tokens
            for (int i = 0; i < 10; i++) {
                rateLimiter.allowRequest(context);
            }

            // Advance clock by 250ms (generates 0.5 tokens -> total 0.5 tokens < 1.0 -> rejected)
            clock.advance(Duration.ofMillis(250));
            RateLimitDecision rejected1 = rateLimiter.allowRequest(context);
            assertFalse(rejected1.allowed());

            // Advance clock by another 250ms (generates 0.5 tokens -> total 1.0 token -> allowed!)
            clock.advance(Duration.ofMillis(250));
            RateLimitDecision allowedAfterSecond250ms = rateLimiter.allowRequest(context);
            assertTrue(allowedAfterSecond250ms.allowed());
            assertEquals(0L, allowedAfterSecond250ms.remainingRequests());
        }
    }

    @Nested
    @DisplayName("6. Edge Cases & Validation")
    class EdgeCasesAndValidation {

        @Test
        @DisplayName("Null context throws NullPointerException")
        void testNullContextThrows() {
            assertThrows(NullPointerException.class, () -> rateLimiter.allowRequest(null));
        }

        @Test
        @DisplayName("Zero or negative capacity throws IllegalArgumentException")
        void testZeroCapacityThrows() {
            properties.setDefaultCapacity(0);
            TokenBucketRateLimiter customLimiter = new TokenBucketRateLimiter(properties, clock);

            assertThrows(IllegalArgumentException.class, () -> customLimiter.allowRequest(createContext("client-zero")));
        }

        @Test
        @DisplayName("Invalid refill rate throws IllegalArgumentException")
        void testInvalidRefillRateThrows() {
            properties.setRefillRate(0.0);
            TokenBucketRateLimiter customLimiter = new TokenBucketRateLimiter(properties, clock);

            assertThrows(IllegalArgumentException.class, () -> customLimiter.allowRequest(createContext("client-1")));
        }

        @Test
        @DisplayName("Non-finite refill rates (NaN, POSITIVE_INFINITY, NEGATIVE_INFINITY) throw IllegalArgumentException")
        void testNonFiniteRefillRatesThrow() {
            RateLimitContext context = createContext("client-non-finite");

            properties.setRefillRate(Double.NaN);
            TokenBucketRateLimiter nanLimiter = new TokenBucketRateLimiter(properties, clock);
            assertThrows(IllegalArgumentException.class, () -> nanLimiter.allowRequest(context));

            properties.setRefillRate(Double.POSITIVE_INFINITY);
            TokenBucketRateLimiter posInfLimiter = new TokenBucketRateLimiter(properties, clock);
            assertThrows(IllegalArgumentException.class, () -> posInfLimiter.allowRequest(context));

            properties.setRefillRate(Double.NEGATIVE_INFINITY);
            TokenBucketRateLimiter negInfLimiter = new TokenBucketRateLimiter(properties, clock);
            assertThrows(IllegalArgumentException.class, () -> negInfLimiter.allowRequest(context));
        }

        @Test
        @DisplayName("Multiple requests at zero elapsed time (same millisecond) consume tokens sequentially")
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
