package com.gauransh.gateway.dashboard.model;

/**
 * High-level system overview payload for {@code GET /api/v1/dashboard/overview}.
 */
public record DashboardOverviewResponse(
        String gatewayStatus,
        String redisStatus,
        double requestsPerSecond,
        double successRate,
        long blockedRequests,
        double averageLatency
) {}
