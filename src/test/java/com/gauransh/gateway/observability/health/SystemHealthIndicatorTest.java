package com.gauransh.gateway.observability.health;

import com.gauransh.gateway.observability.model.HealthResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemHealthIndicatorTest {

    @Test
    @DisplayName("Reports overall UP when Redis ping succeeds and system resources are healthy")
    void reportsUpWhenAllSubsystemsHealthy() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);

        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        SystemHealthIndicator indicator = new SystemHealthIndicator(factory);
        HealthResponse response = indicator.checkHealth();

        assertThat(response.status()).isEqualTo("UP");
        assertThat(response.gateway()).isEqualTo("UP");
        assertThat(response.redis()).isEqualTo("UP");
        assertThat(response.disk()).isEqualTo("UP");
        assertThat(response.memory()).isEqualTo("UP");
    }

    @Test
    @DisplayName("Reports DOWN when Redis connection fails")
    void reportsDownWhenRedisFails() {
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new RuntimeException("Redis unreachable"));

        SystemHealthIndicator indicator = new SystemHealthIndicator(factory);
        HealthResponse response = indicator.checkHealth();

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.gateway()).isEqualTo("UP");
        assertThat(response.redis()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("Reports DOWN when RedisConnectionFactory is null")
    void reportsDownWhenRedisFactoryNull() {
        SystemHealthIndicator indicator = new SystemHealthIndicator(null);
        HealthResponse response = indicator.checkHealth();

        assertThat(response.status()).isEqualTo("DOWN");
        assertThat(response.redis()).isEqualTo("DOWN");
    }
}
