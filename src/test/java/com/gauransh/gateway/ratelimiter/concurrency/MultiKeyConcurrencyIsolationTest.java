package com.gauransh.gateway.ratelimiter.concurrency;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.FixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.leakybucket.LeakyBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.SlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.TokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves per-key state isolation under genuine concurrency for every in-memory rate limiting
 * algorithm.
 *
 * <p>The existing per-algorithm suites each contain a same-key contention test, and the
 * multi-client tests are sequential. Neither combination exercises the case a production gateway
 * actually sees: many distinct clients contending on the same shared map at the same moment.
 * A lost map entry, a cross-key state leak, or a non-atomic read-modify-write would all survive
 * the existing tests and fail here.</p>
 *
 * <p><strong>Determinism.</strong> The clock is fixed for the duration of each burst, so no window
 * rolls over, no tokens refill, and no water leaks while the workers run. Every algorithm therefore
 * has exactly one correct answer: precisely {@code CAPACITY} allowed per client and the remainder
 * rejected. Assertions are exact equality, never bounds, so a test cannot pass merely because the
 * scheduler happened to serialize the workers.</p>
 */
@DisplayName("Multi-Key Concurrency Isolation — all in-memory algorithms")
class MultiKeyConcurrencyIsolationTest {

    private static final int CAPACITY = 8;
    private static final int CLIENTS = 6;
    private static final int THREADS_PER_CLIENT = 20;
    private static final int TOTAL_THREADS = CLIENTS * THREADS_PER_CLIENT;
    private static final Instant BASE_TIME = Instant.parse("2026-09-17T12:00:00.000Z");

    static Stream<Arguments> algorithms() {
        return Stream.of(
                Arguments.of("FixedWindow",
                        (BiFunction<RateLimiterProperties, Clock, RateLimiter>) FixedWindowRateLimiter::new),
                Arguments.of("SlidingWindowCounter",
                        (BiFunction<RateLimiterProperties, Clock, RateLimiter>) SlidingWindowCounterRateLimiter::new),
                Arguments.of("SlidingWindowLog",
                        (BiFunction<RateLimiterProperties, Clock, RateLimiter>) SlidingWindowLogRateLimiter::new),
                Arguments.of("TokenBucket",
                        (BiFunction<RateLimiterProperties, Clock, RateLimiter>) TokenBucketRateLimiter::new),
                Arguments.of("LeakyBucket",
                        (BiFunction<RateLimiterProperties, Clock, RateLimiter>) LeakyBucketRateLimiter::new)
        );
    }

    @ParameterizedTest(name = "{0} isolates per-client state under {1} concurrent threads")
    @MethodSource("algorithms")
    @DisplayName("Each client receives exactly its own quota when all clients contend simultaneously")
    void testPerKeyIsolationUnderContention(
            String algorithmName,
            BiFunction<RateLimiterProperties, Clock, RateLimiter> factory) throws Exception {

        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setDefaultWindow(Duration.ofMinutes(1));
        properties.setRefillRate(CAPACITY);

        MutableTestClock clock = new MutableTestClock(BASE_TIME);
        RateLimiter rateLimiter = factory.apply(properties, clock);

        Map<String, AtomicInteger> allowedPerClient = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> rejectedPerClient = new ConcurrentHashMap<>();
        for (int c = 0; c < CLIENTS; c++) {
            allowedPerClient.put(clientId(c), new AtomicInteger());
            rejectedPerClient.put(clientId(c), new AtomicInteger());
        }

        ExecutorService executor = Executors.newFixedThreadPool(TOTAL_THREADS);
        CountDownLatch readyLatch = new CountDownLatch(TOTAL_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(TOTAL_THREADS);

        try {
            // Interleave clients so adjacent threads hit different keys, maximising map contention.
            for (int t = 0; t < THREADS_PER_CLIENT; t++) {
                for (int c = 0; c < CLIENTS; c++) {
                    final String client = clientId(c);
                    futures.add(executor.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();
                        RateLimitDecision decision = rateLimiter.allowRequest(createContext(client, clock));
                        if (decision.allowed()) {
                            allowedPerClient.get(client).incrementAndGet();
                        } else {
                            rejectedPerClient.get(client).incrementAndGet();
                        }
                        return null;
                    }));
                }
            }

            assertTrue(readyLatch.await(30, TimeUnit.SECONDS), "workers failed to reach the start barrier");
            startLatch.countDown();

            // Future.get() both awaits completion and rethrows any worker exception.
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        int totalAllowed = 0;
        for (int c = 0; c < CLIENTS; c++) {
            String client = clientId(c);
            assertEquals(CAPACITY, allowedPerClient.get(client).get(),
                    algorithmName + ": client " + client + " must be allowed exactly its own capacity");
            assertEquals(THREADS_PER_CLIENT - CAPACITY, rejectedPerClient.get(client).get(),
                    algorithmName + ": client " + client + " must reject every request beyond capacity");
            totalAllowed += allowedPerClient.get(client).get();
        }

        assertEquals(CAPACITY * CLIENTS, totalAllowed,
                algorithmName + ": total allowed must equal capacity times client count (no cross-key leakage)");
    }

    private static String clientId(int index) {
        return "client-" + index;
    }

    private static RateLimitContext createContext(String clientId, Clock clock) {
        return new RateLimitContext(
                clientId,
                "/api/v1/resource",
                "GET",
                clock.instant(),
                "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId))
        );
    }
}
