package com.gauransh.gateway.ratelimiter.algorithm.tokenbucket;

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
 * Distributed Token Bucket rate limiter backed by Redis.
 *
 * <p>Functionally equivalent to {@link TokenBucketRateLimiter}, but each client's bucket lives in a
 * Redis hash rather than the JVM heap, so every Gateway instance draws from one shared pool of
 * tokens instead of holding a private bucket per instance.</p>
 *
 * <h2>The algorithm</h2>
 *
 * <p>A bucket holds up to {@code capacity} tokens and refills continuously at a configured rate.
 * Each request consumes one token; a request arriving at an empty bucket is rejected. Because a
 * bucket that has been idle accumulates tokens up to its capacity, this is the algorithm that
 * deliberately <em>permits</em> a burst — a client that has been quiet may spend the whole bucket
 * at once, then is held to the refill rate. Tokens are fractional, so the rate is honoured exactly
 * rather than in whole-token steps.</p>
 *
 * <h2>Atomicity</h2>
 *
 * <p>Read, refill, consume and write execute inside {@code token_bucket.lua} as one atomic step.
 * This algorithm is the reason the project adopted Lua at all: the new token count depends on
 * elapsed time and is capped at capacity, so it cannot be expressed as an {@code INCRBY}. Issued as
 * separate commands, two instances would each read the same count, each compute the same
 * post-consumption value and each write it, letting two requests consume one token.</p>
 *
 * <h2>State</h2>
 *
 * <p>The hash stores {@code tokens} and {@code lastRefill}. Capacity is deliberately not stored:
 * it is configuration rather than bucket state, resolved per request from the policy snapshot, so
 * persisting it would let a stale value outlive a configuration change. This is a documented
 * deviation from the Redis design's three-field sketch.</p>
 *
 * <p>State is written on every evaluation, including rejections — a rejected request still
 * materialises the tokens accrued up to now and still advances {@code lastRefill}, matching the
 * in-memory implementation. This limiter holds no mutable JVM state of its own.</p>
 *
 * <h2>Time source</h2>
 *
 * <p>Timestamps come from the injected {@link Clock}, consistent with the other distributed
 * limiters and with the in-memory Token Bucket. The consequence is that this algorithm is more
 * sensitive to clock skew than the window algorithms: there a skewed clock selects a different
 * key, whereas here the timestamp feeds the refill arithmetic directly. An instance whose clock
 * runs fast writes a {@code lastRefill} in the future, and other instances then compute no elapsed
 * time until the real clock catches up. The script clamps negative elapsed time to zero so a
 * skewed instance can never drain a shared bucket, but it cannot manufacture a cluster-wide clock.
 * Deployments are expected to keep instances NTP-synchronised.</p>
 *
 * <h2>Redis failures</h2>
 *
 * <p>Redis failures propagate as the Redis module's exceptions. Choosing between fail-open and
 * fail-closed is a separate documented deliverable and is deliberately not decided here.</p>
 */
public class RedisTokenBucketRateLimiter implements RateLimiter {

    /** Module segment of the Redis key, per the Engineering Contract key schema. */
    static final String KEY_MODULE = "ratelimiter";

    /** Resource segment identifying the Token Bucket algorithm. */
    static final String KEY_RESOURCE = "tokenbucket";

    /**
     * Lifetime of an idle bucket.
     *
     * <p>Documented in the Redis design as one hour, refreshed on access. Unlike the window
     * algorithms the bucket has no window to expire with, so its lifetime is measured from the
     * last request rather than from a window boundary: an active client's bucket persists, and an
     * abandoned one disappears on its own.</p>
     */
    static final Duration BUCKET_TTL = Duration.ofHours(1);

    private static final int RESULT_ALLOWED = 0;
    private static final int RESULT_REMAINING = 1;
    private static final int RESULT_RETRY_AFTER_MILLIS = 2;
    private static final int RESULT_TOKENS = 3;
    private static final int RESULT_SIZE = 4;

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final RedisService redisService;
    private final RedisKeyBuilder keyBuilder;

