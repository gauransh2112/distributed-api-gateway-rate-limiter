package com.gauransh.gateway.observability.metrics;

import com.gauransh.gateway.observability.model.GatewayMetricsResponse;
import com.gauransh.gateway.observability.model.JvmMetricsResponse;
import com.gauransh.gateway.observability.model.RateLimiterMetricsResponse;
import com.gauransh.gateway.observability.model.RedisMetricsResponse;
import com.gauransh.gateway.observability.model.TrafficStatsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisServerCommands;

import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsServiceTest {

    private RedisConnectionFactory redisConnectionFactory;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        redisConnectionFactory = mock(RedisConnectionFactory.class);
        metricsService = new MetricsService(redisConnectionFactory);
    }

    @Test
    @DisplayName("Records HTTP requests and computes correct rates and averages")
    void recordsHttpRequests() {
        metricsService.recordHttpRequest(200, Duration.ofMillis(10));
        metricsService.recordHttpRequest(200, Duration.ofMillis(20));
        metricsService.recordHttpRequest(500, Duration.ofMillis(30));

        GatewayMetricsResponse metrics = metricsService.getGatewayMetrics();

        assertThat(metrics.requestsTotal()).isEqualTo(3);
        assertThat(metrics.successRate()).isEqualTo(66.67);
        assertThat(metrics.errorRate()).isEqualTo(33.33);
        assertThat(metrics.averageLatencyMs()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("Returns valid JVM runtime telemetry")
    void retrievesJvmMetrics() {
        JvmMetricsResponse jvm = metricsService.getJvmMetrics();

        assertThat(jvm.heapUsedMb()).isGreaterThan(0);
        assertThat(jvm.heapMaxMb()).isGreaterThan(0);
        assertThat(jvm.threads()).isGreaterThan(0);
        assertThat(jvm.uptimeSeconds()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Retrieves Redis metrics when Redis is healthy")
    void retrievesRedisMetricsWhenUp() {
        RedisConnection connection = mock(RedisConnection.class);
        RedisServerCommands serverCommands = mock(RedisServerCommands.class);

        when(redisConnectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        when(connection.serverCommands()).thenReturn(serverCommands);

        Properties props = new Properties();
        props.setProperty("connected_clients", "5");
        when(serverCommands.info("clients")).thenReturn(props);

        RedisMetricsResponse redis = metricsService.getRedisMetrics();

        assertThat(redis.status()).isEqualTo("UP");
        assertThat(redis.connections()).isEqualTo(5);
    }

    @Test
    @DisplayName("Returns DOWN when Redis ping throws an exception")
    void retrievesRedisMetricsWhenDown() {
        when(redisConnectionFactory.getConnection()).thenThrow(new RuntimeException("Connection refused"));

        RedisMetricsResponse redis = metricsService.getRedisMetrics();

        assertThat(redis.status()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("Aggregates traffic stats across requested periods")
    void aggregatesTrafficStats() {
        metricsService.recordHttpRequest(200, Duration.ofMillis(15));

        TrafficStatsResponse traffic1m = metricsService.getTrafficStats("1m");
        TrafficStatsResponse traffic5m = metricsService.getTrafficStats("5m");

        assertThat(traffic1m.requests()).isEqualTo(1);
        assertThat(traffic1m.successful()).isEqualTo(1);
        assertThat(traffic1m.averageLatencyMs()).isEqualTo(15.0);

        assertThat(traffic5m.requests()).isEqualTo(1);
    }

    @Test
    @DisplayName("Maintains rate limiter telemetry and algorithm breakdown")
    void maintainsRateLimiterTelemetry() {
        metricsService.recordRateLimitDecision("TOKEN_BUCKET", true, Duration.ofMillis(1));
        metricsService.recordRateLimitDecision("TOKEN_BUCKET", false, Duration.ofMillis(2));
        metricsService.recordRateLimitFailure("TOKEN_BUCKET", Duration.ofMillis(3));

        RateLimiterMetricsResponse rl = metricsService.getRateLimiterMetrics();

        assertThat(rl.allowedRequests()).isEqualTo(1);
        assertThat(rl.blockedRequests()).isEqualTo(1);
        assertThat(rl.algorithm()).isEqualTo("TOKEN_BUCKET");
        assertThat(rl.averageDecisionLatencyMs()).isEqualTo(2.0);

        assertThat(metricsService.getAlgorithmBreakdown().get("TOKEN_BUCKET").failed.sum()).isEqualTo(1);
    }
}
