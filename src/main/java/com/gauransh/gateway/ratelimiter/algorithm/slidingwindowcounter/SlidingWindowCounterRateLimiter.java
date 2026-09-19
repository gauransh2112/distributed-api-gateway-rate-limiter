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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-ready, thread-safe implementation of the Sliding Window Counter rate limiting algorithm.
 *
 * <p>Estimates the current request rate over a rolling sliding window frame by combining
 * the current fixed window counter with a weighted fraction of the previous fixed window counter.
 * Window transitions and counter increments are executed atomically via bucket-level map synchronization.</p>
 */
@Component("slidingWindowCounterRateLimiter")
public class SlidingWindowCounterRateLimiter implements RateLimiter {

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final ConcurrentHashMap<String, SlidingWindowCounter> counters = new ConcurrentHashMap<>();

    public SlidingWindowCounterRateLimiter(RateLimiterProperties properties) {
        this(properties, new DefaultRateLimitKeyResolver(), null, Clock.systemUTC());
    }

    public SlidingWindowCounterRateLimiter(RateLimiterProperties properties, Clock clock) {
        this(properties, new DefaultRateLimitKeyResolver(), null, clock);
    }

    @Autowired
    public SlidingWindowCounterRateLimiter(
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

        RateLimitPolicy policy = resolvePolicy(context);
        long capacity = resolveCapacity(policy);
        Duration windowDuration = resolveWindowDuration(policy);

        if (windowDuration == null || windowDuration.isNegative() || windowDuration.isZero()) {
            throw new IllegalArgumentException("Rate limit window duration must be strictly positive");
        }

        long windowDurationMillis = windowDuration.toMillis();
        Instant now = clock.instant();
        long nowMillis = now.toEpochMilli();
        long currentWindowStart = (nowMillis / windowDurationMillis) * windowDurationMillis;
        long previousWindowStart = currentWindowStart - windowDurationMillis;
        Instant resetTime = Instant.ofEpochMilli(currentWindowStart + windowDurationMillis);

        // Immediate rejection for non-positive configured capacity
        if (capacity <= 0) {
            Duration retryAfter = Duration.between(now, resetTime);
            if (retryAfter.isNegative()) {
                retryAfter = Duration.ZERO;
            }
            return RateLimitDecision.rejected(0L, resetTime, retryAfter, RateLimitConstants.REASON_EXCEEDED);
        }

        AtomicReference<EvaluationResult> evaluationRef = new AtomicReference<>();

        counters.compute(key, (clientKey, existing) -> {
            long prevCount;
            long currCount;

            if (existing == null) {
                prevCount = 0L;
                currCount = 0L;
            } else if (existing.currentWindowStartEpochMillis() == currentWindowStart) {
                prevCount = existing.previousCount();
                currCount = existing.currentCount();
            } else if (existing.currentWindowStartEpochMillis() == previousWindowStart) {
                // Immediate next window frame rollover
                prevCount = existing.currentCount();
                currCount = 0L;
            } else if (existing.currentWindowStartEpochMillis() < previousWindowStart) {
                // Multiple windows passed; previous count decayed to zero
                prevCount = 0L;
                currCount = 0L;
            } else {
                // Backward clock drift detected; reset window frame safely
                prevCount = 0L;
                currCount = 0L;
            }

            double elapsedMillis = Math.max(0L, nowMillis - currentWindowStart);
            double previousWeight = Math.max(0.0, (double) (windowDurationMillis - elapsedMillis) / windowDurationMillis);
            double estimatedCountBefore = (prevCount * previousWeight) + currCount;

            if (estimatedCountBefore + 1.0 <= capacity) {
                long updatedCurrCount = currCount + 1L;
                double estimatedCountAfter = estimatedCountBefore + 1.0;
                long remaining = Math.max(0L, (long) Math.floor(capacity - estimatedCountAfter));
                evaluationRef.set(new EvaluationResult(true, remaining));
                return new SlidingWindowCounter(currentWindowStart, updatedCurrCount, prevCount);
            } else {
                evaluationRef.set(new EvaluationResult(false, 0L));
                return new SlidingWindowCounter(currentWindowStart, currCount, prevCount);
            }
        });

        EvaluationResult eval = evaluationRef.get();
        if (eval.allowed()) {
            return RateLimitDecision.allowed(capacity, eval.remaining(), resetTime, RateLimitConstants.REASON_ALLOWED);
        }

        Duration retryAfter = Duration.between(now, resetTime);
        if (retryAfter.isNegative()) {
            retryAfter = Duration.ZERO;
        }

        return RateLimitDecision.rejected(capacity, resetTime, retryAfter, RateLimitConstants.REASON_EXCEEDED);
    }

    /**
     * Resolves the policy snapshot governing a single request evaluation.
     *
     * <p>Called exactly once per {@code allowRequest}. Every policy-derived value used by that
     * evaluation is read from the returned snapshot, so one request can never combine values taken
     * from two different resolutions — for example a capacity from one policy version and a window
     * from another, yielding a decision matching no configured policy.</p>
     *
     * <p>The resolver is an optional injected strategy; {@code null} means no policy applies and
     * configured defaults are used.</p>
     *
     * @param context request context
     * @return the resolved policy, or {@code null} when no resolver is configured or none matches
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

    /**
     * Clears all in-memory rate limit counters. Intended for testing and reset scenarios.
     */
    public void clearStorage() {
        counters.clear();
    }

    /**
     * Returns the number of active client counters currently held in memory.
     */
    public int getActiveWindowCount() {
        return counters.size();
    }

    private record EvaluationResult(boolean allowed, long remaining) {}
}
