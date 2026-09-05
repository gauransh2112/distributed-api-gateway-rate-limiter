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
import java.time.Instant;
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
    @DisplayName("Verify SET_IF_ABSENT, SET_IF_ABSENT_WITH_TTL, and GET_AND_SET against live Redis")
    void testAtomicSetPrimitives() {
        testKey = keyBuilder.buildKey("ratelimiter", "atomic", UUID.randomUUID().toString());

        Boolean set1 = redisService.setIfAbsent(testKey, "first");
        assertThat(set1).isTrue();

        Boolean set2 = redisService.setIfAbsent(testKey, "second");
        assertThat(set2).isFalse();

        Optional<String> oldVal = redisService.getAndSet(testKey, "third");
        assertThat(oldVal).contains("first");
        assertThat(redisService.get(testKey)).contains("third");
    }

    @Test
    @DisplayName("Verify SET_IF_ABSENT_WITH_TTL creates key with TTL and rejects overwrite of existing key")
    void testSetIfAbsentWithTtl() {
        testKey = keyBuilder.buildKey("ratelimiter", "nx-ttl", UUID.randomUUID().toString());

        Boolean created = redisService.setIfAbsentWithTtl(testKey, "val1", Duration.ofSeconds(60));
        assertThat(created).isTrue();
        assertThat(redisService.get(testKey)).contains("val1");

        Optional<Duration> ttl = redisService.getTtl(testKey);
        assertThat(ttl).isPresent();
        assertThat(ttl.get().getSeconds()).isGreaterThan(0L);

        Boolean overwritten = redisService.setIfAbsentWithTtl(testKey, "val2", Duration.ofSeconds(60));
        assertThat(overwritten).isFalse();
        assertThat(redisService.get(testKey)).contains("val1");
    }

    @Test
    @DisplayName("Verify SET with TTL, GET_TTL, EXPIRE_AT, PERSIST, and missing-key behavior against live Redis")
    void testTtlAndExpirePrimitives() {
        testKey = keyBuilder.buildKey("ratelimiter", "ttl", UUID.randomUUID().toString());

        redisService.setWithTtl(testKey, "ttl-val", Duration.ofSeconds(60));
        assertThat(redisService.exists(testKey)).isTrue();

        Optional<Duration> ttl = redisService.getTtl(testKey);
        assertThat(ttl).isPresent();
        assertThat(ttl.get().getSeconds()).isGreaterThan(0L);

        Instant expireAt = Instant.now().plusSeconds(120);
        Boolean expiredAt = redisService.expireAt(testKey, expireAt);
        assertThat(expiredAt).isTrue();

        Boolean persisted = redisService.persist(testKey);
        assertThat(persisted).isTrue();
        assertThat(redisService.getTtl(testKey)).isEmpty(); // Persistent key returns empty TTL

        // Missing key behavior
        String missingKey = keyBuilder.buildKey("ratelimiter", "missing", UUID.randomUUID().toString());
        assertThat(redisService.getTtl(missingKey)).isEmpty();
        assertThat(redisService.expire(missingKey, Duration.ofSeconds(30))).isFalse();
        assertThat(redisService.expireAt(missingKey, Instant.now().plusSeconds(30))).isFalse();
        assertThat(redisService.persist(missingKey)).isFalse();
    }

    @Test
    @DisplayName("Verify atomic INCREMENT, INCREMENT_BY, DECREMENT, and DECREMENT_BY primitives against live Redis")
    void testCounters() {
        testKey = keyBuilder.buildKey("ratelimiter", "counter", UUID.randomUUID().toString());

        Long v1 = redisService.increment(testKey);
        assertThat(v1).isEqualTo(1L);

        Long v2 = redisService.incrementBy(testKey, 5L);
        assertThat(v2).isEqualTo(6L);

        Long v3 = redisService.decrement(testKey);
        assertThat(v3).isEqualTo(5L);

        Long v4 = redisService.decrementBy(testKey, 2L);
        assertThat(v4).isEqualTo(3L);
    }

    @Test
    @DisplayName("Verify HASH SET, SET_IF_ABSENT, INCREMENT, GET, and DELETE primitives against live Redis")
    void testHashOperations() {
        testKey = keyBuilder.buildKey("ratelimiter", "hash", UUID.randomUUID().toString());

        redisService.hashSet(testKey, "tokens", "50");
        redisService.hashSet(testKey, "lastRefill", "1722587600");

        Boolean hashSetAbsent = redisService.hashSetIfAbsent(testKey, "tokens", "100");
        assertThat(hashSetAbsent).isFalse();

        Long updatedTokens = redisService.hashIncrement(testKey, "tokens", 10L);
        assertThat(updatedTokens).isEqualTo(60L);

        Optional<String> tokens = redisService.hashGet(testKey, "tokens");
        assertThat(tokens).contains("60");

        Boolean deleted = redisService.hashDelete(testKey, "tokens");
        assertThat(deleted).isTrue();
        assertThat(redisService.hashGet(testKey, "tokens")).isEmpty();
    }
}
