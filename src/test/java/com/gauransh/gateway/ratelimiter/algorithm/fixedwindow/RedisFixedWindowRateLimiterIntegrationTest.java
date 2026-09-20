package com.gauransh.gateway.ratelimiter.algorithm.fixedwindow;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.service.RedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Live integration tests for {@link RedisFixedWindowRateLimiter}.
 *
 * <p>These prove the property the in-memory algorithms cannot have: the counter lives in Redis, so
 * two independent limiter instances — standing in for two Gateway instances — share one quota. An
 * in-memory limiter would grant each instance its own quota and fail these tests by construction.</p>
 *
 * <p>Executes against Redis on port 6379 when available, using the {@link EnabledIf} socket probe
 * established in earlier sprints so offline unit runs stay independent of Redis.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
@DisplayName("Redis Fixed Window Rate Limiter — live Redis")
class RedisFixedWindowRateLimiterIntegrationTest {

    private static final long CAPACITY = 5L;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Instant BASE_TIME = Instant.parse("2026-09-20T12:00:00.000Z");

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RateLimiterProperties properties;
    private MutableClock clock;
    private RedisFixedWindowRateLimiter rateLimiter;
    private String clientId;

    static boolean isRedisAvailable() {
        try (Socket socket = new Socket("localhost", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setDefaultWindow(WINDOW);
        clock = new MutableClock(BASE_TIME);
        rateLimiter = newLimiter(clock);
        // A fresh client per test keeps runs independent without relying on key expiry.
        clientId = "it-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("*:" + RedisFixedWindowRateLimiter.KEY_MODULE + ":"
                + RedisFixedWindowRateLimiter.KEY_RESOURCE + ":" + clientId.toLowerCase() + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private RedisFixedWindowRateLimiter newLimiter(Clock limiterClock) {
        return new RedisFixedWindowRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), null, limiterClock, redisService, keyBuilder);
    }

    private RateLimitContext context() {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", clock.instant(), "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    private String currentKey() {
        long windowStartSeconds = (clock.instant().toEpochMilli() / WINDOW.toMillis()) * WINDOW.toMillis() / 1000L;
        return keyBuilder.buildKey(RedisFixedWindowRateLimiter.KEY_MODULE,
                RedisFixedWindowRateLimiter.KEY_RESOURCE, clientId + ":" + windowStartSeconds);
    }

    @Test
    @DisplayName("Requests increment a counter that actually lives in Redis")
    void testCounterIsStoredInRedis() {
        rateLimiter.allowRequest(context());
        rateLimiter.allowRequest(context());
        rateLimiter.allowRequest(context());

        assertThat(redisService.get(currentKey())).contains("3");
    }

    @Test
    @DisplayName("The generated key follows the contract schema and exists in Redis")
    void testKeySchema() {
        rateLimiter.allowRequest(context());

        String key = currentKey();
        assertThat(key).isEqualTo("dev:ratelimiter:fixed:" + clientId.toLowerCase() + ":"
                + BASE_TIME.getEpochSecond());
        assertThat(redisService.exists(key)).isTrue();
    }

    @Test
    @DisplayName("The limit is enforced: exactly capacity allowed, the rest rejected")
    void testLimitEnforced() {
        int allowed = 0;
        int rejected = 0;
        for (int i = 0; i < CAPACITY + 3; i++) {
            if (rateLimiter.allowRequest(context()).allowed()) {
                allowed++;
            } else {
                rejected++;
            }
        }

        assertThat(allowed).isEqualTo((int) CAPACITY);
        assertThat(rejected).isEqualTo(3);
    }

    @Test
    @DisplayName("TTL is applied as window + 10s and is not extended by later requests")
    void testTtlAppliedOnceAndNotExtended() {
        rateLimiter.allowRequest(context());
        Optional<Duration> afterFirst = redisService.getTtl(currentKey());

        // Later requests in the same window must not push the expiry out — that would turn a
        // fixed window into a sliding one.
        rateLimiter.allowRequest(context());
        Optional<Duration> afterSecond = redisService.getTtl(currentKey());

        assertThat(afterFirst).isPresent();
        assertThat(afterFirst.get()).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(70));
        assertThat(afterSecond).isPresent();
        assertThat(afterSecond.get()).isLessThanOrEqualTo(afterFirst.get());
    }

    @Test
    @DisplayName("Crossing the window boundary restores the full quota under a new key")
    void testWindowRollover() {
        for (int i = 0; i < CAPACITY; i++) {
            rateLimiter.allowRequest(context());
        }
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();
        String firstWindowKey = currentKey();

        clock.advance(WINDOW);

        RateLimitDecision afterRollover = rateLimiter.allowRequest(context());
        assertThat(afterRollover.allowed()).isTrue();
        assertThat(afterRollover.remainingRequests()).isEqualTo(CAPACITY - 1);
        assertThat(currentKey()).isNotEqualTo(firstWindowKey);
    }

    @Test
    @DisplayName("Two independent limiter instances share one quota through Redis")
    void testDistributedQuotaIsShared() {
        RedisFixedWindowRateLimiter instanceA = newLimiter(clock);
        RedisFixedWindowRateLimiter instanceB = newLimiter(clock);

        int allowed = 0;
        // Alternate between the two "Gateway instances". If each held its own counter, this would
        // admit 2 x CAPACITY requests instead of CAPACITY.
        for (int i = 0; i < CAPACITY + 5; i++) {
            RedisFixedWindowRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
            if (instance.allowRequest(context()).allowed()) {
                allowed++;
            }
        }

        assertThat(allowed)
                .as("a client must not obtain a separate quota per Gateway instance")
                .isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("Quota exhausted on one instance is already exhausted on the other")
    void testExhaustionVisibleAcrossInstances() {
        RedisFixedWindowRateLimiter instanceA = newLimiter(clock);
        RedisFixedWindowRateLimiter instanceB = newLimiter(clock);

        for (int i = 0; i < CAPACITY; i++) {
            assertThat(instanceA.allowRequest(context()).allowed()).isTrue();
        }

        RateLimitDecision onB = instanceB.allowRequest(context());
        assertThat(onB.allowed()).isFalse();
        assertThat(onB.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
    }

    @Test
    @DisplayName("Concurrent requests across two instances admit exactly the configured quota")
    void testConcurrentRequestsAcrossInstances() throws Exception {
        int threads = 40;
        RedisFixedWindowRateLimiter instanceA = newLimiter(clock);
        RedisFixedWindowRateLimiter instanceB = newLimiter(clock);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                RedisFixedWindowRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    if (instance.allowRequest(context()).allowed()) {
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

        assertThat(allowed.get())
                .as("Redis-side atomicity must admit exactly the quota, with no lost updates")
                .isEqualTo((int) CAPACITY);
        assertThat(rejected.get()).isEqualTo(threads - (int) CAPACITY);
        assertThat(redisService.get(currentKey())).contains(String.valueOf(threads));
    }

    /** Deterministic clock so window boundaries are controlled rather than observed. */
    private static final class MutableClock extends Clock {
        private volatile Instant instant;

        private MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advance(Duration amount) {
            this.instant = this.instant.plus(amount);
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
