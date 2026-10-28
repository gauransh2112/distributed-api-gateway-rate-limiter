package com.gauransh.gateway.ratelimiter.metrics;

import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Default pass-through fallback implementation of {@link RateLimitMetricsPublisher}.
 */
@Component
@ConditionalOnMissingBean(RateLimitMetricsPublisher.class)
public class NoOpRateLimitMetricsPublisher implements RateLimitMetricsPublisher {

    @Override
    public void publishMetrics(RateLimitContext context, RateLimitDecision decision, Duration executionDuration) {
        // No-op fallback
    }
}
