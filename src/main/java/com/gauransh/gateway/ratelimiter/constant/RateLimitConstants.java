package com.gauransh.gateway.ratelimiter.constant;

import java.util.Set;

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

    /**
     * Operational endpoints, which are not rate limited.
     *
     * <p>Rate limiting applies to requests travelling the forwarding path toward an upstream. These
     * endpoints are terminated by the Gateway itself and are never forwarded, so they fall outside
     * that scope — see the API Specification, "Operational Endpoints — Rate Limited: No", and
     * ADR-0016's Endpoint Scope.</p>
     *
     * <p>They must also stay answerable precisely when the Gateway is unhealthy. Rate limiting them
     * made the Gateway unable to report degraded health on Redis unavailability, which ADR-0008
     * requires, because the refusal happened before the health subsystem ran.</p>
     *
     * <p>Deliberately an explicit list rather than a configurable pattern mechanism: only endpoints
     * the application actually exposes are listed, and no general path-exclusion capability is
     * introduced.</p>
     */
    public static final Set<String> OPERATIONAL_ENDPOINTS = Set.of(
            "/health",
            "/ready",
            "/actuator/health");

    /**
     * Prefix covering Spring's health groups, for example {@code /actuator/health/readiness}.
     *
     * <p>Held separately because a sub-path of the dependency-health endpoint must not be blocked
     * either; blocking one would reintroduce the same defect by a narrower route.</p>
     */
    public static final String OPERATIONAL_HEALTH_PREFIX = "/actuator/health/";
}
