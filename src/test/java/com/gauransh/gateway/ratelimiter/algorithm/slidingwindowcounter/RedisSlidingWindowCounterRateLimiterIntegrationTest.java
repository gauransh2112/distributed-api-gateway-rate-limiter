package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter;

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
 * Live integration tests for {@link RedisSlidingWindowCounterRateLimiter}.
 *
 * <p>These verify the two properties that separate this algorithm from Fixed Window: the weighted
 * carry-over from the previous window actually suppresses boundary bursts, and the whole
 * read-calculate-decide-write sequence stays atomic across independent limiter instances sharing
 * one Redis.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
@DisplayName("Redis Sliding Window Counter Rate Limiter — live Redis")
class RedisSlidingWindowCounterRateLimiterIntegrationTest {

    private static final long CAPACITY = 10L;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Instant BASE_TIME = Instant.parse("2026-09-21T12:00:00.000Z");

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RateLimiterProperties properties;
    private MutableClock clock;
    private RedisSlidingWindowCounterRateLimiter rateLimiter;
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
        rateLimiter = newLimiter();
        clientId = "swc-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        Set<String> keys = redisTemplate.keys("*:" + RedisSlidingWindowCounterRateLimiter.KEY_MODULE + ":"
                + RedisSlidingWindowCounterRateLimiter.KEY_RESOURCE + ":" + clientId.toLowerCase() + ":*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private RedisSlidingWindowCounterRateLimiter newLimiter() {
        return new RedisSlidingWindowCounterRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), null, clock, redisService, keyBuilder);
    }

    private RateLimitContext context() {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", clock.instant(), "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    private String windowKey(Instant windowStart) {
        return keyBuilder.buildKey(RedisSlidingWindowCounterRateLimiter.KEY_MODULE,
                RedisSlidingWindowCounterRateLimiter.KEY_RESOURCE,
                clientId + ":" + windowStart.getEpochSecond());
    }

    private int consume(int attempts) {
        int allowed = 0;
        for (int i = 0; i < attempts; i++) {
            if (rateLimiter.allowRequest(context()).allowed()) {
                allowed++;
            }
        }
        return allowed;
    }

    @Test
    @DisplayName("Counters are stored in Redis under the contract key schema")
    void testCountersStoredUnderExpectedKeys() {
        consume(3);

        String key = windowKey(BASE_TIME);
        assertThat(key).isEqualTo("dev:ratelimiter:sliding:" + clientId.toLowerCase() + ":"
                + BASE_TIME.getEpochSecond());
        assertThat(redisService.get(key)).contains("3");
    }

    @Test
    @DisplayName("With no previous window the limit is simply the capacity")
    void testFirstWindowAllowsFullCapacity() {
        int allowed = consume((int) CAPACITY + 5);

        assertThat(allowed).isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("TTL spans two windows so the counter survives to act as the previous window")
    void testTtlSpansTwoWindows() {
        consume(1);

        Optional<Duration> ttl = redisService.getTtl(windowKey(BASE_TIME));

        assertThat(ttl).isPresent();
        // 2 x 60s + 10s buffer, minus whatever elapsed during the call.
        assertThat(ttl.get()).isBetween(Duration.ofSeconds(120), Duration.ofSeconds(130));
    }

    @Test
    @DisplayName("Rejected requests do not consume quota")
    void testRejectedRequestsDoNotIncrementTheCounter() {
        consume((int) CAPACITY);
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        // Three rejections occurred; the counter must still read exactly CAPACITY.
        assertThat(redisService.get(windowKey(BASE_TIME))).contains(String.valueOf(CAPACITY));
    }

    @Test
    @DisplayName("The previous window is carried over by weight — this is what Fixed Window cannot do")
    void testPreviousWindowIsWeightedIntoTheDecision() {
        // Fill window 1 completely.
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        // Step to 50% into window 2. Half of window 1 still overlaps, so the weighted estimate is
        // 10 * 0.5 = 5 and only 5 further requests may be admitted — a Fixed Window limiter would
        // have granted a full fresh 10 here.
        clock.advance(WINDOW.plus(WINDOW.dividedBy(2)));

        int allowedInSecondWindow = consume((int) CAPACITY + 5);

        assertThat(allowedInSecondWindow)
                .as("previous window must be weighted into the current decision")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("Once the previous window has fully rolled off, full capacity returns")
    void testQuotaFullyRecoversAfterTwoWindows() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        // Two windows on, the original window contributes nothing.
        clock.advance(WINDOW.multipliedBy(2));

        assertThat(consume((int) CAPACITY + 5)).isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("Two independent limiter instances share one quota through Redis")
    void testDistributedQuotaIsShared() {
        RedisSlidingWindowCounterRateLimiter instanceA = newLimiter();
        RedisSlidingWindowCounterRateLimiter instanceB = newLimiter();

        int allowed = 0;
        for (int i = 0; i < CAPACITY + 5; i++) {
            RedisSlidingWindowCounterRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
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
        RedisSlidingWindowCounterRateLimiter instanceA = newLimiter();
        RedisSlidingWindowCounterRateLimiter instanceB = newLimiter();

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
        RedisSlidingWindowCounterRateLimiter instanceA = newLimiter();
        RedisSlidingWindowCounterRateLimiter instanceB = newLimiter();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                RedisSlidingWindowCounterRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
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

        // Read-calculate-decide-write runs inside Lua, so no two instances can both conclude there
        // is room for the same slot. Over-admission here would mean the script is not atomic.
        assertThat(allowed.get())
                .as("atomic evaluation must admit exactly the quota, never more")
                .isEqualTo((int) CAPACITY);
        assertThat(rejected.get()).isEqualTo(threads - (int) CAPACITY);
        assertThat(redisService.get(windowKey(BASE_TIME))).contains(String.valueOf(CAPACITY));
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
