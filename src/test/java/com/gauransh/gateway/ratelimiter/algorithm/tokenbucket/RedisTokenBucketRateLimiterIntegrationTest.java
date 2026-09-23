package com.gauransh.gateway.ratelimiter.algorithm.tokenbucket;

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
 * Live integration tests for {@link RedisTokenBucketRateLimiter}.
 *
 * <p>These verify the properties that distinguish Token Bucket from the window algorithms: an idle
 * bucket accumulates a burst allowance, tokens refill continuously and fractionally rather than in
 * whole steps, and a rejected request still advances the bucket's state — plus the atomicity that
 * keeps all of it correct across independent limiter instances.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
@DisplayName("Redis Token Bucket Rate Limiter — live Redis")
class RedisTokenBucketRateLimiterIntegrationTest {

    private static final long CAPACITY = 5L;
    /** 2 tokens per second, so exactly one token per 500ms. */
    private static final double REFILL_RATE = 2.0;
    private static final Duration ONE_TOKEN = Duration.ofMillis(500);
    private static final Instant BASE_TIME = Instant.parse("2026-09-23T12:00:00.000Z");

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RateLimiterProperties properties;
    private MutableClock clock;
    private RedisTokenBucketRateLimiter rateLimiter;
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
        properties.setRefillRate(REFILL_RATE);
        clock = new MutableClock(BASE_TIME);
        rateLimiter = newLimiter();
        clientId = "tb-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.delete(bucketKey());
    }

    private RedisTokenBucketRateLimiter newLimiter() {
        return new RedisTokenBucketRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), null, clock, redisService, keyBuilder);
    }

    private RateLimitContext context() {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", clock.instant(), "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    private String bucketKey() {
        return keyBuilder.buildKey(RedisTokenBucketRateLimiter.KEY_MODULE,
                RedisTokenBucketRateLimiter.KEY_RESOURCE, clientId);
    }

    /** The exact fractional token count currently stored, as the script wrote it. */
    private double storedTokens() {
        Object value = redisTemplate.opsForHash().get(bucketKey(), "tokens");
        return value == null ? -1d : Double.parseDouble(String.valueOf(value));
    }

    private long storedLastRefill() {
        Object value = redisTemplate.opsForHash().get(bucketKey(), "lastRefill");
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
    @DisplayName("The bucket is a hash under the contract key schema")
    void testBucketStoredAsHash() {
        consume(1);

        assertThat(bucketKey()).isEqualTo("dev:ratelimiter:tokenbucket:" + clientId.toLowerCase());
        assertThat(redisTemplate.type(bucketKey()))
                .isEqualTo(org.springframework.data.redis.connection.DataType.HASH);
    }

    @Test
    @DisplayName("Capacity is not stored: it is configuration, not bucket state")
    void testOnlyTokensAndLastRefillAreStored() {
        consume(1);

        assertThat(redisTemplate.opsForHash().keys(bucketKey()))
                .containsExactlyInAnyOrder("tokens", "lastRefill");
    }

    @Test
    @DisplayName("A new bucket starts full, so a first-time client may burst up to capacity")
    void testNewBucketStartsFull() {
        assertThat(consume((int) CAPACITY + 3))
                .as("an unseen client gets a full bucket, then is held to the refill rate")
                .isEqualTo((int) CAPACITY);
        assertThat(storedTokens()).isZero();
    }

    @Test
    @DisplayName("Tokens refill at the configured rate")
    void testRefillAtConfiguredRate() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        // One token per 500ms.
        clock.advance(ONE_TOKEN);
        assertThat(rateLimiter.allowRequest(context()).allowed()).isTrue();
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        clock.advance(ONE_TOKEN.multipliedBy(2));
        assertThat(consume(3))
                .as("two tokens accrued, so exactly two of three requests are admitted")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("A partial refill is stored as a fraction, not truncated to zero")
    void testFractionalTokensSurviveTheRoundTrip() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        // Half of one token's worth of time.
        clock.advance(ONE_TOKEN.dividedBy(2));
        assertThat(rateLimiter.allowRequest(context()).allowed())
                .as("half a token is not enough to admit a request")
                .isFalse();

        assertThat(storedTokens())
                .as("the fraction must survive Redis: truncation here would destroy the refill")
                .isEqualTo(0.5d);

        // The other half completes the token.
        clock.advance(ONE_TOKEN.dividedBy(2));
        assertThat(rateLimiter.allowRequest(context()).allowed()).isTrue();
    }

    @Test
    @DisplayName("A rejected request still materialises state and advances lastRefill")
    void testRejectedRequestWritesState() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);
        long refillAfterExhaustion = storedLastRefill();

        clock.advance(Duration.ofMillis(100));
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        assertThat(storedLastRefill())
                .as("unlike the sliding log, a rejected request updates the bucket")
                .isEqualTo(refillAfterExhaustion + 100L);
        assertThat(storedTokens())
                .as("the tokens accrued during those 100ms are materialised, not discarded")
                .isEqualTo(0.2d);
    }

    @Test
    @DisplayName("Tokens are capped at capacity however long the bucket idles")
    void testTokensCapAtCapacity() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        clock.advance(Duration.ofHours(1));

        assertThat(consume((int) CAPACITY + 3))
                .as("an hour of refill cannot exceed the bucket's capacity")
                .isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("A rejected request reports the wait until the next whole token")
    void testRetryAfterReportsWaitForNextToken() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);

        RateLimitDecision decision = rateLimiter.allowRequest(context());

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.retryAfter())
                .as("an empty bucket refilling at 2/s needs 500ms for one token")
                .isEqualTo(ONE_TOKEN);
        assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
    }

    @Test
    @DisplayName("TTL is one hour and is refreshed on every request, including rejections")
    void testTtlAppliedAndRefreshed() {
        consume(1);
        Optional<Duration> afterFirst = redisService.getTtl(bucketKey());

        // Exhaust the bucket so the next request is rejected, then check the TTL was still pushed
        // out: a bucket expiring mid-throttle would be recreated full.
        consume((int) CAPACITY);
        // Less than one token's worth of time, so the bucket is still empty and this request is
        // genuinely rejected rather than served by a refill.
        clock.advance(Duration.ofMillis(100));
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();
        Optional<Duration> afterRejection = redisService.getTtl(bucketKey());

        assertThat(afterFirst).isPresent();
        assertThat(afterFirst.get()).isBetween(Duration.ofMinutes(59), Duration.ofHours(1));
        assertThat(afterRejection).isPresent();
        assertThat(afterRejection.get()).isBetween(Duration.ofMinutes(59), Duration.ofHours(1));
    }

    @Test
    @DisplayName("An expired bucket is recreated full")
    void testExpiredBucketReinitialises() {
        assertThat(consume((int) CAPACITY)).isEqualTo((int) CAPACITY);
        assertThat(rateLimiter.allowRequest(context()).allowed()).isFalse();

        // Simulate the one-hour TTL elapsing while the client was idle.
        redisTemplate.delete(bucketKey());

        assertThat(consume((int) CAPACITY))
                .as("a bucket that has expired behaves exactly like an unseen client")
                .isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("A backward clock neither refills nor drains the shared bucket")
    void testClockRegressionDoesNotCorruptState() {
        assertThat(consume(2)).isEqualTo(2);
        double tokensBefore = storedTokens();

        clock.advance(Duration.ofSeconds(-30));
        assertThat(rateLimiter.allowRequest(context()).allowed()).isTrue();

        assertThat(storedTokens())
                .as("no time elapsed, so only the consumed token is deducted")
                .isEqualTo(tokensBefore - 1.0d);
        assertThat(storedLastRefill())
                .as("the timestamp follows the observed clock, matching the in-memory algorithm")
                .isEqualTo(clock.instant().toEpochMilli());
    }

    @Test
    @DisplayName("Two independent limiter instances share one bucket through Redis")
    void testDistributedQuotaIsShared() {
        RedisTokenBucketRateLimiter instanceA = newLimiter();
        RedisTokenBucketRateLimiter instanceB = newLimiter();

        int allowed = 0;
        for (int i = 0; i < CAPACITY + 5; i++) {
            RedisTokenBucketRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
            if (instance.allowRequest(context()).allowed()) {
                allowed++;
            }
        }

        assertThat(allowed)
                .as("a client must not obtain a separate bucket per Gateway instance")
                .isEqualTo((int) CAPACITY);
    }

    @Test
    @DisplayName("Quota exhausted on one instance is already exhausted on the other")
    void testExhaustionVisibleAcrossInstances() {
        RedisTokenBucketRateLimiter instanceA = newLimiter();
        RedisTokenBucketRateLimiter instanceB = newLimiter();

        for (int i = 0; i < CAPACITY; i++) {
            assertThat(instanceA.allowRequest(context()).allowed()).isTrue();
        }

        RateLimitDecision onB = instanceB.allowRequest(context());
        assertThat(onB.allowed()).isFalse();
        assertThat(onB.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
    }

    @Test
    @DisplayName("Concurrent requests consume exactly the quota and leave exactly that many tokens spent")
    void testConcurrentRequestsAcrossInstances() throws Exception {
        int threads = 40;
        RedisTokenBucketRateLimiter instanceA = newLimiter();
        RedisTokenBucketRateLimiter instanceB = newLimiter();

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                RedisTokenBucketRateLimiter instance = (i % 2 == 0) ? instanceA : instanceB;
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
                .as("atomic read-refill-consume-write must admit exactly the quota, never more")
                .isEqualTo((int) CAPACITY);
        assertThat(rejected.get()).isEqualTo(threads - (int) CAPACITY);

        // The counters above would still pass if the script lost a write under contention: the
        // stored balance is the independent check. The clock is fixed for the whole burst, so no
        // refill can mask a lost decrement.
        assertThat(storedTokens())
                .as("every admitted request must have removed exactly one token from the bucket")
                .isEqualTo((double) CAPACITY - allowed.get());
    }

    /** Deterministic clock so refill boundaries are controlled rather than observed. */
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
