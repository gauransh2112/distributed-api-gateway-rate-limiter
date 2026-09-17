package com.gauransh.gateway.redis.service;

import com.gauransh.gateway.redis.exception.RedisStorageException;
import com.gauransh.gateway.redis.script.LuaExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultRedisServiceUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private LuaExecutor luaExecutor;

    private DefaultRedisService redisService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        redisService = new DefaultRedisService(redisTemplate, luaExecutor);
    }

    @Test
    @DisplayName("Should delegate set operation to StringRedisTemplate")
    void testSet() {
        redisService.set("dev:ratelimiter:user:1", "100");
        verify(valueOperations).set("dev:ratelimiter:user:1", "100");
    }

    @Test
    @DisplayName("Should delegate setWithTtl to StringRedisTemplate")
    void testSetWithTtl() {
        Duration ttl = Duration.ofSeconds(60);
        redisService.setWithTtl("dev:ratelimiter:user:1", "100", ttl);
        verify(valueOperations).set("dev:ratelimiter:user:1", "100", ttl);
    }

    @Test
    @DisplayName("Should delegate setIfAbsent and setIfAbsentWithTtl returning Redis result")
    void testSetIfAbsent() {
        when(valueOperations.setIfAbsent("dev:lock:key", "val")).thenReturn(true);
        Boolean set1 = redisService.setIfAbsent("dev:lock:key", "val");
        assertThat(set1).isTrue();

        Duration ttl = Duration.ofSeconds(30);
        when(valueOperations.setIfAbsent("dev:lock:key", "val", ttl)).thenReturn(false);
        Boolean set2 = redisService.setIfAbsentWithTtl("dev:lock:key", "val", ttl);
        assertThat(set2).isFalse();
        verify(valueOperations).setIfAbsent("dev:lock:key", "val", ttl);
    }

    @Test
    @DisplayName("Should delegate getAndSet returning empty Optional when Redis returns null")
    void testGetAndSet() {
        when(valueOperations.getAndSet("dev:counter:key", "new-val")).thenReturn("old-val");
        Optional<String> oldVal = redisService.getAndSet("dev:counter:key", "new-val");
        assertThat(oldVal).contains("old-val");

        when(valueOperations.getAndSet("dev:counter:key2", "val")).thenReturn(null);
        Optional<String> emptyVal = redisService.getAndSet("dev:counter:key2", "val");
        assertThat(emptyVal).isEmpty();
    }

    @Test
    @DisplayName("Should retrieve Optional value on get")
    void testGet() {
        when(valueOperations.get("dev:ratelimiter:user:1")).thenReturn("100");
        Optional<String> result = redisService.get("dev:ratelimiter:user:1");

        assertThat(result).contains("100");
    }

    @Test
    @DisplayName("Should return empty Optional when key does not exist")
    void testGetNonExistent() {
        when(valueOperations.get("dev:ratelimiter:user:missing")).thenReturn(null);
        Optional<String> result = redisService.get("dev:ratelimiter:user:missing");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Should delegate increment, incrementBy, decrement, and decrementBy")
    void testAtomicNumericOperations() {
        when(valueOperations.increment("dev:counter:requests:1")).thenReturn(1L);
        assertThat(redisService.increment("dev:counter:requests:1")).isEqualTo(1L);

        when(valueOperations.increment("dev:counter:requests:1", 5L)).thenReturn(6L);
        assertThat(redisService.incrementBy("dev:counter:requests:1", 5L)).isEqualTo(6L);

        when(valueOperations.decrement("dev:counter:requests:1")).thenReturn(5L);
        assertThat(redisService.decrement("dev:counter:requests:1")).isEqualTo(5L);

        when(valueOperations.decrement("dev:counter:requests:1", 2L)).thenReturn(3L);
        assertThat(redisService.decrementBy("dev:counter:requests:1", 2L)).isEqualTo(3L);
    }

    @Test
    @DisplayName("Should delegate expire, expireAt for valid future timestamp, and persist")
    void testExpireDelegation() {
        Duration ttl = Duration.ofSeconds(60);
        when(redisTemplate.expire("dev:ttl:key", ttl)).thenReturn(true);
        assertThat(redisService.expire("dev:ttl:key", ttl)).isTrue();
        verify(redisTemplate).expire("dev:ttl:key", ttl);

        Instant futureInstant = Instant.now().plusSeconds(300);
        when(redisTemplate.expireAt("dev:ttl:key", futureInstant)).thenReturn(true);
        assertThat(redisService.expireAt("dev:ttl:key", futureInstant)).isTrue();
        verify(redisTemplate).expireAt("dev:ttl:key", futureInstant);

        when(redisTemplate.persist("dev:ttl:key")).thenReturn(true);
        assertThat(redisService.persist("dev:ttl:key")).isTrue();
        verify(redisTemplate).persist("dev:ttl:key");
    }

    @Test
    @DisplayName("Should return empty Optional getTtl for -1 persistent key and -2 missing key")
    void testGetTtlEdgeCases() {
        when(redisTemplate.getExpire("dev:persistent:key")).thenReturn(-1L);
        assertThat(redisService.getTtl("dev:persistent:key")).isEmpty();

        when(redisTemplate.getExpire("dev:missing:key")).thenReturn(-2L);
        assertThat(redisService.getTtl("dev:missing:key")).isEmpty();

        when(redisTemplate.getExpire("dev:ttl:key")).thenReturn(45L);
        assertThat(redisService.getTtl("dev:ttl:key")).contains(Duration.ofSeconds(45));
    }

    @Test
    @DisplayName("Should delegate hashIncrement and hashSetIfAbsent")
    void testHashAtomicOperations() {
        when(hashOperations.putIfAbsent("dev:hash:key", "field1", "val1")).thenReturn(true);
        assertThat(redisService.hashSetIfAbsent("dev:hash:key", "field1", "val1")).isTrue();

        when(hashOperations.increment("dev:hash:key", "tokens", 5L)).thenReturn(50L);
        assertThat(redisService.hashIncrement("dev:hash:key", "tokens", 5L)).isEqualTo(50L);
    }

    @Test
    @DisplayName("Should validate parameters and throw IllegalArgumentException BEFORE invoking Redis")
    void testInvalidParameterValidations() {
        assertThatThrownBy(() -> redisService.set(null, "val"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> redisService.setWithTtl("dev:key", "val", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> redisService.expire("dev:key", Duration.ofSeconds(-10)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> redisService.expireAt("dev:key", null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> redisService.expireAt("dev:key", Instant.now().minusSeconds(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ExpireAt instant must not be in the past");

        assertThatThrownBy(() -> redisService.hashSet("dev:key", "  ", "val"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(valueOperations);
        verifyNoInteractions(hashOperations);
    }

    @Test
    @DisplayName("Should wrap Spring Data Redis exception into RedisStorageException")
    void testExceptionTranslation() {
        when(valueOperations.get("dev:error:key:1"))
                .thenThrow(new QueryTimeoutException("Redis command timed out"));

        assertThatThrownBy(() -> redisService.get("dev:error:key:1"))
                .isInstanceOf(RedisStorageException.class)
                .hasMessageContaining("Redis operation [GET] failed for key [dev:error:key:1]")
                .hasCauseInstanceOf(QueryTimeoutException.class);
    }

    @Test
    @DisplayName("Should delegate executeLua to the LuaExecutor without altering arguments")
    void testExecuteLuaDelegation() {
        List<String> keys = List.of("dev:ratelimiter:user:1");
        List<String> args = List.of("1", "60");
        when(luaExecutor.executeLua("increment.lua", Long.class, keys, args)).thenReturn(7L);

        Long result = redisService.executeLua("increment.lua", Long.class, keys, args);

        assertThat(result).isEqualTo(7L);
        verify(luaExecutor).executeLua("increment.lua", Long.class, keys, args);
        verifyNoInteractions(valueOperations);
    }
}
