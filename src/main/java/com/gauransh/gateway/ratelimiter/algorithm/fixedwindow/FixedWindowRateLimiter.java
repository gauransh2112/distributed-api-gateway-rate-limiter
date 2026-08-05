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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Production-ready, thread-safe implementation of the Fixed Window Counter rate limiting algorithm.
 *
 * <p>Divides continuous time into non-overlapping fixed windows of configured duration.
 * Client request counts are tracked per partition key using an in-memory {@link ConcurrentHashMap}.
 * Window transitions and counter increments are executed atomically via bucket-level map synchronization.</p>
 */
@Component("fixedWindowRateLimiter")
public class FixedWindowRateLimiter implements RateLimiter {

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final ConcurrentHashMap<String, FixedWindow> windows = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(RateLimiterProperties properties) {
        this(properties, new DefaultRateLimitKeyResolver(), null, Clock.systemUTC());
    }

    public FixedWindowRateLimiter(RateLimiterProperties properties, Clock clock) {
        this(properties, new DefaultRateLimitKeyResolver(), null, clock);
    }

    @Autowired
    public FixedWindowRateLimiter(
            RateLimiterProperties properties,
            @Autowired(required = false) RateLimitKeyResolver keyResolver,
            @Autowired(required = false) RateLimitPolicyResolver policyResolver,
            @Autowired(required = false) Clock clock
    ) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.keyResolver = keyResolver != null ? keyResolver : new DefaultRateLimitKeyResolver();
        this.policyResolver = policyResolver;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public RateLimitDecision allowRequest(RateLimitContext context) {
        Objects.requireNonNull(context, "context must not be null");

        String rawKey = keyResolver.resolveKey(context);
        String key = (rawKey != null && !rawKey.isBlank()) ? rawKey : RateLimitConstants.DEFAULT_ANONYMOUS_KEY;

        long capacity = resolveCapacity(context);
        Duration windowDuration = resolveWindowDuration(context);

        if (windowDuration == null || windowDuration.isNegative() || windowDuration.isZero()) {
            throw new IllegalArgumentException("Rate limit window duration must be strictly positive");
        }

        long windowDurationMillis = windowDuration.toMillis();
        Instant now = clock.instant();
        long nowMillis = now.toEpochMilli();
        long currentWindowStart = (nowMillis / windowDurationMillis) * windowDurationMillis;
        Instant resetTime = Instant.ofEpochMilli(currentWindowStart + windowDurationMillis);

        // Immediate rejection for non-positive configured capacity
        if (capacity <= 0) {
            Duration retryAfter = Duration.between(now, resetTime);
            if (retryAfter.isNegative()) {
                retryAfter = Duration.ZERO;
            }
            return RateLimitDecision.rejected(0L, resetTime, retryAfter, RateLimitConstants.REASON_EXCEEDED);
        }

        // Atomically evaluate and update client window counter.
        // Checking inequality handles both expired past windows and backward clock drift.
        FixedWindow updatedWindow = windows.compute(key, (clientKey, existingWindow) -> {
            if (existingWindow == null || existingWindow.windowStartEpochMillis() != currentWindowStart) {
                return new FixedWindow(currentWindowStart, 1L);
            }
            return new FixedWindow(currentWindowStart, existingWindow.requestCount() + 1L);
        });

        long count = updatedWindow.requestCount();

        if (count <= capacity) {
            long remaining = Math.max(0, capacity - count);
            return RateLimitDecision.allowed(capacity, remaining, resetTime, RateLimitConstants.REASON_ALLOWED);
        }

        Duration retryAfter = Duration.between(now, resetTime);
        if (retryAfter.isNegative()) {
            retryAfter = Duration.ZERO;
        }

        return RateLimitDecision.rejected(capacity, resetTime, retryAfter, RateLimitConstants.REASON_EXCEEDED);
    }

    private long resolveCapacity(RateLimitContext context) {
        if (policyResolver != null) {
            RateLimitPolicy policy = policyResolver.resolvePolicy(context);
            if (policy != null) {
                return policy.capacity();
            }
        }
        return properties.getDefaultCapacity();
    }

    private Duration resolveWindowDuration(RateLimitContext context) {
        if (policyResolver != null) {
            RateLimitPolicy policy = policyResolver.resolvePolicy(context);
            if (policy != null && policy.window() != null) {
                return policy.window();
            }
        }
        return properties.getDefaultWindow();
    }

    /**
     * Clears all in-memory rate limit windows. Intended for testing and reset scenarios.
     */
    public void clearStorage() {
        windows.clear();
    }

    /**
     * Returns the number of active client windows currently held in memory.
     */
    public int getActiveWindowCount() {
        return windows.size();
    }
}

