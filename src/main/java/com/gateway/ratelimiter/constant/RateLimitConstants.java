package com.gateway.ratelimiter.constant;

/**
 * Centralized constants for the Rate Limiting module.
 */
public final class RateLimitConstants {

    private RateLimitConstants() {
        // Utility class
    }

    // HTTP Header Constants
    public static final String HEADER_LIMIT = "X-RateLimit-Limit";
    public static final String HEADER_REMAINING = "X-RateLimit-Remaining";
    public static final String HEADER_RESET = "X-RateLimit-Reset";
    public static final String HEADER_RETRY_AFTER = "Retry-After";

    // Request Header Constants
    public static final String HEADER_API_KEY = "X-API-Key";
    public static final String HEADER_CLIENT_ID = "X-Client-Id";
    public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    // Error Codes
    public static final String ERROR_CODE_RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED";

    // Decision Reason Constants
    public static final String REASON_NO_OP = "NO_OP_RATE_LIMITER";
    public static final String REASON_ALLOWED = "ALLOWED";
    public static final String REASON_EXCEEDED = "RATE_LIMIT_EXCEEDED";
    public static final String REASON_UNLIMITED = "UNLIMITED_QUOTA";

    // Fallback Constants
    public static final String DEFAULT_ANONYMOUS_KEY = "anonymous";
    public static final String DEFAULT_LOCAL_IP = "127.0.0.1";
}
