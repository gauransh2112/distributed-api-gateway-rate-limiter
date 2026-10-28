package com.gauransh.gateway.observability.model;

/**
 * Gateway runtime metrics payload for {@code GET /api/v1/monitoring/metrics}.
 */
public record GatewayMetricsResponse(
        long requestsTotal,
        double requestsPerSecond,
        double successRate,
        double errorRate,
        double averageLatencyMs
) {}
