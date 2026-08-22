package com.gauransh.gateway.ratelimiter.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThat;

import com.gauransh.gateway.bootstrap.GatewayApplication;

/**
 * Infrastructure verification test for Spring Data Redis connectivity and Actuator health.
 *
 * <p>Executes only when a local Redis container is reachable on port 6379, ensuring
 * that standard unit tests remain 100% independent of Redis.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
class RedisInfrastructureTest {

    @Autowired(required = false)
    private RedisConnectionFactory connectionFactory;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private HealthEndpoint healthEndpoint;

    static boolean isRedisAvailable() {
        try (Socket socket = new Socket("localhost", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    @Test
    @DisplayName("Verify Spring Data Redis Connection Factory Bean initialization")
    void testRedisConnectionFactoryPresent() {
        assertThat(connectionFactory).isNotNull();
    }

    @Test
    @DisplayName("Verify StringRedisTemplate execution with PING command")
    void testRedisPingPong() {
        assertThat(redisTemplate).isNotNull();
        String pong = redisTemplate.getConnectionFactory().getConnection().ping();
        assertThat(pong).isEqualToIgnoringCase("PONG");
    }

    @Test
    @DisplayName("Verify Spring Boot Actuator Redis Health Endpoint status UP")
    void testActuatorRedisHealthIndicator() {
        assertThat(connectionFactory).isNotNull();
        assertThat(healthEndpoint).isNotNull();
        HealthComponent redisHealth = healthEndpoint.healthForPath("redis");
        assertThat(redisHealth).isNotNull();
        assertThat(redisHealth.getStatus().getCode()).isEqualTo("UP");
    }
}
