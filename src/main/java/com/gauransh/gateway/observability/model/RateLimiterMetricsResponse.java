package com.gauransh.gateway.observability.model;

/**
 * Rate Limiter operational statistics payload for {@code GET /api/v1/monitoring/ratelimiter}.
 */
public record RateLimiterMetricsResponse(
        long allowedRequests,
        long blockedRequests,
        String algorithm,
        double averageDecisionLatencyMs
) {}
