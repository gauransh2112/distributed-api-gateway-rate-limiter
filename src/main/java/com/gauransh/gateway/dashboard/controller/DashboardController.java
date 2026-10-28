package com.gauransh.gateway.dashboard.controller;

import com.gauransh.gateway.dashboard.model.DashboardOverviewResponse;
import com.gauransh.gateway.dashboard.model.LiveStatusResponse;
import com.gauransh.gateway.dashboard.model.RateLimiterDashboardResponse;
import com.gauransh.gateway.dashboard.model.SystemInfoResponse;
import com.gauransh.gateway.dashboard.service.DashboardService;
import com.gauransh.gateway.shared.model.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing aggregated dashboard endpoints under {@code /api/v1/dashboard/*}.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<DashboardOverviewResponse>> getOverview(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        DashboardOverviewResponse overview = dashboardService.getOverview();
        return ResponseEntity.ok(ApiResponse.success(overview, requestId));
    }

    @GetMapping("/live")
    public ResponseEntity<ApiResponse<LiveStatusResponse>> getLiveStatus(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        LiveStatusResponse liveStatus = dashboardService.getLiveStatus();
        return ResponseEntity.ok(ApiResponse.success(liveStatus, requestId));
    }


    @GetMapping("/ratelimiter")
    public ResponseEntity<ApiResponse<RateLimiterDashboardResponse>> getRateLimiter(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        RateLimiterDashboardResponse rateLimiter = dashboardService.getRateLimiterDashboard();
        return ResponseEntity.ok(ApiResponse.success(rateLimiter, requestId));
    }

    @GetMapping("/system")
    public ResponseEntity<ApiResponse<SystemInfoResponse>> getSystemInfo(HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        SystemInfoResponse systemInfo = dashboardService.getSystemInfo();
        return ResponseEntity.ok(ApiResponse.success(systemInfo, requestId));
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
