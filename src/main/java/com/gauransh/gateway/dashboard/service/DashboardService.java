package com.gauransh.gateway.dashboard.service;

import com.gauransh.gateway.dashboard.model.DashboardOverviewResponse;
import com.gauransh.gateway.dashboard.model.LiveStatusResponse;
import com.gauransh.gateway.dashboard.model.RateLimiterDashboardResponse;
import com.gauransh.gateway.dashboard.model.SystemInfoResponse;
import com.gauransh.gateway.observability.health.SystemHealthIndicator;
import com.gauransh.gateway.observability.metrics.MetricsService;
import com.gauransh.gateway.observability.model.GatewayMetricsResponse;
import com.gauransh.gateway.observability.model.HealthResponse;
import com.gauransh.gateway.observability.model.RateLimiterMetricsResponse;
import com.gauransh.gateway.observability.model.RedisMetricsResponse;
import org.springframework.boot.SpringBootVersion;
import org.springframework.stereotype.Service;

/**
 * Service aggregating lower-level observability metrics and health indicators into
 * dashboard-tailored presentation representations.
 */
@Service
public class DashboardService {

    private final MetricsService metricsService;
    private final SystemHealthIndicator healthIndicator;

    public DashboardService(MetricsService metricsService, SystemHealthIndicator healthIndicator) {
        this.metricsService = metricsService;
        this.healthIndicator = healthIndicator;
    }

    /**
     * Constructs {@link DashboardOverviewResponse} for {@code GET /api/v1/dashboard/overview}.
     */
    public DashboardOverviewResponse getOverview() {
        HealthResponse health = healthIndicator.checkHealth();
        GatewayMetricsResponse metrics = metricsService.getGatewayMetrics();
        RateLimiterMetricsResponse rl = metricsService.getRateLimiterMetrics();

        return new DashboardOverviewResponse(
                health.gateway(),
                health.redis(),
                metrics.requestsPerSecond(),
                metrics.successRate(),
                rl.blockedRequests(),
                metrics.averageLatencyMs()
        );
    }

    /**
     * Constructs {@link LiveStatusResponse} for {@code GET /api/v1/dashboard/live}.
     */
    public LiveStatusResponse getLiveStatus() {
        HealthResponse health = healthIndicator.checkHealth();
        return new LiveStatusResponse(
                health.gateway(),
                health.redis(),
                "UP",
                "UP",
                "UP"
        );
    }


    /**
     * Constructs {@link RateLimiterDashboardResponse} for {@code GET /api/v1/dashboard/ratelimiter}.
     */
    public RateLimiterDashboardResponse getRateLimiterDashboard() {
        RateLimiterMetricsResponse rl = metricsService.getRateLimiterMetrics();
        RedisMetricsResponse redis = metricsService.getRedisMetrics();

        return new RateLimiterDashboardResponse(
                rl.algorithm(),
                rl.allowedRequests(),
                rl.blockedRequests(),
                rl.averageDecisionLatencyMs(),
                redis.averageLatencyMs()
        );
    }

    /**
     * Constructs {@link SystemInfoResponse} for {@code GET /api/v1/dashboard/system}.
     */
    public SystemInfoResponse getSystemInfo() {
        long uptimeSec = (System.currentTimeMillis() - metricsService.getStartTimeMs()) / 1000L;
        long days = uptimeSec / (24 * 3600);
        long hours = (uptimeSec % (24 * 3600)) / 3600;
        long minutes = (uptimeSec % 3600) / 60;
        String uptimeFormatted = String.format("%dd %dh %dm", days, hours, minutes);

        String sbVersion = SpringBootVersion.getVersion();
        if (sbVersion == null || sbVersion.isBlank()) {
            sbVersion = "3.3.2";
        }

        return new SystemInfoResponse(
                "Distributed API Gateway",
                "1.0.0-SNAPSHOT",
                System.getProperty("java.version"),
                sbVersion,
                uptimeFormatted
        );
    }
}
