package com.gauransh.gateway.observability.metrics;

import com.gauransh.gateway.ratelimiter.config.RateLimitFailurePolicy;
import com.gauransh.gateway.ratelimiter.config.RateLimiterAlgorithm;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

class GatewayRateLimitMetricsPublisherTest {

    private MetricsService metricsService;
    private RateLimiterProperties properties;
    private GatewayRateLimitMetricsPublisher publisher;

    @BeforeEach
    void setUp() {
        metricsService = new MetricsService(null);
        properties = new RateLimiterProperties();
        properties.setAlgorithm(RateLimiterAlgorithm.TOKEN_BUCKET);
        publisher = new GatewayRateLimitMetricsPublisher(metricsService, properties);
    }

    @Test
    @DisplayName("Publishes allowed decision and records counters and decision duration")
    void publishesAllowedDecision() {
        RateLimitContext context = new RateLimitContext(
                "client-1", "/api/v1/test", "GET", Instant.now(), "127.0.0.1", Collections.emptyMap()
        );
        RateLimitDecision decision = RateLimitDecision.allowed(100L, 99L, Instant.now().plusSeconds(60), "OK");

        publisher.publishMetrics(context, decision, Duration.ofMillis(5));

        assertThat(metricsService.getRateLimiterMetrics().allowedRequests()).isEqualTo(1);
        assertThat(metricsService.getRateLimiterMetrics().blockedRequests()).isZero();
        assertThat(metricsService.getRateLimiterMetrics().averageDecisionLatencyMs()).isEqualTo(5.0);
        assertThat(metricsService.getRateLimiterMetrics().algorithm()).isEqualTo("TOKEN_BUCKET");
    }

    @Test
    @DisplayName("Publishes blocked decision and records blocked request counter")
    void publishesBlockedDecision() {
        RateLimitContext context = new RateLimitContext(
                "client-1", "/api/v1/test", "GET", Instant.now(), "127.0.0.1", Collections.emptyMap()
        );
        RateLimitDecision decision = RateLimitDecision.rejected(100L, Instant.now().plusSeconds(10), Duration.ofSeconds(10), "Quota exceeded");

        publisher.publishMetrics(context, decision, Duration.ofMillis(2));

        assertThat(metricsService.getRateLimiterMetrics().allowedRequests()).isZero();
        assertThat(metricsService.getRateLimiterMetrics().blockedRequests()).isEqualTo(1);
    }

    @Test
    @DisplayName("Publishes failure event and records failure metric without throwing")
    void publishesFailureEvent() {
        RateLimitContext context = new RateLimitContext(
                "client-1", "/api/v1/test", "GET", Instant.now(), "127.0.0.1", Collections.emptyMap()
        );

        publisher.publishFailure(context, RateLimitFailurePolicy.FAIL_CLOSED, new RuntimeException("Redis unreachable"), Duration.ofMillis(3));

        assertThat(metricsService.getAlgorithmBreakdown()).containsKey("TOKEN_BUCKET");
        assertThat(metricsService.getAlgorithmBreakdown().get("TOKEN_BUCKET").failed.sum()).isEqualTo(1);
    }

    @Test
    @DisplayName("Publisher isolates unexpected exceptions and never propagates them to caller")
    void isolatesExceptionsSafely() {
        GatewayRateLimitMetricsPublisher faultyPublisher = new GatewayRateLimitMetricsPublisher(null, null);

        assertThatCode(() -> faultyPublisher.publishMetrics(null, null, Duration.ofMillis(1)))
                .doesNotThrowAnyException();

        assertThatCode(() -> faultyPublisher.publishFailure(null, null, null, Duration.ofMillis(1)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Publisher does not catch fatal JVM Error (e.g. OutOfMemoryError)")
    void doesNotSwallowFatalErrors() {
        MetricsService errorService = mock(MetricsService.class);
        org.mockito.Mockito.doThrow(new OutOfMemoryError("Simulated OOM"))
                .when(errorService).recordRateLimitDecision(any(), org.mockito.ArgumentMatchers.anyBoolean(), any());

        GatewayRateLimitMetricsPublisher pub = new GatewayRateLimitMetricsPublisher(errorService, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> pub.publishMetrics(null, null, Duration.ZERO))
                .isInstanceOf(OutOfMemoryError.class);
    }
}
