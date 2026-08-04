package com.gauransh.gateway.ratelimiter.model;

import com.gauransh.gateway.ratelimiter.config.RateLimiterAlgorithm;

import java.time.Duration;
import java.util.Objects;

/**
 * Value object representing a production rate limiting policy specification.
 *
 * @param policyName unique identifier or descriptive name of the policy
 * @param capacity maximum permitted requests within the window duration
 * @param window duration of the rate limit window
 * @param burstCapacity additional allowed burst request capacity above standard capacity
 * @param algorithm rate limiting algorithm governing this policy
 * @param enabled whether this specific policy is active
 * @param priority rule evaluation priority (lower value = higher priority)
 * @param scope target evaluation scope (e.g., GLOBAL, PER_CLIENT, PER_ROUTE, PER_IP)
 */
public record RateLimitPolicy(
        String policyName,
        long capacity,
        Duration window,
        long burstCapacity,
        RateLimiterAlgorithm algorithm,
        boolean enabled,
        int priority,
        String scope
) {
    public RateLimitPolicy {
        Objects.requireNonNull(policyName, "policyName must not be null");
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        Objects.requireNonNull(window, "window must not be null");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window duration must be positive");
        }
        if (burstCapacity < 0) {
            throw new IllegalArgumentException("burstCapacity cannot be negative");
        }
        Objects.requireNonNull(algorithm, "algorithm must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
    }

    public static RateLimitPolicy unlimited() {
        return new RateLimitPolicy(
                "UNLIMITED_DEFAULT",
                Long.MAX_VALUE,
                Duration.ofDays(365),
                0L,
                RateLimiterAlgorithm.NO_OP,
                true,
                Integer.MAX_VALUE,
                "GLOBAL"
        );
    }
}
