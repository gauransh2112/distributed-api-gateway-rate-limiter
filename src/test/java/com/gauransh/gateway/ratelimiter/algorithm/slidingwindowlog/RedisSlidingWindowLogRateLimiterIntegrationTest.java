package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog;

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
 * Live integration tests for {@link RedisSlidingWindowLogRateLimiter}.
 *
 * <p>These verify the properties that make this the most precise window algorithm: exact boundary
 * semantics, no approximation of the previous window, and gradual eviction as the window rolls —
 * plus the atomicity that keeps all of it correct across independent limiter instances.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
@DisplayName("Redis Sliding Window Log Rate Limiter — live Redis")
class RedisSlidingWindowLogRateLimiterIntegrationTest {

    private static final long CAPACITY = 5L;
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
    private RedisSlidingWindowLogRateLimiter rateLimiter;
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
        clientId = "swl-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(logKey());
    }

    private RedisSlidingWindowLogRateLimiter newLimiter() {
        return new RedisSlidingWindowLogRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), null, clock, redisService, keyBuilder);
    }

    private RateLimitContext context() {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", clock.instant(), "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    private String logKey() {
        return keyBuilder.buildKey(RedisSlidingWindowLogRateLimiter.KEY_MODULE,
                RedisSlidingWindowLogRateLimiter.KEY_RESOURCE, clientId);
    }

    /** Number of entries actually stored in the sorted set. */
    private long logSize() {
        Long size = redisTemplate.opsForZSet().zCard(logKey());
        return size != null ? size : 0L;
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
    @DisplayName("The log is a sorted set under the contract key schema")
    void testLogStoredAsSortedSet() {
        consume(3);

        assertThat(logKey()).isEqualTo("dev:ratelimiter:log:" + clientId.toLowerCase());
        assertThat(redisTemplate.type(logKey())).isEqualTo(org.springframework.data.redis.connection.DataType.ZSET);
        assertThat(logSize()).isEqualTo(3L);
    }

    @Test
    @DisplayName("The limit is enforced: exactly capacity allowed, the rest rejected")
    void testLimitEnforced() {
        assertThat(consume((int) CAPACITY + 3)).isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("Rejected requests never enter the log")
    void testRejectedRequestsDoNotEnterTheLog() {
        consume((int) CAPACITY);
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        assertThat(logSize())
                .as("a rejected request must not consume quota")
                .isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("TTL is window + 10s and is refreshed while requests keep arriving")
    void testTtlAppliedAndRefreshed() {
        consume(1);
        Optional<Duration> afterFirst = redisService.getTtl(logKey());

        clock.advance(Duration.ofSeconds(10));
        consume(1);
        Optional<Duration> afterSecond = redisService.getTtl(logKey());

        assertThat(afterFirst).isPresent();
        assertThat(afterFirst.get()).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(70));
        // Continuous log: the key should outlive its newest entry, so the TTL is pushed out again.
        assertThat(afterSecond).isPresent();
        assertThat(afterSecond.get()).isBetween(Duration.ofSeconds(60), Duration.ofSeconds(70));
    }

    @Test
    @DisplayName("An entry exactly at the window boundary is still active")
    void testEntryAtBoundaryIsActive() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        // Exactly one window later the oldest entry sits at now - window, which is still inside
        // the window by the exclusive-cutoff rule, so no quota is released.
        clock.advance(WINDOW);

        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();
        assertThat(logSize()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("One millisecond past the boundary the oldest entry expires and releases one slot")
    void testEntryOneMillisecondPastBoundaryExpires() {
        // Entries are spaced one millisecond apart so they age out one at a time. Entries sharing
        // an instant share a score and would therefore age out together (see the test below), which
        // is a different property from the one asserted here.
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(rateLimiter.allowRequest(context()).allowed()).isTrue();
            if (i < CAPACITY - 1) {
                clock.advance(Duration.ofMillis(1));
            }
        }

        // The clock now sits on the newest entry, CAPACITY-1 milliseconds after the oldest. Advance
        // so that "now" is exactly one millisecond past the oldest entry's boundary: the cutoff then
        // falls between the oldest entry and the second oldest, evicting only the oldest.
        clock.advance(WINDOW.plusMillis(1).minusMillis(CAPACITY - 1));

        assertThat(rateLimiter.allowRequest(context()).allowed())
                .as("exactly one slot should be released")
                .isTrue();
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();
        assertThat(logSize()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("Entries recorded in the same millisecond age out together")
    void testSameInstantBatchExpiresTogether() {
        // The log orders by timestamp, so a burst landing inside one millisecond forms a single
        // cohort: it is admitted together and it expires together, releasing the whole quota at once
        // rather than one slot at a time.
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        clock.advance(WINDOW.plusMillis(1));

        assertThat(consume((int) CAPACITY))
                .as("the whole same-instant cohort ages out at the same moment")
                .isEqualTo((int) CAPACITY);
        assertThat(logSize()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("The Fixed Window boundary burst is rejected — the reason this algorithm exists")
    void testBoundaryBurstIsSuppressed() {
        // Spend the whole quota at the very end of a window.
        clock.advance(WINDOW.minusMillis(1));
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        // Step just past the boundary. Fixed Window would grant a fresh full quota here; the log
        // still holds all CAPACITY entries because none has aged out yet.
        clock.advance(Duration.ofMillis(2));

        assertThat(consume((int) CAPACITY))
                .as("no 2x burst may cross the window boundary")
                .isZero();
    }

    @Test
    @DisplayName("Entries evict gradually as the rolling window advances")
    void testGradualEviction() {
        // Three requests spaced ten seconds apart.
        consume(1);
        clock.advance(Duration.ofSeconds(10));
        consume(1);
        clock.advance(Duration.ofSeconds(10));
        consume(1);
        assertThat(logSize()).isEqualTo(3L);

        // Move far enough that only the first entry has aged out.
        clock.advance(WINDOW.minus(Duration.ofSeconds(15)));
        consume(1);

        assertThat(logSize())
                .as("one entry evicted, one added")
                .isEqualTo(3L);
    }

    @Test
    @DisplayName("A long idle period clears the log entirely")
    void testLongIdleResetsTheLog() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        clock.advance(WINDOW.multipliedBy(3));

        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);
        assertThat(logSize()).isEqualTo(CAPACITY);
    }

    @Test
    @DisplayName("Two independent limiter instances share one log through Redis")
    void testDistributedQuotaIsShared() {
        RedisSlidingWindowLogRateLimiter instanceA = newLimiter();
        RedisSlidingWindowLogRateLimiter instanceB = newLimiter();

        int allowed = 0;
        for (int i = 0; i < CAPACITY + 5; i++) {
            RedisSlidingWindowLogRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
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
        RedisSlidingWindowLogRateLimiter instanceA = newLimiter();
        RedisSlidingWindowLogRateLimiter instanceB = newLimiter();

        for (int i = 0; i < CAPACITY; i++) {
            assertThat(instanceA.allowRequest(context()).allowed()).isTrue();
        }

        RateLimitDecision onB = instanceB.allowRequest(context());
        assertThat(onB.allowed()).isFalse();
        assertThat(onB.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
    }

    @Test
    @DisplayName("Concurrent requests admit exactly the quota and store exactly that many entries")
    void testConcurrentRequestsAcrossInstances() throws Exception {
        int threads = 40;
        RedisSlidingWindowLogRateLimiter instanceA = newLimiter();
        RedisSlidingWindowLogRateLimiter instanceB = newLimiter();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                RedisSlidingWindowLogRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
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
                .as("atomic evaluation must admit exactly the quota, never more")
                .isEqualTo((int) CAPACITY);
        assertThat(rejected.get()).isEqualTo(threads - (int) CAPACITY);

        // These requests land within the same millisecond. If members were the bare timestamp they
        // would collide, ZADD would overwrite rather than append, and the log would hold fewer
        // entries than were admitted — an under-count invisible to the counters above.
        assertThat(logSize())
                .as("every admitted request must occupy its own entry")
                .isEqualTo(allowed.get());
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
