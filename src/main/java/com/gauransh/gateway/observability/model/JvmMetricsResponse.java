package com.gauransh.gateway.observability.model;

/**
 * JVM runtime statistics payload for {@code GET /api/v1/monitoring/jvm}.
 */
public record JvmMetricsResponse(
        long heapUsedMb,
        long heapMaxMb,
        double cpuUsage,
        int threads,
        long uptimeSeconds
) {}
