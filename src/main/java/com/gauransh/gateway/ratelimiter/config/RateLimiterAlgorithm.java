package com.gauransh.gateway.ratelimiter.config;

/**
 * Type-safe enumeration of supported rate limiting algorithms.
 */
public enum RateLimiterAlgorithm {
    NO_OP,
    FIXED_WINDOW,
    SLIDING_WINDOW_COUNTER,
    SLIDING_WINDOW_LOG,
    TOKEN_BUCKET,
    LEAKY_BUCKET
}
