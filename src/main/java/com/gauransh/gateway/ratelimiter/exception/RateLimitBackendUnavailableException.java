package com.gauransh.gateway.ratelimiter.exception;

import com.gauransh.gateway.shared.exception.GatewayException;

/**
 * Raised when a request is refused because the distributed rate limiting state is unavailable.
 *
 * <p>Carries the {@code FAIL_CLOSED} outcome of the configured failure policy (ADR-0016) to the
 * response layer, where it becomes {@code 503 Service Unavailable} — the status the Error Catalog
 * specifies for Redis connection and timeout failures.</p>
 *
 * <p>The message is deliberately generic. The originating Redis failure is logged at ERROR with its
 * full cause, but nothing about the Gateway's storage internals reaches the client.</p>
 */
public class RateLimitBackendUnavailableException extends GatewayException {

    /** Error code for a rate limiting decision that could not be made. */
    public static final String ERROR_CODE = "RATE_LIMIT_BACKEND_UNAVAILABLE";

    private static final String CLIENT_MESSAGE = "Rate limiting is temporarily unavailable. Please retry.";

    public RateLimitBackendUnavailableException(Throwable cause) {
        super(ERROR_CODE, CLIENT_MESSAGE, cause);
    }
}
