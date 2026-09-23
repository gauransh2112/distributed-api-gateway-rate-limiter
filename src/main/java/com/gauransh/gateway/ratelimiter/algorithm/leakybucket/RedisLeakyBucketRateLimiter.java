package com.gauransh.gateway.ratelimiter.algorithm.leakybucket;

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

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Distributed Leaky Bucket rate limiter backed by Redis.
 *
 * <p>Functionally equivalent to {@link LeakyBucketRateLimiter}, but each client's bucket lives in a
 * Redis hash rather than the JVM heap, so every Gateway instance meters against one shared bucket
 * instead of holding a private one.</p>
 *
 * <h2>The algorithm</h2>
 *
 * <p>A bucket holds a water level that rises by one unit per admitted request and drains
 * continuously at a fixed leak rate. A request is admitted only while there is room for one more
 * unit below capacity. Because the bucket starts <em>empty</em> and never accumulates unused
 * allowance, this is the algorithm that grants <strong>no burst</strong> — it enforces a smooth,
 * constant outflow, which is precisely the property Token Bucket deliberately relaxes.</p>
 *
 * <h2>State model — a documented deviation</h2>
 *
 * <p>The Redis design describes Leaky Bucket as a List used as a FIFO queue of requests. The
 * Gateway does not implement a queue: {@link LeakyBucket} is a meter holding one fractional water
 * level and a timestamp. A Redis List could not hold that level alongside the last-leak timestamp
 * the same design requires, and implementing a real queue would give the distributed limiter
 * different boundary behaviour from the in-memory one, which is the behavioural reference. The
 * state is therefore a hash of {@code level} and {@code lastLeak}. The deviation is recorded in the
 * Redis setup guide rather than applied silently.</p>
 *
 * <p>Capacity is deliberately not stored: it is configuration resolved per request from the policy
 * snapshot, so persisting it would let a stale value outlive a configuration change.</p>
 *
 * <h2>Atomicity</h2>
 *
 * <p>Drain, admission test and write execute inside {@code leaky_bucket.lua} as one atomic step.
 * The new level depends on elapsed time and is clamped at zero, so it cannot be expressed as an
 * {@code INCRBY}; issued as separate commands, two instances would each find room for one more unit
 * and each write, admitting past capacity.</p>
 *
 * <h2>Clock regression</h2>
 *
 * <p>The in-memory implementation states an explicit invariant: the last-leak timestamp must never
 * move backwards. This differs from Token Bucket, whose timestamp follows the observed clock. The
 * script preserves the stored timestamp when the clock runs backwards, so a skewed instance can
 * neither drain the shared bucket early nor cause the same interval to be drained twice later.</p>
 *
 * <h2>State and failures</h2>
 *
 * <p>State is written on every evaluation, including rejections — a rejected request still
 * materialises the water drained up to now, matching the in-memory implementation. This limiter
 * holds no mutable JVM state. Redis failures propagate as the Redis module's exceptions; fail-open
 * versus fail-closed is a separate documented deliverable.</p>
 */
public class RedisLeakyBucketRateLimiter implements RateLimiter {

    /** Module segment of the Redis key, per the Engineering Contract key schema. */
    static final String KEY_MODULE = "ratelimiter";

    /** Resource segment identifying the Leaky Bucket algorithm. */
    static final String KEY_RESOURCE = "leaky";

    /**
     * Lifetime of an idle bucket.
     *
     * <p>Documented in the Redis design as one hour, refreshed on access — the same rule as Token
     * Bucket, and for the same reason: a bucket has no window to expire alongside, so its lifetime
     * is measured from the last request.</p>
     */
    static final Duration BUCKET_TTL = Duration.ofHours(1);

    private static final int RESULT_ALLOWED = 0;
    private static final int RESULT_LEVEL = 1;
    private static final int RESULT_LAST_LEAK = 2;
    private static final int RESULT_SIZE = 3;

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final RedisService redisService;
    private final RedisKeyBuilder keyBuilder;

    public RedisLeakyBucketRateLimiter(
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
        double leakRate = resolveLeakRate(policy, capacity);

        // Validation matches the in-memory Leaky Bucket exactly, including that an invalid
        // configuration is an error rather than a rejection. Redis is not contacted.
        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate limit capacity must be strictly positive");
        }

