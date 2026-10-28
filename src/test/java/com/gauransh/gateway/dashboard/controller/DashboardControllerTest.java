package com.gauransh.gateway.dashboard.controller;

import com.gauransh.gateway.dashboard.model.DashboardOverviewResponse;
import com.gauransh.gateway.dashboard.model.LiveStatusResponse;
import com.gauransh.gateway.dashboard.model.RateLimiterDashboardResponse;
import com.gauransh.gateway.dashboard.model.SystemInfoResponse;
import com.gauransh.gateway.dashboard.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;


import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private DashboardService dashboardService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        DashboardController controller = new DashboardController(dashboardService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/overview returns HTTP 200 with aggregated overview DTO")
    void getOverview() throws Exception {
        when(dashboardService.getOverview()).thenReturn(
                new DashboardOverviewResponse("UP", "UP", 318.0, 99.91, 94, 17.3)
        );

        mockMvc.perform(get("/api/v1/dashboard/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gatewayStatus").value("UP"))
                .andExpect(jsonPath("$.data.redisStatus").value("UP"))
                .andExpect(jsonPath("$.data.requestsPerSecond").value(318.0))
                .andExpect(jsonPath("$.data.successRate").value(99.91))
                .andExpect(jsonPath("$.data.blockedRequests").value(94))
                .andExpect(jsonPath("$.data.averageLatency").value(17.3));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/live returns HTTP 200 with component statuses")
    void getLiveStatus() throws Exception {
        when(dashboardService.getLiveStatus()).thenReturn(
                new LiveStatusResponse("UP", "UP", "UP", "UP", "UP")
        );

        mockMvc.perform(get("/api/v1/dashboard/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.gateway").value("UP"))
                .andExpect(jsonPath("$.data.redis").value("UP"))
                .andExpect(jsonPath("$.data.authentication").value("UP"))
                .andExpect(jsonPath("$.data.routing").value("UP"))
                .andExpect(jsonPath("$.data.rateLimiter").value("UP"));
    }


    @Test
    @DisplayName("GET /api/v1/dashboard/ratelimiter returns HTTP 200 with rate limiter presentation metrics")
    void getRateLimiterDashboard() throws Exception {
        when(dashboardService.getRateLimiterDashboard()).thenReturn(
                new RateLimiterDashboardResponse("TOKEN_BUCKET", 84231, 312, 0.63, 0.72)
        );

        mockMvc.perform(get("/api/v1/dashboard/ratelimiter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.algorithm").value("TOKEN_BUCKET"))
                .andExpect(jsonPath("$.data.allowedRequests").value(84231))
                .andExpect(jsonPath("$.data.blockedRequests").value(312))
                .andExpect(jsonPath("$.data.averageDecisionLatency").value(0.63))
                .andExpect(jsonPath("$.data.redisLatency").value(0.72));
    }

    @Test
    @DisplayName("GET /api/v1/dashboard/system returns HTTP 200 with runtime system metadata")
    void getSystemInfo() throws Exception {
        when(dashboardService.getSystemInfo()).thenReturn(
                new SystemInfoResponse("Distributed API Gateway", "1.0.0-SNAPSHOT", "21", "3.3.2", "3d 12h")
        );

        mockMvc.perform(get("/api/v1/dashboard/system"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.application").value("Distributed API Gateway"))
                .andExpect(jsonPath("$.data.version").value("1.0.0-SNAPSHOT"))
                .andExpect(jsonPath("$.data.javaVersion").value("21"))
                .andExpect(jsonPath("$.data.springBootVersion").value("3.3.2"))
                .andExpect(jsonPath("$.data.uptime").value("3d 12h"));
    }
}
