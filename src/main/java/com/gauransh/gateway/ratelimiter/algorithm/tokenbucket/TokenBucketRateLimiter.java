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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-ready, thread-safe implementation of the Token Bucket rate limiting algorithm.
 *
 * <p>Token Bucket maintains a token pool for each client partition key. Tokens are refilled continuously
 * based on elapsed time at a configured refill rate and consumed on incoming requests. Allows controlled
 * request bursts up to maximum bucket capacity.</p>
 *
 * <p><strong>Thread-Safety Strategy:</strong> Uses a {@link ConcurrentHashMap} of client keys to
 * immutable {@link TokenBucket} state objects. Read, time-based refill calculation, capacity capping,
 * consumption, and state updating occur atomically inside
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)} per client key.</p>
 */
@Component("tokenBucketRateLimiter")
public class TokenBucketRateLimiter implements RateLimiter {

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketRateLimiter(RateLimiterProperties properties) {
        this(properties, new DefaultRateLimitKeyResolver(), null, Clock.systemUTC());
    }

    public TokenBucketRateLimiter(RateLimiterProperties properties, Clock clock) {
        this(properties, new DefaultRateLimitKeyResolver(), null, clock);
    }

    @Autowired
    public TokenBucketRateLimiter(
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
        double refillRate = resolveRefillRate(policy, capacity);

        if (capacity <= 0) {
            throw new IllegalArgumentException("Rate limit capacity must be strictly positive");
        }

        if (!Double.isFinite(refillRate) || refillRate <= 0.0) {
            throw new IllegalArgumentException("Refill rate must be strictly positive and finite");
        }

        Instant now = clock.instant();
        long nowMillis = now.toEpochMilli();
        double refillRatePerMillis = refillRate / 1000.0;

        AtomicReference<EvaluationResult> evalRef = new AtomicReference<>();

        buckets.compute(key, (clientKey, existingBucket) -> {
            if (existingBucket == null) {
                // Initial bucket state: starts at full capacity
                double currentTokens = (double) capacity;
                if (currentTokens >= 1.0) {
                    double remainingTokens = currentTokens - 1.0;
                    double tokensToFull = (double) capacity - remainingTokens;
                    long millisToFull = (long) Math.ceil(tokensToFull / refillRatePerMillis);
                    Instant resetTime = Instant.ofEpochMilli(nowMillis + millisToFull);
                    evalRef.set(new EvaluationResult(true, capacity, (long) remainingTokens, resetTime, Duration.ZERO));
                    return new TokenBucket(remainingTokens, nowMillis);
                } else {
                    double tokensNeeded = 1.0 - currentTokens;
                    long millisToWait = (long) Math.ceil(tokensNeeded / refillRatePerMillis);
                    Duration retryAfter = Duration.ofMillis(millisToWait);
                    Instant resetTime = now.plus(retryAfter);
                    evalRef.set(new EvaluationResult(false, capacity, 0L, resetTime, retryAfter));
                    return new TokenBucket(currentTokens, nowMillis);
                }
            }

            long lastRefill = existingBucket.lastRefillTimestampMillis();
            long elapsedMillis;

            // Handle clock regression (backward clock drift): elapsed = 0, preserve tokens, set timestamp to now
            if (nowMillis < lastRefill) {
                elapsedMillis = 0L;
            } else {
                elapsedMillis = nowMillis - lastRefill;
            }

            double tokensToAdd = elapsedMillis * refillRatePerMillis;
            double currentTokens = Math.min((double) capacity, existingBucket.tokens() + tokensToAdd);

            if (currentTokens >= 1.0) {
                double remainingTokens = currentTokens - 1.0;
                double tokensToFull = (double) capacity - remainingTokens;
                long millisToFull = (long) Math.ceil(tokensToFull / refillRatePerMillis);
                Instant resetTime = Instant.ofEpochMilli(nowMillis + millisToFull);
                evalRef.set(new EvaluationResult(true, capacity, (long) remainingTokens, resetTime, Duration.ZERO));
                return new TokenBucket(remainingTokens, nowMillis);
            } else {
                double tokensNeeded = 1.0 - currentTokens;
                long millisToWait = (long) Math.ceil(tokensNeeded / refillRatePerMillis);
                Duration retryAfter = Duration.ofMillis(millisToWait);
                Instant resetTime = now.plus(retryAfter);
                evalRef.set(new EvaluationResult(false, capacity, 0L, resetTime, retryAfter));
                // Materialize refilled tokens up to nowMillis on rejection
                return new TokenBucket(currentTokens, nowMillis);
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

    private double resolveRefillRate(RateLimitPolicy policy, long capacity) {
        if (policy != null && policy.window() != null && !policy.window().isZero() && !policy.window().isNegative()) {
            double windowSeconds = policy.window().toMillis() / 1000.0;
            return (double) capacity / windowSeconds;
        }
        return properties.getRefillRate();
    }

    void clearStorage() {
        buckets.clear();
    }

    private record EvaluationResult(
            boolean allowed,
            long capacity,
            long remaining,
            Instant resetTime,
            Duration retryAfter
    ) {}
}
