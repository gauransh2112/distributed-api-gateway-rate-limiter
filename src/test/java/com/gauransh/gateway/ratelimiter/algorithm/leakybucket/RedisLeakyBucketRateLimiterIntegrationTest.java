package com.gauransh.gateway.ratelimiter.algorithm.leakybucket;

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
 * Live integration tests for {@link RedisLeakyBucketRateLimiter}.
 *
 * <p>These verify the properties that distinguish Leaky Bucket from Token Bucket despite their
 * near-identical state: the bucket starts <em>empty</em> and grants no burst, water drains at a
 * constant rate rather than accumulating as allowance, and the last-leak timestamp never moves
 * backwards — plus the atomicity that keeps all of it correct across independent instances.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
@DisplayName("Redis Leaky Bucket Rate Limiter — live Redis")
class RedisLeakyBucketRateLimiterIntegrationTest {

    private static final long CAPACITY = 5L;
    /** 2 units per second, so exactly one unit drains per 500ms. */
    private static final double LEAK_RATE = 2.0;
    private static final Duration ONE_UNIT = Duration.ofMillis(500);
    private static final Instant BASE_TIME = Instant.parse("2026-09-23T12:00:00.000Z");

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RateLimiterProperties properties;
    private MutableClock clock;
    private RedisLeakyBucketRateLimiter rateLimiter;
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
        properties.setRefillRate(LEAK_RATE);
        clock = new MutableClock(BASE_TIME);
        rateLimiter = newLimiter();
        clientId = "lb-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(bucketKey());
    }

    private RedisLeakyBucketRateLimiter newLimiter() {
        return new RedisLeakyBucketRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), null, clock, redisService, keyBuilder);
    }

    private RateLimitContext context() {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", clock.instant(), "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    private String bucketKey() {
        return keyBuilder.buildKey(RedisLeakyBucketRateLimiter.KEY_MODULE,
                RedisLeakyBucketRateLimiter.KEY_RESOURCE, clientId);
    }

    /** The exact fractional water level currently stored, as the script wrote it. */
    private double storedLevel() {
        Object value = redisTemplate.opsForHash().get(bucketKey(), "level");
        return value == null ? -1d : Double.parseDouble(String.valueOf(value));
    }

    private long storedLastLeak() {
        Object value = redisTemplate.opsForHash().get(bucketKey(), "lastLeak");
        return value == null ? -1L : Long.parseLong(String.valueOf(value));
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
    @DisplayName("The bucket is a hash under the contract key schema, not the documented List")
    void testBucketStoredAsHash() {
        consume(1);

        assertThat(bucketKey()).isEqualTo("dev:ratelimiter:leaky:" + clientId.toLowerCase());
        assertThat(redisTemplate.type(bucketKey()))
                .as("the meter model requires a hash; a List could not hold the timestamp")
                .isEqualTo(org.springframework.data.redis.connection.DataType.HASH);
    }

    @Test
    @DisplayName("Only the water level and last-leak timestamp are stored")
    void testOnlyLevelAndLastLeakAreStored() {
        consume(1);

        assertThat(redisTemplate.opsForHash().keys(bucketKey()))
                .containsExactlyInAnyOrder("level", "lastLeak");
    }

    @Test
    @DisplayName("A new bucket starts empty, so the first request finds it at level one")
    void testNewBucketStartsEmpty() {
        assertThat(rateLimiter.allowRequest(context()).allowed()).isTrue();

        assertThat(storedLevel())
                .as("an unseen client's bucket is empty, not full as in Token Bucket")
                .isEqualTo(1.0d);
    }

    @Test
    @DisplayName("The bucket fills to capacity and then rejects")
    void testFillsToCapacityThenRejects() {
        assertThat(consume((int) CAPACITY + 3)).isEqualTo((int) CAPACITY);
        assertThat(storedLevel()).isEqualTo((double) CAPACITY);
    }

    @Test
    @DisplayName("Water drains at the configured constant rate")
    void testDrainsAtConfiguredRate() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        clock.advance(ONE_UNIT);
        assertThat(rateLimiter.allowRequest(context()).allowed())
                .as("one unit has drained, so exactly one request fits")
                .isTrue();
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        clock.advance(ONE_UNIT.multipliedBy(2));
        assertThat(consume(3))
                .as("two units drained, so exactly two of three requests are admitted")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("A partial drain is stored as a fraction, not truncated")
    void testFractionalLevelSurvivesTheRoundTrip() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        clock.advance(ONE_UNIT.dividedBy(2));
        assertThat(rateLimiter.allowRequest(context()).allowed())
                .as("half a unit of headroom is not enough for a whole request")
                .isFalse();

        assertThat(storedLevel())
                .as("the fraction must survive Redis: truncation here would corrupt the drain")
                .isEqualTo(4.5d);
    }

    @Test
    @DisplayName("A rejected request still materialises the drained level")
    void testRejectedRequestWritesState() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        clock.advance(Duration.ofMillis(100));
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        assertThat(storedLevel())
                .as("100ms at 0.002/ms drains 0.2 units, and that is persisted")
                .isEqualTo(4.8d);
        assertThat(storedLastLeak()).isEqualTo(BASE_TIME.toEpochMilli() + 100L);
    }

    @Test
    @DisplayName("No burst is granted however long the bucket has been idle")
    void testNoBurstAfterIdle() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        // An hour of idling empties the bucket completely, but an empty Leaky Bucket confers no
        // stored allowance: the very next burst is still capped at capacity, never more.
        clock.advance(Duration.ofHours(1));

        assertThat(storedLevel()).isEqualTo((double) CAPACITY);
        assertThat(consume((int) CAPACITY + 3))
                .as("an idle Leaky Bucket grants no extra credit")
                .isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("A long idle period drains the bucket to empty, never below")
    void testLongIdleEmptiesBucket() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        clock.advance(Duration.ofHours(1));
        assertThat(rateLimiter.allowRequest(context()).allowed()).isTrue();

        assertThat(storedLevel())
                .as("the level is clamped at zero before this request's unit is added")
                .isEqualTo(1.0d);
    }

    @Test
    @DisplayName("A backward clock never rewinds the stored last-leak timestamp")
    void testClockRegressionPreservesTimestamp() {
        assertThat(consume(2)).isEqualTo(2);
        long lastLeakBefore = storedLastLeak();
        double levelBefore = storedLevel();

        clock.advance(Duration.ofSeconds(-30));
        assertThat(rateLimiter.allowRequest(context()).allowed()).isTrue();

        assertThat(storedLastLeak())
                .as("the in-memory invariant is that lastLeak must never move backwards")
                .isEqualTo(lastLeakBefore);
        assertThat(storedLevel())
                .as("no time elapsed, so only this request's unit is added")
                .isEqualTo(levelBefore + 1.0d);
    }

    @Test
    @DisplayName("A rejected request reports the wait until one unit has drained")
    void testRetryAfterReportsDrainWait() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        RateLimitDecision decision = rateLimiter.allowRequest(context());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter())
                .as("a full bucket draining at 2/s needs 500ms of headroom")
                .isEqualTo(ONE_UNIT);
        assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
    }

    @Test
    @DisplayName("TTL is one hour and is refreshed on every request, including rejections")
    void testTtlAppliedAndRefreshed() {
        consume(1);
        Optional<Duration> afterFirst = redisService.getTtl(bucketKey());

        consume((int) CAPACITY);
        // Less than one unit's worth of drain, so this request is genuinely rejected.
        clock.advance(Duration.ofMillis(100));
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();
        Optional<Duration> afterRejection = redisService.getTtl(bucketKey());

        assertThat(afterFirst).isPresent();
        assertThat(afterFirst.get()).isBetween(Duration.ofMinutes(59), Duration.ofHours(1));
        assertThat(afterRejection).isPresent();
        assertThat(afterRejection.get()).isBetween(Duration.ofMinutes(59), Duration.ofHours(1));
    }

    @Test
    @DisplayName("An expired bucket behaves exactly like an unseen client")
    void testExpiredBucketReinitialises() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        redisTemplate.delete(bucketKey());

        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("Two independent limiter instances share one bucket through Redis")
    void testDistributedQuotaIsShared() {
        RedisLeakyBucketRateLimiter instanceA = newLimiter();
        RedisLeakyBucketRateLimiter instanceB = newLimiter();

        int allowed = 0;
        for (int i = 0; i < CAPACITY + 5; i++) {
            RedisLeakyBucketRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
            if (instance.allowRequest(context()).allowed()) {
                allowed++;
            }
        }

        assertThat(allowed)
                .as("a client must not obtain a separate bucket per Gateway instance")
                .isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("Capacity reached on one instance is already reached on the other")
    void testExhaustionVisibleAcrossInstances() {
        RedisLeakyBucketRateLimiter instanceA = newLimiter();
        RedisLeakyBucketRateLimiter instanceB = newLimiter();

        for (int i = 0; i < CAPACITY; i++) {
            assertThat(instanceA.allowRequest(context()).allowed()).isTrue();
        }

        RateLimitDecision onB = instanceB.allowRequest(context());
        assertThat(onB.allowed()).isFalse();
        assertThat(onB.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
    }

    @Test
    @DisplayName("Concurrent requests admit exactly the capacity and leave exactly that much water")
    void testConcurrentRequestsAcrossInstances() throws Exception {
        int threads = 40;
        RedisLeakyBucketRateLimiter instanceA = newLimiter();
        RedisLeakyBucketRateLimiter instanceB = newLimiter();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                RedisLeakyBucketRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
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
                .as("atomic drain-test-write must admit exactly the capacity, never more")
                .isEqualTo((int) CAPACITY);
        assertThat(rejected.get()).isEqualTo(threads - (int) CAPACITY);

        // The counters above would still pass if the script lost a write under contention: the
        // stored level is the independent check. The clock is fixed for the whole burst, so no
        // drain can mask a lost increment.
        assertThat(storedLevel())
                .as("every admitted request must have added exactly one unit of water")
                .isEqualTo((double) allowed.get());
    }

    /** Deterministic clock so drain boundaries are controlled rather than observed. */
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
