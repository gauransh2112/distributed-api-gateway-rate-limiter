package com.gauransh.gateway.dashboard.model;

/**
 * Rate limiter telemetry summary payload for {@code GET /api/v1/dashboard/ratelimiter}.
 */
public record RateLimiterDashboardResponse(
        String algorithm,
        long allowedRequests,
        long blockedRequests,
        double averageDecisionLatency,
        double redisLatency
) {}
