package com.gauransh.gateway.ratelimiter.metrics;

import com.gauransh.gateway.ratelimiter.config.RateLimitFailurePolicy;
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

    /**
     * Records that a rate limit evaluation could not be completed because the distributed state was
     * unavailable, and which failure policy was applied (ADR-0016).
     *
     * <p>No {@link RateLimitDecision} exists for such a request: no decision was reached. Declared
     * as a default no-op so existing publishers need no change; the signal is available to a future
     * Micrometer-backed implementation without this sprint building one.</p>
     *
     * @param context request context
     * @param policy the failure policy applied
     * @param cause the underlying storage failure
     * @param executionDuration duration taken before the failure surfaced
     */
    default void publishFailure(RateLimitContext context, RateLimitFailurePolicy policy, Throwable cause,
                                Duration executionDuration) {
        // No-op by default.
    }
}
