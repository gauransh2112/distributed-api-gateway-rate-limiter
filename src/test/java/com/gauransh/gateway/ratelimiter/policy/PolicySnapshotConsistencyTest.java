package com.gauransh.gateway.ratelimiter.policy;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.FixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.leakybucket.LeakyBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.SlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.TokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterAlgorithm;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.model.RateLimitPolicy;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitPolicyResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that a single request evaluation resolves its policy exactly once and evaluates against
 * that one snapshot.
 *
 * <p>Each algorithm previously called {@link RateLimitPolicyResolver#resolvePolicy} twice per
 * request — once for capacity and once for the window or rate. A resolver backed by live-reloading
 * configuration could therefore return one policy version for the first call and another for the
 * second, producing a decision that matched no configured policy: capacity from version A combined
 * with a window from version B.</p>
 *
 * <p>This is a consistency defect, not a thread-safety defect, and the fix is to resolve once per
 * request rather than to lock. These tests pin that guarantee down against every algorithm.</p>
 */
@DisplayName("Policy snapshot consistency — one resolution per request")
class PolicySnapshotConsistencyTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-19T12:00:00.000Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(BASE_TIME, ZoneOffset.UTC);

    /** Capacity and window of the first policy a shifting resolver hands out. */
    private static final long FIRST_CAPACITY = 5L;
    private static final Duration FIRST_WINDOW = Duration.ofSeconds(60);

    /** A deliberately incompatible second policy, returned from the second call onwards. */
    private static final long SECOND_CAPACITY = 500L;
    private static final Duration SECOND_WINDOW = Duration.ofSeconds(1);

    static Stream<Arguments> algorithms() {
        return Stream.of(
                Arguments.of("FixedWindow", (LimiterFactory) FixedWindowRateLimiter::new),
                Arguments.of("SlidingWindowCounter", (LimiterFactory) SlidingWindowCounterRateLimiter::new),
                Arguments.of("SlidingWindowLog", (LimiterFactory) SlidingWindowLogRateLimiter::new),
                Arguments.of("TokenBucket", (LimiterFactory) TokenBucketRateLimiter::new),
                Arguments.of("LeakyBucket", (LimiterFactory) LeakyBucketRateLimiter::new)
        );
    }

    @ParameterizedTest(name = "{0} resolves the policy exactly once per request")
    @MethodSource("algorithms")
    @DisplayName("Test 1 — the resolver is invoked exactly once per allowRequest")
    void testSinglePolicyResolutionPerRequest(String algorithmName, LimiterFactory factory) {
        CountingPolicyResolver resolver = new CountingPolicyResolver(stablePolicy());
        RateLimiter rateLimiter = factory.create(defaultProperties(), new DefaultRateLimitKeyResolver(), resolver, FIXED_CLOCK);

        rateLimiter.allowRequest(context("client-a"));

        assertEquals(1, resolver.invocations(),
                algorithmName + ": one request must resolve the policy exactly once");

        rateLimiter.allowRequest(context("client-a"));
        rateLimiter.allowRequest(context("client-b"));

        assertEquals(3, resolver.invocations(),
                algorithmName + ": resolution count must track requests exactly, one to one");
    }

    @ParameterizedTest(name = "{0} evaluates one request against one policy snapshot")
    @MethodSource("algorithms")
    @DisplayName("Test 2 — a shifting resolver cannot split one request across two policy versions")
    void testConsistentPolicySnapshot(String algorithmName, LimiterFactory factory) {
        // Returns the restrictive policy once, then the permissive one forever after. Under the
        // previous two-call implementation the single request would have mixed the two.
        ShiftingPolicyResolver resolver = new ShiftingPolicyResolver(
                new RateLimitPolicy("first", FIRST_CAPACITY, FIRST_WINDOW, 0L,
                        RateLimiterAlgorithm.NO_OP, true, 1, "PER_CLIENT"),
                new RateLimitPolicy("second", SECOND_CAPACITY, SECOND_WINDOW, 0L,
                        RateLimiterAlgorithm.NO_OP, true, 1, "PER_CLIENT"));

        RateLimiter rateLimiter = factory.create(defaultProperties(), new DefaultRateLimitKeyResolver(), resolver, FIXED_CLOCK);

        RateLimitDecision decision = rateLimiter.allowRequest(context("client-a"));

        assertEquals(1, resolver.invocations(),
                algorithmName + ": a mixed snapshot is only possible if the resolver is called twice");
        assertTrue(decision.allowed(), algorithmName + ": the first request under either policy is allowed");

        // The decision must be attributable to the first policy alone. Algorithms that publish a
        // limit expose it directly; the rest are covered by the invocation count above.
        if (decision.limit() != null) {
            assertEquals(FIRST_CAPACITY, decision.limit(),
                    algorithmName + ": the decision must carry the capacity of the single resolved snapshot");
        }
    }

    @ParameterizedTest(name = "{0} resolves once per request under concurrency")
    @MethodSource("algorithms")
    @DisplayName("Test 3 — resolution stays one-per-request when requests run concurrently")
    void testSingleResolutionUnderConcurrency(String algorithmName, LimiterFactory factory) throws Exception {
        int threads = 40;
        CountingPolicyResolver resolver = new CountingPolicyResolver(stablePolicy());
        RateLimiter rateLimiter = factory.create(defaultProperties(), new DefaultRateLimitKeyResolver(), resolver, FIXED_CLOCK);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                final String client = "client-" + i;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    rateLimiter.allowRequest(context(client));
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

        assertEquals(threads, resolver.invocations(),
                algorithmName + ": " + threads + " concurrent requests must produce exactly "
                        + threads + " resolutions — no duplicates, none lost");
    }

    private static RateLimiterProperties defaultProperties() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultCapacity(100);
        properties.setDefaultWindow(Duration.ofMinutes(1));
        properties.setRefillRate(10.0);
        return properties;
    }

    private static RateLimitPolicy stablePolicy() {
        return new RateLimitPolicy("stable", FIRST_CAPACITY, FIRST_WINDOW, 0L,
                RateLimiterAlgorithm.NO_OP, true, 1, "PER_CLIENT");
    }

    private static RateLimitContext context(String clientId) {
        return new RateLimitContext(
                clientId,
                "/api/v1/resource",
                "GET",
                BASE_TIME,
                "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId))
        );
    }

    /**
     * Constructs an algorithm under test through its full injection constructor, matching how
     * {@code RateLimiterConfiguration} wires each limiter in production.
     */
    @FunctionalInterface
    private interface LimiterFactory {
        RateLimiter create(RateLimiterProperties properties,
                           RateLimitKeyResolver keyResolver,
                           RateLimitPolicyResolver policyResolver,
                           Clock clock);
    }

    /** Counts resolutions; safe to read after the workers have been joined. */
    private static final class CountingPolicyResolver implements RateLimitPolicyResolver {
        private final RateLimitPolicy policy;
        private final AtomicInteger invocations = new AtomicInteger();

        private CountingPolicyResolver(RateLimitPolicy policy) {
            this.policy = policy;
        }

        @Override
        public RateLimitPolicy resolvePolicy(RateLimitContext context) {
            invocations.incrementAndGet();
            return policy;
        }

        int invocations() {
            return invocations.get();
        }
    }

    /** Stands in for a live-reloading policy store: the answer changes between calls. */
    private static final class ShiftingPolicyResolver implements RateLimitPolicyResolver {
        private final RateLimitPolicy first;
        private final RateLimitPolicy subsequent;
        private final AtomicInteger invocations = new AtomicInteger();

        private ShiftingPolicyResolver(RateLimitPolicy first, RateLimitPolicy subsequent) {
            this.first = first;
            this.subsequent = subsequent;
        }

        @Override
        public RateLimitPolicy resolvePolicy(RateLimitContext context) {
            return invocations.getAndIncrement() == 0 ? first : subsequent;
        }

        int invocations() {
            return invocations.get();
        }
    }
}
