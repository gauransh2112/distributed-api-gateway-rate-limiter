package com.gauransh.gateway.observability.metrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GatewayTrafficMetricsFilterTest {

    private MetricsService metricsService;
    private GatewayTrafficMetricsFilter filter;

    @BeforeEach
    void setUp() {
        metricsService = mock(MetricsService.class);
        filter = new GatewayTrafficMetricsFilter(metricsService);
    }

    @Test
    @DisplayName("Inbound application request records HTTP metrics upon completion")
    void recordsHttpMetricsForApplicationTraffic() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/123");
        request.setRequestURI("/api/v1/users/123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        verify(metricsService).recordHttpRequest(eq(200), any(Duration.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/health",
            "/health/",
            "/health/readiness",
            "/ready",
            "/ready/",
            "/ready/live",
            "/actuator",
            "/actuator/health",
            "/actuator/health/readiness",
            "/api/v1/monitoring/health",
            "/api/v1/monitoring/metrics",
            "/api/v1/monitoring/jvm",
            "/api/v1/monitoring/redis",
            "/api/v1/monitoring/traffic",
            "/api/v1/monitoring/ratelimiter",
            "/api/v1/dashboard/overview",
            "/api/v1/dashboard/live",
            "/api/v1/dashboard/ratelimiter",
            "/api/v1/dashboard/system"
    })
    @DisplayName("Operational, monitoring, and dashboard paths are excluded from gateway traffic counters")
    void operationalAndMonitoringPathsAreExempted(String uri) throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        verify(metricsService, never()).recordHttpRequest(any(Integer.class), any(Duration.class));
    }

    @Test
    @DisplayName("Filter executes chain safely when MetricsService is absent")
    void executesSafelyWithoutMetricsService() throws ServletException, IOException {
        GatewayTrafficMetricsFilter nullServiceFilter = new GatewayTrafficMetricsFilter(null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.setRequestURI("/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(201);

        boolean[] chainExecuted = {false};
        FilterChain chain = (req, res) -> chainExecuted[0] = true;

        nullServiceFilter.doFilter(request, response, chain);

        assertThat(chainExecuted[0]).isTrue();
    }
}
