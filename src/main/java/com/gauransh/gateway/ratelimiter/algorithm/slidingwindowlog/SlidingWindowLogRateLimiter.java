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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Production-ready, thread-safe implementation of the Sliding Window Log rate limiting algorithm.
 *
 * <p>Records exact request timestamps in a chronological log per partition key. Evaluates
 * request rate over a rolling window frame of configured duration by evicting expired timestamps
 * and checking active request count against capacity.</p>

 * <p><strong>Thread-Safety Strategy:</strong> Uses a {@link ConcurrentHashMap} of client keys to
 * {@link SlidingWindowLog} instances. All read, eviction, evaluation, and insertion operations
 * on the underlying {@link java.util.ArrayDeque} occur atomically inside
 * {@link ConcurrentHashMap#compute(Object, java.util.function.BiFunction)}, guaranteeing
 * thread safety without global lock contention.</p>
 */
@Component("slidingWindowLogRateLimiter")
public class SlidingWindowLogRateLimiter implements RateLimiter {

    private final RateLimiterProperties properties;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitPolicyResolver policyResolver;
    private final Clock clock;
    private final ConcurrentHashMap<String, SlidingWindowLog> logs = new ConcurrentHashMap<>();

    public SlidingWindowLogRateLimiter(RateLimiterProperties properties) {
        this(properties, new DefaultRateLimitKeyResolver(), null, Clock.systemUTC());
    }

    public SlidingWindowLogRateLimiter(RateLimiterProperties properties, Clock clock) {
        this(properties, new DefaultRateLimitKeyResolver(), null, clock);
    }

    @Autowired
    public SlidingWindowLogRateLimiter(
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
        long windowStartCutoffMillis = nowMillis - windowDurationMillis;
        Instant defaultResetTime = Instant.ofEpochMilli(nowMillis + windowDurationMillis);

        // Immediate rejection for non-positive configured capacity
        if (capacity <= 0) {
            Duration retryAfter = Duration.between(now, defaultResetTime);
            if (retryAfter.isNegative()) {
                retryAfter = Duration.ZERO;
            }
            return RateLimitDecision.rejected(0L, defaultResetTime, retryAfter, RateLimitConstants.REASON_EXCEEDED);
        }

        AtomicReference<EvaluationResult> evaluationRef = new AtomicReference<>();

        logs.compute(key, (clientKey, existingLog) -> {
            SlidingWindowLog log = (existingLog != null) ? existingLog : new SlidingWindowLog();

            // Reset log on backward clock drift to maintain monotonic timestamp ordering in ArrayDeque
            Long newestTimestamp = log.getNewestTimestamp();
            if (newestTimestamp != null && nowMillis < newestTimestamp) {
                log.clear();
            }

            log.evictExpired(windowStartCutoffMillis);

            if (log.size() < capacity) {
                log.addTimestamp(nowMillis);
                long remaining = capacity - log.size();
                Instant resetTime = Instant.ofEpochMilli(log.getOldestTimestamp() + windowDurationMillis);
                evaluationRef.set(new EvaluationResult(true, capacity, remaining, resetTime, Duration.ZERO));
                return log;
            } else {
                Long oldestTimestamp = log.getOldestTimestamp();
                Instant resetTime = (oldestTimestamp != null)
                        ? Instant.ofEpochMilli(oldestTimestamp + windowDurationMillis)
                        : defaultResetTime;
                Duration retryAfter = Duration.between(now, resetTime);
                if (retryAfter.isNegative()) {
                    retryAfter = Duration.ZERO;
                }
                evaluationRef.set(new EvaluationResult(false, capacity, 0L, resetTime, retryAfter));

                // Clean up map entry if log is empty and state is not needed
                if (log.isEmpty()) {
                    return null;
                }
                return log;
            }
        });

        EvaluationResult eval = evaluationRef.get();
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

    private Duration resolveWindowDuration(RateLimitPolicy policy) {
        if (policy != null && policy.window() != null) {
            return policy.window();
        }
        return properties.getDefaultWindow();
    }

    /**
     * Clears all in-memory rate limit logs. Intended for testing and reset scenarios.
     */
    public void clearStorage() {
        logs.clear();
    }

    /**
     * Returns the number of active client logs currently held in memory.
     */
    public int getActiveWindowCount() {
        return logs.size();
    }

    private record EvaluationResult(
            boolean allowed,
            long capacity,
            long remaining,
            Instant resetTime,
            Duration retryAfter
    ) {}
}
