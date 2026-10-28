package com.gauransh.gateway.observability.controller;

import com.gauransh.gateway.observability.health.SystemHealthIndicator;
import com.gauransh.gateway.observability.metrics.MetricsService;
import com.gauransh.gateway.observability.model.GatewayMetricsResponse;
import com.gauransh.gateway.observability.model.HealthResponse;
import com.gauransh.gateway.observability.model.JvmMetricsResponse;
import com.gauransh.gateway.observability.model.RateLimiterMetricsResponse;
import com.gauransh.gateway.observability.model.RedisMetricsResponse;
import com.gauransh.gateway.observability.model.TrafficStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MonitoringControllerTest {

    private SystemHealthIndicator healthIndicator;
    private MetricsService metricsService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        healthIndicator = mock(SystemHealthIndicator.class);
        metricsService = mock(MetricsService.class);
        MonitoringController controller = new MonitoringController(healthIndicator, metricsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/monitoring/health returns HTTP 200 and standard envelope when healthy")
    void getHealthUp() throws Exception {
        when(healthIndicator.checkHealth()).thenReturn(new HealthResponse("UP", "UP", "UP", "UP", "UP"));

        mockMvc.perform(get("/api/v1/monitoring/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.gateway").value("UP"))
                .andExpect(jsonPath("$.data.redis").value("UP"))
                .andExpect(jsonPath("$.data.disk").value("UP"))
                .andExpect(jsonPath("$.data.memory").value("UP"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("GET /api/v1/monitoring/health returns HTTP 503 and MON-004 error when DOWN")
    void getHealthDown() throws Exception {
        when(healthIndicator.checkHealth()).thenReturn(new HealthResponse("DOWN", "UP", "DOWN", "UP", "UP"));

        mockMvc.perform(get("/api/v1/monitoring/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("MON-004"))
                .andExpect(jsonPath("$.error.details.status").value("DOWN"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("GET /api/v1/monitoring/metrics returns HTTP 200 with runtime metrics")
    void getMetrics() throws Exception {
        when(metricsService.getGatewayMetrics()).thenReturn(
                new GatewayMetricsResponse(125043, 352.0, 99.84, 0.16, 14.8)
        );

        mockMvc.perform(get("/api/v1/monitoring/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestsTotal").value(125043))
                .andExpect(jsonPath("$.data.requestsPerSecond").value(352.0))
                .andExpect(jsonPath("$.data.successRate").value(99.84))
                .andExpect(jsonPath("$.data.errorRate").value(0.16))
                .andExpect(jsonPath("$.data.averageLatencyMs").value(14.8));
    }

    @Test
    @DisplayName("GET /api/v1/monitoring/jvm returns HTTP 200 with JVM statistics")
    void getJvm() throws Exception {
        when(metricsService.getJvmMetrics()).thenReturn(
                new JvmMetricsResponse(421, 1024, 18.4, 62, 48231)
        );

        mockMvc.perform(get("/api/v1/monitoring/jvm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.heapUsedMb").value(421))
                .andExpect(jsonPath("$.data.heapMaxMb").value(1024))
                .andExpect(jsonPath("$.data.cpuUsage").value(18.4))
                .andExpect(jsonPath("$.data.threads").value(62))
                .andExpect(jsonPath("$.data.uptimeSeconds").value(48231));
    }

    @Test
    @DisplayName("GET /api/v1/monitoring/redis returns HTTP 200 with Redis telemetry")
    void getRedis() throws Exception {
        when(metricsService.getRedisMetrics()).thenReturn(
                new RedisMetricsResponse("UP", 0.72, 12, 842731, 11842)
        );

        mockMvc.perform(get("/api/v1/monitoring/redis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.averageLatencyMs").value(0.72))
                .andExpect(jsonPath("$.data.connections").value(12))
                .andExpect(jsonPath("$.data.commandsExecuted").value(842731))
                .andExpect(jsonPath("$.data.luaExecutions").value(11842));
    }

    @Test
    @DisplayName("GET /api/v1/monitoring/traffic returns HTTP 200 with period stats")
    void getTraffic() throws Exception {
        when(metricsService.getTrafficStats(anyString())).thenReturn(
                new TrafficStatsResponse(12431, 12398, 33, 18.7)
        );

        mockMvc.perform(get("/api/v1/monitoring/traffic").param("period", "5m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requests").value(12431))
                .andExpect(jsonPath("$.data.successful").value(12398))
                .andExpect(jsonPath("$.data.failed").value(33))
                .andExpect(jsonPath("$.data.averageLatencyMs").value(18.7));
    }

    @Test
    @DisplayName("GET /api/v1/monitoring/ratelimiter returns HTTP 200 with rate limiter stats")
    void getRateLimiter() throws Exception {
        when(metricsService.getRateLimiterMetrics()).thenReturn(
                new RateLimiterMetricsResponse(842193, 841, "TOKEN_BUCKET", 0.48)
        );

        mockMvc.perform(get("/api/v1/monitoring/ratelimiter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.allowedRequests").value(842193))
                .andExpect(jsonPath("$.data.blockedRequests").value(841))
                .andExpect(jsonPath("$.data.algorithm").value("TOKEN_BUCKET"))
                .andExpect(jsonPath("$.data.averageDecisionLatencyMs").value(0.48));
    }
}