        if (!Double.isFinite(leakRate) || leakRate <= 0.0) {
            throw new IllegalArgumentException("Leak rate must be strictly positive and finite");
        }

        Instant now = clock.instant();
        long nowMillis = now.toEpochMilli();
        double leakRatePerMillis = leakRate / 1000.0;

        String bucketKey = keyBuilder.buildKey(KEY_MODULE, KEY_RESOURCE, clientKey);

        List<?> result = redisService.executeLua(
                LuaScriptLoader.LEAKY_BUCKET_SCRIPT,
                List.class,
                List.of(bucketKey),
                List.of(String.valueOf(nowMillis),
                        String.valueOf(capacity),
                        String.valueOf(leakRatePerMillis),
                        String.valueOf(BUCKET_TTL.toSeconds())));

        if (result == null || result.size() < RESULT_SIZE) {
            throw new IllegalStateException(
                    "Redis returned an unusable leaky bucket result for key [" + bucketKey + "]");
        }

        boolean allowed = toLong(result.get(RESULT_ALLOWED), bucketKey) == 1L;
        // The level after this evaluation: incremented when admitted, merely drained when not.
        double level = toDouble(result.get(RESULT_LEVEL), bucketKey);

        if (allowed) {
            long remaining = Math.max(0L, capacity - (long) Math.ceil(level));
            long millisToEmpty = (long) Math.ceil(level / leakRatePerMillis);
            // Measured from the timestamp actually stored, which under a backward clock is the
            // preserved one rather than now.
            long lastLeak = toLong(result.get(RESULT_LAST_LEAK), bucketKey);
            Instant resetTime = Instant.ofEpochMilli(lastLeak + millisToEmpty);
            return RateLimitDecision.allowed(capacity, remaining, resetTime, RateLimitConstants.REASON_ALLOWED);
        }

        // How much must drain away before one more unit would fit. Floored at one millisecond so a
        // rejected client is never told to retry immediately.
        double waterNeeded = (level + 1.0) - capacity;
        long millisToWait = Math.max(1L, (long) Math.ceil(waterNeeded / leakRatePerMillis));
        Duration retryAfter = Duration.ofMillis(millisToWait);
        return RateLimitDecision.rejected(capacity, now.plus(retryAfter), retryAfter,
                RateLimitConstants.REASON_EXCEEDED);
    }

    /** Reads a whole-number element of the script result. */
    private static long toLong(Object value, String bucketKey) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof byte[] bytes) {
            return Long.parseLong(new String(bytes, StandardCharsets.UTF_8));
        }
        throw new IllegalStateException(
                "Redis returned a non-numeric leaky bucket field for key [" + bucketKey + "]: " + value);
    }

    /**
     * Reads the exact fractional water level from the script result.
     *
     * <p>The script returns this field as a string deliberately. Redis truncates a Lua number to an
     * integer on the way out — including inside a returned table — so returning it as a number
     * would discard the fractional part of the bucket on every call.</p>
     */
    private static double toDouble(Object value, String bucketKey) {
        String text = (value instanceof byte[] bytes) ? new String(bytes, StandardCharsets.UTF_8)
                : String.valueOf(value);
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Redis returned an unparseable water level for key [" + bucketKey + "]: " + text, e);
        }
    }

    /**
     * Resolves the policy snapshot governing a single request evaluation.
     *
     * <p>Called exactly once per {@code allowRequest}, so capacity and leak rate are always read
     * from one snapshot and a request can never mix values from two policy versions.</p>
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

    /**
     * Resolves the leak rate in units per second, matching the in-memory implementation: a policy
     * window implies the rate that drains exactly one capacity over that window, otherwise the
     * configured rate applies. There is no separate leak-rate property; {@code refillRate} serves
     * both bucket algorithms.
     */
    private double resolveLeakRate(RateLimitPolicy policy, long capacity) {
        if (policy != null && policy.window() != null && !policy.window().isZero() && !policy.window().isNegative()) {
            double windowSeconds = policy.window().toMillis() / 1000.0;
            return (double) capacity / windowSeconds;
        }
        return properties.getRefillRate();
    }
}
