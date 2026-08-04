package com.gateway.ratelimiter.metrics;

import com.gateway.ratelimiter.model.RateLimitContext;
import com.gateway.ratelimiter.model.RateLimitDecision;
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
