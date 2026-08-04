package com.gauransh.gateway.ratelimiter.metrics;

import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;

import java.time.Duration;

/**
 * Extension contract for recording operational metrics and telemetry for rate limiting.
 *
 * <p>Enables future integration with Micrometer, Prometheus, or Grafana dashboards
 * without altering core filter execution logic.</p>
 */
public interface RateLimitMetricsPublisher {

    /**
     * Records telemetry data for a rate limit evaluation.
     *
     * @param context request context
     * @param decision decision result
     * @param executionDuration duration taken to evaluate rate limit check
     */
    void publishMetrics(RateLimitContext context, RateLimitDecision decision, Duration executionDuration);
}
