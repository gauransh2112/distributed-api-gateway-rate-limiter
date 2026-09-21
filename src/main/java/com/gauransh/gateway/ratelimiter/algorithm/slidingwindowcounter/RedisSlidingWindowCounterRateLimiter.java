package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter;

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

/**
 * Distributed Sliding Window Counter rate limiter backed by Redis.
 *
 * <p>Functionally equivalent to {@link SlidingWindowCounterRateLimiter}, but both window counters
 * live in Redis rather than the JVM heap, so every Gateway instance evaluating the same client
 * shares one view of the quota.</p>
 *
 * <h2>The algorithm</h2>
 *
 * <p>Fixed Window admits bursts at its boundary: a client can spend a full quota at the end of one
 * window and another immediately at the start of the next. Sliding Window Counter smooths that by
 * weighting the previous window's count by how much of it still overlaps the rolling window:</p>
 *
 * <pre>
 * estimated = previousCount * previousWeight + currentCount
 * previousWeight = (windowDuration - elapsedInCurrentWindow) / windowDuration
 * </pre>
 *
 * <p>A request is admitted when {@code estimated + 1 <= capacity}.</p>
 *
 * <h2>Why this needs its own Lua script</h2>
 *
 * <p>Unlike Fixed Window, the decision here reads <em>two</em> keys, performs a weighted
 * calculation, and writes only if the result permits it. Run as separate commands, two Gateway
 * instances could each read the same counts, each conclude there is room, and each increment —
 * admitting more than capacity. {@code sliding_counter.lua} performs the whole
 * read-calculate-decide-write sequence as one atomic step, which is exactly the case ADR-0009
 * reserves Lua for.</p>
 *
 * <h2>Keys and expiry</h2>
 *
 * <p>Each window owns a counter keyed by its start instant, so the "previous window" is simply the
 * key one window duration earlier and no key rotation is needed. A counter must outlive its own
 * window because the following window reads it as the previous one, so its TTL covers two windows
 * plus the safety buffer.</p>
 *
 * <h2>State and failures</h2>
 *
 * <p>The limiter holds no mutable JVM state. Redis failures propagate as the Redis module's
 * exceptions; fail-open versus fail-closed is a separate documented deliverable.</p>
 */
public class RedisSlidingWindowCounterRateLimiter implements RateLimiter {

    /** Module segment of the Redis key, per the Engineering Contract key schema. */
    static final String KEY_MODULE = "ratelimiter";

    /** Resource segment identifying the Sliding Window Counter algorithm. */
    static final String KEY_RESOURCE = "sliding";

    /** Safety buffer added to the counter TTL, matching the Fixed Window limiter. */
    static final Duration TTL_SAFETY_BUFFER = Duration.ofSeconds(10);

    /** Index of the allowed flag in the script result. */
    private static final int RESULT_ALLOWED = 0;

    /** Index of the remaining count in the script result. */
    private static final int RESULT_REMAINING = 1;

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final RedisService redisService;
    private final RedisKeyBuilder keyBuilder;

    public RedisSlidingWindowCounterRateLimiter(
            RateLimiterProperties properties,
            RateLimitKeyResolver keyResolver,
            RateLimitPolicyResolver policyResolver,
            Clock clock,
            RedisService redisService,
            RedisKeyBuilder keyBuilder
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.keyResolver = keyResolver != null ? keyResolver : new DefaultRateLimitKeyResolver();
        this.policyResolver = policyResolver;
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.redisService = Objects.requireNonNull(redisService, "redisService must not be null");
        this.keyBuilder = Objects.requireNonNull(keyBuilder, "keyBuilder must not be null");
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
        long currentWindowStartMillis = (nowMillis / windowDurationMillis) * windowDurationMillis;
        long previousWindowStartMillis = currentWindowStartMillis - windowDurationMillis;
        Instant resetTime = Instant.ofEpochMilli(currentWindowStartMillis + windowDurationMillis);

        // Immediate rejection for non-positive configured capacity, matching the in-memory
        // algorithm. Redis is not contacted, because no counter transition is warranted.
        if (capacity <= 0) {
            return RateLimitDecision.rejected(0L, resetTime, retryAfter(now, resetTime),
                    RateLimitConstants.REASON_EXCEEDED);
        }

        double elapsedMillis = Math.max(0L, nowMillis - currentWindowStartMillis);
        double previousWeight =
                Math.max(0.0, (double) (windowDurationMillis - elapsedMillis) / windowDurationMillis);

        // Because each window owns a timestamped key, backward clock drift simply selects an
        // earlier key rather than corrupting a shared one; no drift special case is required.
        String currentKey = buildWindowKey(clientKey, currentWindowStartMillis);
        String previousKey = buildWindowKey(clientKey, previousWindowStartMillis);

        // Two windows plus the buffer: a counter is read as the "previous" window throughout the
        // window that follows it, so it must survive beyond its own.
        long ttlSeconds = windowDuration.multipliedBy(2).plus(TTL_SAFETY_BUFFER).toSeconds();

        List<?> result = redisService.executeLua(
                LuaScriptLoader.SLIDING_COUNTER_SCRIPT,
                List.class,
                List.of(currentKey, previousKey),
                List.of(String.valueOf(capacity), String.valueOf(previousWeight), String.valueOf(ttlSeconds)));

        if (result == null || result.size() < 2) {
            throw new IllegalStateException(
                    "Sliding window counter script returned an unusable result for key [" + currentKey + "]");
        }

        boolean allowed = toLong(result.get(RESULT_ALLOWED)) == 1L;
        if (allowed) {
            return RateLimitDecision.allowed(capacity, toLong(result.get(RESULT_REMAINING)), resetTime,
                    RateLimitConstants.REASON_ALLOWED);
        }

        return RateLimitDecision.rejected(capacity, resetTime, retryAfter(now, resetTime),
                RateLimitConstants.REASON_EXCEEDED);
    }

    /**
     * Builds the Redis key holding one client's counter for one window.
     *
     * <p>Produced through {@link RedisKeyBuilder} so the key obeys the Engineering Contract schema
     * {@code environment:module:resource:identifier}, following the resolution already applied to
     * the Fixed Window limiter. Example: {@code dev:ratelimiter:sliding:user-101:1722587600}</p>
     *
     * <p>Keying by window start replaces the rotation of a "current" and "previous" key pair: the
     * previous window's counter is simply the key one window earlier, which removes the need to
     * rename or reset anything as windows roll.</p>
     */
    private String buildWindowKey(String clientKey, long windowStartMillis) {
        long windowStartSeconds = windowStartMillis / 1000L;
        return keyBuilder.buildKey(KEY_MODULE, KEY_RESOURCE, clientKey + ":" + windowStartSeconds);
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Sliding window counter script returned a non-numeric value: " + value);
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
