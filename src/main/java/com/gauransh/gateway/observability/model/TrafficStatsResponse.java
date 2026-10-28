package com.gauransh.gateway.observability.model;

/**
 * Traffic statistics payload for {@code GET /api/v1/monitoring/traffic}.
 */
public record TrafficStatsResponse(
        long requests,
        long successful,
        long failed,
        double averageLatencyMs
) {}
