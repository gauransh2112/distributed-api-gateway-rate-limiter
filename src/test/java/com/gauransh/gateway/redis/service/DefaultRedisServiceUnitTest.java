package com.gauransh.gateway.redis.service;

import com.gauransh.gateway.redis.exception.RedisStorageException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DefaultRedisServiceUnitTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private DefaultRedisService redisService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        redisService = new DefaultRedisService(redisTemplate);
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
    @DisplayName("Should delegate increment to StringRedisTemplate")
    void testIncrement() {
        when(valueOperations.increment("dev:counter:requests:1")).thenReturn(1L);
        Long count = redisService.increment("dev:counter:requests:1");

        assertThat(count).isEqualTo(1L);
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
}
