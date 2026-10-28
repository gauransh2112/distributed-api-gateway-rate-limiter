package com.gauransh.gateway.dashboard.model;

/**
 * Component-level operational health status payload for {@code GET /api/v1/dashboard/live}.
 */
public record LiveStatusResponse(
        String gateway,
        String redis,
        String authentication,
        String routing,
        String rateLimiter
) {}
