package com.gauransh.gateway.observability.metrics;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Early-stage HTTP filter recording inbound gateway request throughput, response statuses,
 * and end-to-end execution latencies into {@link MetricsService}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class GatewayTrafficMetricsFilter extends OncePerRequestFilter {

    private final MetricsService metricsService;

    public GatewayTrafficMetricsFilter(@Autowired(required = false) MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator")
                || uri.equals("/health")
                || uri.startsWith("/health/")
                || uri.equals("/ready")
                || uri.startsWith("/ready/")
                || uri.startsWith("/api/v1/monitoring")
                || uri.startsWith("/api/v1/dashboard");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        long startNs = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (metricsService != null) {
                long durationNs = System.nanoTime() - startNs;
                metricsService.recordHttpRequest(response.getStatus(), Duration.ofNanos(durationNs));
            }
        }
    }
}
