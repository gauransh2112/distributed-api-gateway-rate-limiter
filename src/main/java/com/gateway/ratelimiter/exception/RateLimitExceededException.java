package com.gateway.ratelimiter.exception;

import com.gateway.shared.exception.GatewayException;

import java.time.Duration;

/**
 * Exception thrown when an incoming request exceeds rate limiting thresholds.
 *
 * <p>Translated by {@link com.gateway.shared.exception.GlobalExceptionHandler}
 * into an HTTP 429 (Too Many Requests) response envelope.</p>
 */
public class RateLimitExceededException extends GatewayException {

    public static final String ERROR_CODE = "RATE_LIMIT_EXCEEDED";

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, Duration retryAfter) {
        super(ERROR_CODE, message);
        this.retryAfterSeconds = retryAfter != null ? Math.max(0, retryAfter.toSeconds()) : 0;
    }

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(ERROR_CODE, message);
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
