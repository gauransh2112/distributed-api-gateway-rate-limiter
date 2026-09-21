package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.model.RateLimitPolicy;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitPolicyResolver;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.script.LuaScriptLoader;
import com.gauransh.gateway.redis.service.RedisService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Distributed Sliding Window Log rate limiter backed by Redis.
 *
 * <p>Functionally equivalent to {@link SlidingWindowLogRateLimiter}, but the request log lives in a
 * Redis sorted set rather than an in-process deque, so every Gateway instance evaluating the same
 * client shares one log.</p>
 *
 * <h2>The algorithm</h2>
 *
 * <p>Sliding Window Log is the most precise of the window algorithms: it records the timestamp of
 * every admitted request and counts exactly those still inside the rolling window. Where Fixed
 * Window permits a double burst at its boundary and Sliding Window Counter only approximates the
 * previous window by weighting it, this one carries no approximation at all.</p>
 *
 * <p>The precision costs memory. One sorted set member is stored per admitted request, so a
 * client's footprint is proportional to its capacity rather than constant as in the counter-based
 * algorithms. Expired entries are removed on every evaluation, which bounds it to one window's
 * worth of traffic.</p>
 *
 * <h2>Atomicity and the member token</h2>
 *
 * <p>Evict, count and conditionally insert execute inside {@code sliding_log.lua} as one atomic
 * step; run as separate commands, two instances could each observe a sub-capacity count and each
 * insert.</p>
 *
 * <p>Each entry's member carries a unique token rather than being the bare timestamp. Sorted set
 * members are unique, so two requests in the same millisecond would otherwise collide on one
 * member: {@code ZADD} would update that member's score instead of adding a second entry, silently
 * under-counting. The token is generated per request and per instance, so it stays unique across
 * Gateway instances without any coordination between them.</p>
 *
 * <h2>Clock regression</h2>
 *
 * <p>The in-memory implementation clears its log when the clock moves backwards, because an
 * {@link java.util.ArrayDeque} relies on insertion order to keep timestamps sorted. A sorted set
 * orders by score regardless of insertion order, so that reset is unnecessary here — and would be
 * actively harmful, since one instance with a skewed clock would wipe state shared by all of them.
 * Out-of-order entries simply sort into place and age out normally.</p>
 *
 * <h2>State and failures</h2>
 *
 * <p>The limiter holds no mutable JVM state. Redis failures propagate as the Redis module's
 * exceptions; fail-open versus fail-closed is a separate documented deliverable.</p>
 */
public class RedisSlidingWindowLogRateLimiter implements RateLimiter {

    /** Module segment of the Redis key, per the Engineering Contract key schema. */
    static final String KEY_MODULE = "ratelimiter";

    /** Resource segment identifying the Sliding Window Log algorithm. */
    static final String KEY_RESOURCE = "log";

    /** Safety buffer added to the log TTL, matching the other distributed limiters. */
    static final Duration TTL_SAFETY_BUFFER = Duration.ofSeconds(10);

    private static final int RESULT_ALLOWED = 0;
    private static final int RESULT_REMAINING = 1;
    private static final int RESULT_OLDEST_MILLIS = 2;

    /** Returned by the script when the log holds no entries. */
    private static final long NO_OLDEST_ENTRY = -1L;

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final RedisService redisService;
    private final RedisKeyBuilder keyBuilder;
    private final Supplier<String> memberTokenSupplier;

    public RedisSlidingWindowLogRateLimiter(
            RateLimiterProperties properties,
            RateLimitKeyResolver keyResolver,
            RateLimitPolicyResolver policyResolver,
            Clock clock,
            RedisService redisService,
            RedisKeyBuilder keyBuilder
    ) {
        this(properties, keyResolver, policyResolver, clock, redisService, keyBuilder,
                () -> UUID.randomUUID().toString());
    }

    /**
     * Additional constructor allowing the member token source to be supplied, so tests can assert
     * on the exact member written to the sorted set.
     */
    RedisSlidingWindowLogRateLimiter(
            RateLimiterProperties properties,
            RateLimitKeyResolver keyResolver,
            RateLimitPolicyResolver policyResolver,
            Clock clock,
            RedisService redisService,
            RedisKeyBuilder keyBuilder,
            Supplier<String> memberTokenSupplier
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.keyResolver = keyResolver != null ? keyResolver : new DefaultRateLimitKeyResolver();
        this.policyResolver = policyResolver;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.redisService = Objects.requireNonNull(redisService, "redisService must not be null");
        this.keyBuilder = Objects.requireNonNull(keyBuilder, "keyBuilder must not be null");
        this.memberTokenSupplier =
                Objects.requireNonNull(memberTokenSupplier, "memberTokenSupplier must not be null");
    }