    public RedisTokenBucketRateLimiter(
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
        double refillRate = resolveRefillRate(policy, capacity);

        // Validation matches the in-memory Token Bucket exactly, including that an invalid
        // configuration is an error rather than a rejection: an unusable bucket is a deployment
        // fault, not a throttled client. Redis is not contacted.
        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate limit capacity must be strictly positive");
        }

        if (!Double.isFinite(refillRate) || refillRate <= 0.0) {
            throw new IllegalArgumentException("Refill rate must be strictly positive and finite");
        }

        Instant now = clock.instant();
        long nowMillis = now.toEpochMilli();
        double refillRatePerMillis = refillRate / 1000.0;

        String bucketKey = keyBuilder.buildKey(KEY_MODULE, KEY_RESOURCE, clientKey);

        List<?> result = redisService.executeLua(
                LuaScriptLoader.TOKEN_BUCKET_SCRIPT,
                List.class,
                List.of(bucketKey),
                List.of(String.valueOf(nowMillis),
                        String.valueOf(capacity),
                        String.valueOf(refillRatePerMillis),
                        String.valueOf(BUCKET_TTL.toSeconds())));

        if (result == null || result.size() < RESULT_SIZE) {
            throw new IllegalStateException(
                    "Redis returned an unusable token bucket result for key [" + bucketKey + "]");
        }

        boolean allowed = toLong(result.get(RESULT_ALLOWED), bucketKey) == 1L;

        if (allowed) {
            long remaining = toLong(result.get(RESULT_REMAINING), bucketKey);
            // The reset time is when the bucket would return to full, so it is derived from the
            // exact fractional count rather than the truncated whole tokens reported to the client.
            double tokens = toDouble(result.get(RESULT_TOKENS), bucketKey);
            long millisToFull = (long) Math.ceil((capacity - tokens) / refillRatePerMillis);
            Instant resetTime = Instant.ofEpochMilli(nowMillis + millisToFull);
            return RateLimitDecision.allowed(capacity, remaining, resetTime, RateLimitConstants.REASON_ALLOWED);
        }

        Duration retryAfter = Duration.ofMillis(toLong(result.get(RESULT_RETRY_AFTER_MILLIS), bucketKey));
        Instant resetTime = now.plus(retryAfter);
        return RateLimitDecision.rejected(capacity, resetTime, retryAfter, RateLimitConstants.REASON_EXCEEDED);
    }

    /**
     * Reads a whole-number element of the script result.
     *
     * <p>Redis returns Lua integers as {@link Long}, but the element is read through
     * {@link Number} so a differently-typed numeric does not fail the request.</p>
     */
    private static long toLong(Object value, String bucketKey) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException(
                "Redis returned a non-numeric token bucket field for key [" + bucketKey + "]: " + value);
    }

    /**
     * Reads the exact fractional token count from the script result.
     *
     * <p>The script returns this field as a string deliberately. Redis truncates a Lua number to an
     * integer on the way out — including inside a returned table — so returning it as a number
     * would discard the fractional part of the bucket on every call.</p>
     */
    private static double toDouble(Object value, String bucketKey) {
        String text = (value instanceof byte[] bytes) ? new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                : String.valueOf(value);
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Redis returned an unparseable token count for key [" + bucketKey + "]: " + text, e);
        }
    }

    /**
     * Resolves the policy snapshot governing a single request evaluation.
     *
     * <p>Called exactly once per {@code allowRequest}, so capacity and refill rate are always read
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
     * Resolves the refill rate in tokens per second, matching the in-memory implementation: a
     * policy window implies the rate that spends exactly one capacity over that window, otherwise
     * the configured default applies.
     */
    private double resolveRefillRate(RateLimitPolicy policy, long capacity) {
        if (policy != null && policy.window() != null && !policy.window().isZero() && !policy.window().isNegative()) {
            double windowSeconds = policy.window().toMillis() / 1000.0;
            return (double) capacity / windowSeconds;
        }
        return properties.getRefillRate();
    }
}
