package com.gauransh.gateway.observability.model;

/**
 * Health status payload for {@code GET /api/v1/monitoring/health}.
 */
public record HealthResponse(
        String status,
        String gateway,
        String redis,
        String disk,
        String memory
) {}