    @Override
    public RateLimitDecision allowRequest(RateLimitContext context) {
        Objects.requireNonNull(context, "context must not be null");

        String rawKey = keyResolver.resolveKey(context);
        String clientKey = (rawKey != null && !rawKey.isBlank()) ? rawKey : RateLimitConstants.DEFAULT_ANONYMOUS_KEY;

        RateLimitPolicy policy = resolvePolicy(context);
        long capacity = resolveCapacity(policy);
        Duration windowDuration = resolveWindowDuration(policy);

        if (windowDuration == null || windowDuration.isNegative() || windowDuration.isZero()) {
            throw new IllegalArgumentException("Rate limit window duration must be strictly positive");
        }

        long windowDurationMillis = windowDuration.toMillis();
        Instant now = clock.instant();
        long nowMillis = now.toEpochMilli();
        long cutoffMillis = nowMillis - windowDurationMillis;
        Instant defaultResetTime = Instant.ofEpochMilli(nowMillis + windowDurationMillis);

        // Immediate rejection for non-positive configured capacity, matching the in-memory
        // algorithm. Redis is not contacted, because no log mutation is warranted.
        if (capacity <= 0) {
            return RateLimitDecision.rejected(0L, defaultResetTime, retryAfter(now, defaultResetTime),
                    RateLimitConstants.REASON_EXCEEDED);
        }

        // One continuous log per client, so unlike the windowed algorithms the key carries no
        // window component and sub-second windows present no key-collision concern.
        String logKey = keyBuilder.buildKey(KEY_MODULE, KEY_RESOURCE, clientKey);
        long ttlSeconds = windowDuration.plus(TTL_SAFETY_BUFFER).toSeconds();

        List<?> result = redisService.executeLua(
                LuaScriptLoader.SLIDING_LOG_SCRIPT,
                List.class,
                List.of(logKey),
                List.of(String.valueOf(nowMillis),
                        String.valueOf(cutoffMillis),
                        String.valueOf(capacity),
                        String.valueOf(ttlSeconds),
                        memberTokenSupplier.get()));

        if (result == null || result.size() < 3) {
            throw new IllegalStateException(
                    "Sliding window log script returned an unusable result for key [" + logKey + "]");
        }

        long oldestMillis = toLong(result.get(RESULT_OLDEST_MILLIS));
        Instant resetTime = (oldestMillis != NO_OLDEST_ENTRY)
                ? Instant.ofEpochMilli(oldestMillis + windowDurationMillis)
                : defaultResetTime;

        if (toLong(result.get(RESULT_ALLOWED)) == 1L) {
            return RateLimitDecision.allowed(capacity, toLong(result.get(RESULT_REMAINING)), resetTime,
                    RateLimitConstants.REASON_ALLOWED);
        }

        return RateLimitDecision.rejected(capacity, resetTime, retryAfter(now, resetTime),
                RateLimitConstants.REASON_EXCEEDED);
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Sliding window log script returned a non-numeric value: " + value);
    }

    private static Duration retryAfter(Instant now, Instant resetTime) {
        Duration retryAfter = Duration.between(now, resetTime);
        return retryAfter.isNegative() ? Duration.ZERO : retryAfter;
    }

    /**
     * Resolves the policy snapshot governing a single request evaluation.
     *
     * <p>Called exactly once per {@code allowRequest}, so capacity and window are always read from
     * one snapshot and a request can never mix values from two policy versions.</p>
     */
    private RateLimitPolicy resolvePolicy(RateLimitContext context) {
        return policyResolver != null ? policyResolver.resolvePolicy(context) : null;
    }

    private long resolveCapacity(RateLimitPolicy policy) {
        if (policy != null) {
            return policy.capacity();
        }
        return properties.getDefaultCapacity();
    }

    private Duration resolveWindowDuration(RateLimitPolicy policy) {
        if (policy != null && policy.window() != null) {
            return policy.window();
        }
        return properties.getDefaultWindow();
    }
}
