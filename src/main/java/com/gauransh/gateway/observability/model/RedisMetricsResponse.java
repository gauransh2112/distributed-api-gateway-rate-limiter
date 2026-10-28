package com.gauransh.gateway.observability.model;

/**
 * Redis operational metrics payload for {@code GET /api/v1/monitoring/redis}.
 */
public record RedisMetricsResponse(
        String status,
        double averageLatencyMs,
        long connections,
        long commandsExecuted,
        long luaExecutions
) {}
