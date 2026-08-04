package com.gateway.ratelimiter.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable domain object representing the outcome of a rate limiting evaluation.
 *
 * @param allowed true if the request is permitted; false if rate limited
 * @param limit maximum requests permitted within the evaluation window (null if unconstrained)
 * @param remainingRequests remaining allowed requests in current window (null if unconstrained)
 * @param resetTime timestamp when current window resets (null if unconstrained)
 * @param retryAfter duration client must wait before retrying (populated when allowed is false)
 * @param reason human-readable justification for the decision
 */
public record RateLimitDecision(
        boolean allowed,
        Long limit,
        Long remainingRequests,
        Instant resetTime,
        Duration retryAfter,
        String reason
) {
    public RateLimitDecision {
        Objects.requireNonNull(reason, "reason must not be null");
        retryAfter = retryAfter != null ? retryAfter : Duration.ZERO;
    }

    /**
     * Indicates whether this decision carries rate limit headers to be rendered in HTTP responses.
     */
    public boolean includesHeaders() {
        return limit != null && remainingRequests != null;
    }

    public static RateLimitDecision allowed(long limit, long remainingRequests, Instant resetTime, String reason) {
        return new RateLimitDecision(true, limit, remainingRequests, resetTime, Duration.ZERO, reason);
    }

    public static RateLimitDecision unlimited(String reason) {
        return new RateLimitDecision(true, null, null, null, Duration.ZERO, reason);
    }

    public static RateLimitDecision rejected(long limit, Instant resetTime, Duration retryAfter, String reason) {
        return new RateLimitDecision(false, limit, 0L, resetTime, retryAfter, reason);
    }
}
