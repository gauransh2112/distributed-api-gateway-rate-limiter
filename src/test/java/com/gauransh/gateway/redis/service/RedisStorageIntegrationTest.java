package com.gauransh.gateway.redis.service;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live integration test for {@link RedisService} storage primitives.
 *
 * <p>Executes against the active Redis container (port 6379) when available.
 * Uses {@link EnabledIf} so that offline Maven unit test execution remains 100% independent of Redis.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
class RedisStorageIntegrationTest {

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    private String testKey;

    static boolean isRedisAvailable() {
        try (Socket socket = new Socket("localhost", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    @AfterEach
    void tearDown() {
        if (testKey != null) {
            redisService.delete(testKey);
        }
    }

    @Test
    @DisplayName("Verify string SET, GET, EXISTS, and DELETE primitives against live Redis")
    void testBasicSetGetExistsDelete() {
        testKey = keyBuilder.buildKey("ratelimiter", "integration", UUID.randomUUID().toString());

        redisService.set(testKey, "hello-redis");
        assertThat(redisService.exists(testKey)).isTrue();

        Optional<String> val = redisService.get(testKey);
        assertThat(val).contains("hello-redis");

        Boolean deleted = redisService.delete(testKey);
        assertThat(deleted).isTrue();
        assertThat(redisService.exists(testKey)).isFalse();
    }

    @Test
    @DisplayName("Verify SET with TTL and EXPIRE primitives against live Redis")
    void testSetWithTtlAndExpire() {
        testKey = keyBuilder.buildKey("ratelimiter", "ttl", UUID.randomUUID().toString());

        redisService.setWithTtl(testKey, "ttl-val", Duration.ofSeconds(10));
        assertThat(redisService.exists(testKey)).isTrue();

        Boolean expired = redisService.expire(testKey, Duration.ofSeconds(5));
        assertThat(expired).isTrue();
    }

    @Test
    @DisplayName("Verify atomic INCREMENT, INCREMENT_BY, and DECREMENT primitives against live Redis")
    void testCounters() {
        testKey = keyBuilder.buildKey("ratelimiter", "counter", UUID.randomUUID().toString());

        Long v1 = redisService.increment(testKey);
        assertThat(v1).isEqualTo(1L);

        Long v2 = redisService.incrementBy(testKey, 5L);
        assertThat(v2).isEqualTo(6L);

        Long v3 = redisService.decrement(testKey);
        assertThat(v3).isEqualTo(5L);
    }

    @Test
    @DisplayName("Verify HASH SET, GET, and DELETE primitives against live Redis")
    void testHashOperations() {
        testKey = keyBuilder.buildKey("ratelimiter", "hash", UUID.randomUUID().toString());

        redisService.hashSet(testKey, "tokens", "50");
        redisService.hashSet(testKey, "lastRefill", "1722587600");

        Optional<String> tokens = redisService.hashGet(testKey, "tokens");
        assertThat(tokens).contains("50");

        Boolean deleted = redisService.hashDelete(testKey, "tokens");
        assertThat(deleted).isTrue();
        assertThat(redisService.hashGet(testKey, "tokens")).isEmpty();
    }
}
