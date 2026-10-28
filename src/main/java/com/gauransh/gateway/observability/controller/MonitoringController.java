package com.gauransh.gateway.observability.controller;

import com.gauransh.gateway.observability.health.SystemHealthIndicator;
import com.gauransh.gateway.observability.metrics.MetricsService;
import com.gauransh.gateway.observability.model.GatewayMetricsResponse;
import com.gauransh.gateway.observability.model.HealthResponse;
import com.gauransh.gateway.observability.model.JvmMetricsResponse;
import com.gauransh.gateway.observability.model.RateLimiterMetricsResponse;
import com.gauransh.gateway.observability.model.RedisMetricsResponse;
import com.gauransh.gateway.observability.model.TrafficStatsResponse;
import com.gauransh.gateway.shared.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing canonical REST monitoring endpoints under {@code /api/v1/monitoring/*}.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {

    private final SystemHealthIndicator healthIndicator;
    private final MetricsService metricsService;

    public MonitoringController(SystemHealthIndicator healthIndicator, MetricsService metricsService) {
        this.healthIndicator = healthIndicator;
        this.metricsService = metricsService;
    }

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        HealthResponse health = healthIndicator.checkHealth();

        if ("DOWN".equalsIgnoreCase(health.status())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error(
                            ApiResponse.ApiError.of("MON-004", "Health check failed: Subsystem status is DOWN", health),
                            requestId
                    ));
        }

        return ResponseEntity.ok(ApiResponse.success(health, requestId));
    }

    @GetMapping("/metrics")
    public ResponseEntity<ApiResponse<GatewayMetricsResponse>> getMetrics(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        GatewayMetricsResponse metrics = metricsService.getGatewayMetrics();
        return ResponseEntity.ok(ApiResponse.success(metrics, requestId));
    }

    @GetMapping("/jvm")
    public ResponseEntity<ApiResponse<JvmMetricsResponse>> getJvm(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        JvmMetricsResponse jvm = metricsService.getJvmMetrics();
        return ResponseEntity.ok(ApiResponse.success(jvm, requestId));
    }

    @GetMapping("/redis")
    public ResponseEntity<ApiResponse<RedisMetricsResponse>> getRedis(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        RedisMetricsResponse redis = metricsService.getRedisMetrics();
        return ResponseEntity.ok(ApiResponse.success(redis, requestId));
    }

    @GetMapping("/traffic")
    public ResponseEntity<ApiResponse<TrafficStatsResponse>> getTraffic(
            @RequestParam(value = "period", required = false, defaultValue = "1m") String period,
            HttpServletRequest request
    ) {
        String requestId = resolveRequestId(request);
        TrafficStatsResponse traffic = metricsService.getTrafficStats(period);
        return ResponseEntity.ok(ApiResponse.success(traffic, requestId));
    }

    @GetMapping("/ratelimiter")
    public ResponseEntity<ApiResponse<RateLimiterMetricsResponse>> getRateLimiter(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        RateLimiterMetricsResponse rateLimiter = metricsService.getRateLimiterMetrics();
        return ResponseEntity.ok(ApiResponse.success(rateLimiter, requestId));
    }

    private String resolveRequestId(HttpServletRequest request) {
        if (request != null) {
            String header = request.getHeader("X-Correlation-ID");
            if (header != null && !header.isBlank()) {
                return header;
            }
        }
        return UUID.randomUUID().toString();
    }
}
