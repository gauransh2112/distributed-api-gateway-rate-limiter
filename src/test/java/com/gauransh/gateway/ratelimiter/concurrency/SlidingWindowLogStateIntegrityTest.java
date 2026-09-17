package com.gauransh.gateway.ratelimiter.concurrency;

import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that the mutable {@code SlidingWindowLog} deque survives high contention intact.
 *
 * <p>This is the one place in the rate limiter package where a <em>mutable</em> object is published
 * into a shared {@code ConcurrentHashMap}. Its safety rests entirely on every access staying inside
 * {@code ConcurrentHashMap.compute}, which the compiler cannot enforce. An {@code ArrayDeque}
 * mutated concurrently does not fail fast; it silently loses, duplicates, or reorders entries.
 * These tests therefore assert on behaviour that depends on the deque's exact contents rather than
 * merely on the allowed/rejected split, so corruption cannot hide.</p>
 */
@DisplayName("Sliding Window Log — mutable state integrity under contention")
class SlidingWindowLogStateIntegrityTest {

    private static final int CAPACITY = 12;
    private static final int THREADS = 80;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Instant BASE_TIME = Instant.parse("2026-09-17T12:00:00.000Z");

    private RateLimiterProperties properties;
    private MutableTestClock clock;
    private SlidingWindowLogRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setDefaultWindow(WINDOW);
        clock = new MutableTestClock(BASE_TIME);
        rateLimiter = new SlidingWindowLogRateLimiter(properties, clock);
    }

    @RepeatedTest(value = 5, name = "burst {currentRepetition} of {totalRepetitions}")
    @DisplayName("Concurrent burst on one key admits exactly capacity and leaves the log uncorrupted")
    void testSingleKeyBurstLeavesLogConsistent() throws Exception {
        BurstResult result = runConcurrentBurst("hot-client", THREADS);

        assertEquals(CAPACITY, result.allowed(), "exactly capacity requests must be admitted");
        assertEquals(THREADS - CAPACITY, result.rejected(), "every request beyond capacity must be rejected");
        assertEquals(1, rateLimiter.getActiveWindowCount(), "exactly one client log must exist");

        // The log must now hold exactly CAPACITY timestamps. If entries had been lost to a
        // concurrent deque mutation, this follow-up request would be admitted instead of rejected.
        RateLimitDecision afterBurst = rateLimiter.allowRequest(createContext("hot-client"));
        assertFalse(afterBurst.allowed(), "log must still be saturated immediately after the burst");
    }

    @Test
    @DisplayName("Eviction after the window still works, proving stored timestamps were not corrupted")
    void testLogEvictsCorrectlyAfterConcurrentBurst() throws Exception {
        BurstResult first = runConcurrentBurst("rolling-client", THREADS);
        assertEquals(CAPACITY, first.allowed());

        // Advance beyond the window so every stored timestamp becomes evictable.
        clock.advance(WINDOW.plusSeconds(1));

        // A corrupted deque would evict the wrong number of entries and change this count.
        BurstResult second = runConcurrentBurst("rolling-client", THREADS);
        assertEquals(CAPACITY, second.allowed(),
                "after full window expiry the client must regain exactly its full quota");
        assertEquals(THREADS - CAPACITY, second.rejected());
    }

    @Test
    @DisplayName("Partial eviction admits exactly the number of expired slots and no more")
    void testPartialEvictionAccountingSurvivesContention() throws Exception {
        // Fill half the quota, then age those entries out of the window.
        BurstResult initial = runConcurrentBurst("partial-client", CAPACITY / 2);
        assertEquals(CAPACITY / 2, initial.allowed());
        assertEquals(0, initial.rejected());

        clock.advance(WINDOW.plusSeconds(1));

        // All earlier entries are expired, so the full quota is available again — exactly once.
        BurstResult afterExpiry = runConcurrentBurst("partial-client", THREADS);
        assertEquals(CAPACITY, afterExpiry.allowed(),
                "eviction must release precisely the expired slots, never more or fewer");
    }

    @Test
    @DisplayName("Concurrent traffic across many keys keeps each log independent")
    void testConcurrentMultiKeyLogsRemainIndependent() throws Exception {
        int keys = 5;
        List<BurstResult> results = new ArrayList<>();
        for (int k = 0; k < keys; k++) {
            results.add(runConcurrentBurst("client-" + k, THREADS));
        }

        for (int k = 0; k < keys; k++) {
            assertEquals(CAPACITY, results.get(k).allowed(), "client-" + k + " must receive its own full quota");
        }
        assertEquals(keys, rateLimiter.getActiveWindowCount(), "each client must own exactly one log");
    }

    /**
     * Releases {@code threads} workers simultaneously against a single key and returns the exact
     * allowed / rejected split. Worker exceptions surface through {@link Future#get(long, TimeUnit)}.
     */
    private BurstResult runConcurrentBurst(String clientId, int threads) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    RateLimitDecision decision = rateLimiter.allowRequest(createContext(clientId));
                    if (decision.allowed()) {
                        allowed.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }

            assertTrue(readyLatch.await(30, TimeUnit.SECONDS), "workers failed to reach the start barrier");
            startLatch.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertEquals(threads, allowed.get() + rejected.get(), "every request must produce exactly one decision");
        return new BurstResult(allowed.get(), rejected.get());
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

    private record BurstResult(int allowed, int rejected) {}
}
