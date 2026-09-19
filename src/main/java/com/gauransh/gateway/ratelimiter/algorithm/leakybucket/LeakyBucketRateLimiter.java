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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-ready, thread-safe implementation of the Leaky Bucket rate limiting algorithm.
 *
 * <p>Leaky Bucket maintains a water level (request volume) for each client partition key.
 * Water drains out (leaks) continuously based on elapsed time at a configured leak rate.
 * Incoming requests attempt to add 1 unit to the bucket; if adding the request would exceed
 * maximum bucket capacity, the request overflows and is rejected, smoothing outbound traffic.</p>
 *
 * <p><strong>Thread-Safety Strategy:</strong> Uses a {@link ConcurrentHashMap} of client keys to
 * immutable {@link LeakyBucket} state objects. Read, time-based leak calculation, capacity boundary
 * evaluation, water level increment, and state updating occur atomically inside
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} per client key.</p>
 */
@Component("leakyBucketRateLimiter")
public class LeakyBucketRateLimiter implements RateLimiter {

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final ConcurrentHashMap<String, LeakyBucket> buckets = new ConcurrentHashMap<>();

    public LeakyBucketRateLimiter(RateLimiterProperties properties) {
        this(properties, new DefaultRateLimitKeyResolver(), null, Clock.systemUTC());
    }

    public LeakyBucketRateLimiter(RateLimiterProperties properties, Clock clock) {
        this(properties, new DefaultRateLimitKeyResolver(), null, clock);
    }

    @Autowired
    public LeakyBucketRateLimiter(
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
        double leakRate = resolveLeakRate(policy, capacity);

        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate limit capacity must be strictly positive");
        }

        if (!Double.isFinite(leakRate) || leakRate <= 0.0) {
            throw new IllegalArgumentException("Leak rate must be strictly positive and finite");
        }

        Instant now = clock.instant();
        long nowMillis = now.toEpochMilli();
        double leakRatePerMillis = leakRate / 1000.0;

        AtomicReference<EvaluationResult> evalRef = new AtomicReference<>();

        buckets.compute(key, (clientKey, existingBucket) -> {
            if (existingBucket == null) {
                // Initial bucket state: starts empty (water level = 0.0)
                double initialWaterLevel = 0.0;
                double newWaterLevel = initialWaterLevel + 1.0;

                if (newWaterLevel <= (double) capacity) {
                    long remaining = (long) Math.max(0, capacity - (long) Math.ceil(newWaterLevel));
                    long millisToEmpty = (long) Math.ceil(newWaterLevel / leakRatePerMillis);
                    Instant resetTime = Instant.ofEpochMilli(nowMillis + millisToEmpty);
                    evalRef.set(new EvaluationResult(true, capacity, remaining, resetTime, Duration.ZERO));
                    return new LeakyBucket(newWaterLevel, nowMillis);
                } else {
                    double waterNeeded = newWaterLevel - (double) capacity;
                    long millisToWait = Math.max(1L, (long) Math.ceil(waterNeeded / leakRatePerMillis));
                    Duration retryAfter = Duration.ofMillis(millisToWait);
                    Instant resetTime = now.plus(retryAfter);
                    evalRef.set(new EvaluationResult(false, capacity, 0L, resetTime, retryAfter));
                    return new LeakyBucket(initialWaterLevel, nowMillis);
                }
            }

            long lastLeak = existingBucket.lastLeakTimestampMillis();
            long elapsedMillis;
            long updatedLastLeak;

            // Invariant: lastLeakTimestampMillis must NEVER move backwards.
            // Backward clock drift sets elapsedMillis = 0 and preserves existing lastLeak timestamp.
            if (nowMillis < lastLeak) {
                elapsedMillis = 0L;
                updatedLastLeak = lastLeak;
            } else {
                elapsedMillis = nowMillis - lastLeak;
                updatedLastLeak = nowMillis;
            }

            double leakedAmount = elapsedMillis * leakRatePerMillis;
            double currentWaterLevel = Math.max(0.0, existingBucket.waterLevel() - leakedAmount);

            if (currentWaterLevel + 1.0 <= (double) capacity) {
                double newWaterLevel = currentWaterLevel + 1.0;
                long remaining = (long) Math.max(0, capacity - (long) Math.ceil(newWaterLevel));
                long millisToEmpty = (long) Math.ceil(newWaterLevel / leakRatePerMillis);
                Instant resetTime = Instant.ofEpochMilli(updatedLastLeak + millisToEmpty);
                evalRef.set(new EvaluationResult(true, capacity, remaining, resetTime, Duration.ZERO));
                return new LeakyBucket(newWaterLevel, updatedLastLeak);
            } else {
                double waterNeeded = (currentWaterLevel + 1.0) - (double) capacity;
                long millisToWait = Math.max(1L, (long) Math.ceil(waterNeeded / leakRatePerMillis));
                Duration retryAfter = Duration.ofMillis(millisToWait);
                Instant resetTime = now.plus(retryAfter);
                evalRef.set(new EvaluationResult(false, capacity, 0L, resetTime, retryAfter));
                // Materialize drained water level up to updatedLastLeak on rejection
                return new LeakyBucket(currentWaterLevel, updatedLastLeak);
            }
        });

        EvaluationResult eval = evalRef.get();
        if (eval.allowed()) {
            return RateLimitDecision.allowed(eval.capacity(), eval.remaining(), eval.resetTime(), RateLimitConstants.REASON_ALLOWED);
        }

        return RateLimitDecision.rejected(eval.capacity(), eval.resetTime(), eval.retryAfter(), RateLimitConstants.REASON_EXCEEDED);
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

    private double resolveLeakRate(RateLimitPolicy policy, long capacity) {
        if (policy != null && policy.window() != null && !policy.window().isZero() && !policy.window().isNegative()) {
            double windowSeconds = policy.window().toMillis() / 1000.0;
            return (double) capacity / windowSeconds;
        }
        return properties.getRefillRate();
    }

    void clearStorage() {
        buckets.clear();
    }

    /**
     * Package-private accessor for testing invariant assertions on internal bucket state.
     */
    LeakyBucket getBucketState(String key) {
        return buckets.get(key);
    }

    private record EvaluationResult(
            boolean allowed,
            long capacity,
            long remaining,
            Instant resetTime,
            Duration retryAfter
    ) {}
}
