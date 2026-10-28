package com.gauransh.gateway.dashboard.model;

/**
 * System metadata and runtime environment payload for {@code GET /api/v1/dashboard/system}.
 */
public record SystemInfoResponse(
        String application,
        String version,
        String javaVersion,
        String springBootVersion,
        String uptime
) {}
