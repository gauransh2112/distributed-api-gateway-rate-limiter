package com.gauransh.gateway.ratelimiter.algorithm.fixedwindow;

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
 * Distributed Fixed Window Counter rate limiter backed by Redis.
 *
 * <p>Functionally equivalent to {@link FixedWindowRateLimiter}, but the window counter lives in
 * Redis instead of the JVM heap. Every Gateway instance evaluating the same client key therefore
 * shares one counter, so a client cannot obtain a separate quota per instance. This is the
 * property that makes rate limiting correct once the Gateway scales horizontally.</p>
 *
 * <h2>Atomicity</h2>
 *
 * <p>The window transition is "increment the counter, and establish its expiration if it has
 * none". Those two commands must not interleave with another instance's, so they execute inside
 * {@code increment.lua} through {@link RedisService#executeLua}. Redis runs a script to completion
 * without interruption, which makes the transition atomic across every Gateway instance — a
 * guarantee no JVM lock can provide, since a lock coordinates only threads inside one process.</p>
 *
 * <p>The script applies the TTL only when the key currently has no expiration. That is what keeps
 * the window <em>fixed</em> rather than sliding: later requests in the same window increment the
 * counter without extending its lifetime.</p>
 *
 * <h2>State</h2>
 *
 * <p>This limiter holds no mutable state. Unlike the in-memory algorithms it owns no map, so it
 * introduces no shared mutable state into the JVM and needs no concurrency mechanism of its own;
 * correctness rests entirely on Redis-side atomicity.</p>
 *
 * <h2>Redis failures</h2>
 *
 * <p>Redis failures propagate as the Redis module's exceptions. Choosing between fail-open and
 * fail-closed is a separate documented deliverable and is deliberately not decided here.</p>
 */
public class RedisFixedWindowRateLimiter implements RateLimiter {

    /** Module segment of the Redis key, per the Engineering Contract key schema. */
    static final String KEY_MODULE = "ratelimiter";

    /** Resource segment identifying the Fixed Window algorithm. */
    static final String KEY_RESOURCE = "fixed";

    /**
     * Safety buffer added to the window duration when setting the counter's TTL.
     *
     * <p>Documented in the Redis design as window duration plus a buffer (60s + 10s = 70s). The
     * buffer prevents a counter expiring fractionally before its window closes because of clock
     * differences between Gateway instances or processing delay.</p>
     */
    static final Duration TTL_SAFETY_BUFFER = Duration.ofSeconds(10);

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final RedisService redisService;
    private final RedisKeyBuilder keyBuilder;

    public RedisFixedWindowRateLimiter(
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
        Instant resetTime = Instant.ofEpochMilli(currentWindowStartMillis + windowDurationMillis);

        // Immediate rejection for non-positive configured capacity, matching the in-memory
        // algorithm. Redis is not contacted, because no counter transition is warranted.
        if (capacity <= 0) {
            return RateLimitDecision.rejected(0L, resetTime, retryAfter(now, resetTime),
                    RateLimitConstants.REASON_EXCEEDED);
        }

        String redisKey = buildWindowKey(clientKey, currentWindowStartMillis);
        long ttlSeconds = windowDuration.plus(TTL_SAFETY_BUFFER).toSeconds();

        Long count = redisService.executeLua(
                LuaScriptLoader.INCREMENT_SCRIPT,
                Long.class,
                List.of(redisKey),
                List.of("1", String.valueOf(ttlSeconds)));

        if (count == null) {
            throw new IllegalStateException(
                    "Redis returned no counter value for fixed window key [" + redisKey + "]");
        }

        if (count <= capacity) {
            long remaining = Math.max(0L, capacity - count);
            return RateLimitDecision.allowed(capacity, remaining, resetTime, RateLimitConstants.REASON_ALLOWED);
        }

        return RateLimitDecision.rejected(capacity, resetTime, retryAfter(now, resetTime),
                RateLimitConstants.REASON_EXCEEDED);
    }

    /**
     * Builds the Redis key for one client's current window.
     *
     * <p>Produced through {@link RedisKeyBuilder} so the key obeys the Engineering Contract schema
     * {@code environment:module:resource:identifier}. The identifier combines the client key with
     * the window start, so each window gets its own counter and an expired window's counter is
     * simply abandoned to its TTL rather than needing to be reset.</p>
     *
     * <p>Example: {@code dev:ratelimiter:fixed:user-101:1722587600}</p>
     *
     * <p>The window component is expressed in epoch seconds, matching the documented key example.
     * Window durations shorter than one second would therefore map adjacent windows onto the same
     * key; such durations are outside the documented configuration surface, whose windows are
     * expressed in seconds or minutes.</p>
     */
    private String buildWindowKey(String clientKey, long windowStartMillis) {
        long windowStartSeconds = windowStartMillis / 1000L;
        return keyBuilder.buildKey(KEY_MODULE, KEY_RESOURCE, clientKey + ":" + windowStartSeconds);
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
