package com.gauransh.gateway.ratelimiter.metrics;

import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Default pass-through implementation of {@link RateLimitMetricsPublisher}.
 */
@Component
@Primary
public class NoOpRateLimitMetricsPublisher implements RateLimitMetricsPublisher {

    @Override
    public void publishMetrics(RateLimitContext context, RateLimitDecision decision, Duration executionDuration) {
        // No-op fallback
    }
}
