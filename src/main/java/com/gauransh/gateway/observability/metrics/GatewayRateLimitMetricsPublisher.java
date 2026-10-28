package com.gauransh.gateway.observability.metrics;

import com.gauransh.gateway.ratelimiter.config.RateLimitFailurePolicy;
import com.gauransh.gateway.ratelimiter.metrics.RateLimitMetricsPublisher;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Production implementation of {@link RateLimitMetricsPublisher} bridging rate limiter filter
 * decisions into the {@link MetricsService} telemetry foundation.
 *
 * <p>Execution is strictly non-blocking and isolated: exceptions are safely trapped so that
 * telemetry never compromises request admission or increases latency.</p>
 */
@Component
@Primary
public class GatewayRateLimitMetricsPublisher implements RateLimitMetricsPublisher {

    private static final Logger log = LoggerFactory.getLogger(GatewayRateLimitMetricsPublisher.class);

    private final MetricsService metricsService;
    private final RateLimiterProperties rateLimiterProperties;

    public GatewayRateLimitMetricsPublisher(
            MetricsService metricsService,
            @Autowired(required = false) RateLimiterProperties rateLimiterProperties
    ) {
        this.metricsService = metricsService;
        this.rateLimiterProperties = rateLimiterProperties;
    }

    @Override
    public void publishMetrics(RateLimitContext context, RateLimitDecision decision, Duration executionDuration) {
        try {
            String algorithm = resolveAlgorithm();
            boolean allowed = decision != null && decision.allowed();
            Duration duration = executionDuration != null ? executionDuration : Duration.ZERO;
            metricsService.recordRateLimitDecision(algorithm, allowed, duration);
        } catch (Exception e) {
            log.warn("Failed to publish rate limit metrics: {}", e.getMessage());
        }
    }

    @Override
    public void publishFailure(RateLimitContext context, RateLimitFailurePolicy policy, Throwable cause,
                               Duration executionDuration) {
        try {
            String algorithm = resolveAlgorithm();
            Duration duration = executionDuration != null ? executionDuration : Duration.ZERO;
            metricsService.recordRateLimitFailure(algorithm, duration);
        } catch (Exception e) {
            log.warn("Failed to publish rate limit failure: {}", e.getMessage());
        }
    }

    private String resolveAlgorithm() {
        if (rateLimiterProperties != null && rateLimiterProperties.getAlgorithm() != null) {
            return rateLimiterProperties.getAlgorithm().name();
        }
        return "TOKEN_BUCKET";
    }
}
