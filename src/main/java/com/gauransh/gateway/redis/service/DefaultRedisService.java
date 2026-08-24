package com.gauransh.gateway.redis.service;

import com.gauransh.gateway.redis.exception.RedisStorageException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Production-grade implementation of {@link RedisService} backed by Spring Data Redis's {@link StringRedisTemplate}.
 *
 * <p>Handles operation execution, connection delegation, and translation of Spring Data / Lettuce exceptions
 * into domain-specific {@link RedisStorageException}.</p>
 */
public class DefaultRedisService implements RedisService {

    private final StringRedisTemplate redisTemplate;

    public DefaultRedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "StringRedisTemplate must not be null");
    }

    @Override
    public void set(String key, String value) {
        validateKey(key);
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            throw new RedisStorageException("SET", key, e.getMessage(), e);
        }
    }

    @Override
    public void setWithTtl(String key, String value, Duration ttl) {
        validateKey(key);
        Objects.requireNonNull(ttl, "TTL duration must not be null");
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception e) {
            throw new RedisStorageException("SET_WITH_TTL", key, e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> get(String key) {
        validateKey(key);
        try {
            String value = redisTemplate.opsForValue().get(key);
            return Optional.ofNullable(value);
        } catch (Exception e) {
            throw new RedisStorageException("GET", key, e.getMessage(), e);
        }
    }

    @Override
    public Long increment(String key) {
        validateKey(key);
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            throw new RedisStorageException("INCREMENT", key, e.getMessage(), e);
        }
    }

    @Override
    public Long incrementBy(String key, long amount) {
        validateKey(key);
        try {
            return redisTemplate.opsForValue().increment(key, amount);
        } catch (Exception e) {
            throw new RedisStorageException("INCREMENT_BY", key, e.getMessage(), e);
        }
    }

    @Override
    public Long decrement(String key) {
        validateKey(key);
        try {
            return redisTemplate.opsForValue().decrement(key);
        } catch (Exception e) {
            throw new RedisStorageException("DECREMENT", key, e.getMessage(), e);
        }
    }

    @Override
    public Boolean delete(String key) {
        validateKey(key);
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            throw new RedisStorageException("DELETE", key, e.getMessage(), e);
        }
    }

    @Override
    public Boolean exists(String key) {
        validateKey(key);
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            throw new RedisStorageException("EXISTS", key, e.getMessage(), e);
        }
    }

    @Override
    public Boolean expire(String key, Duration ttl) {
        validateKey(key);
        Objects.requireNonNull(ttl, "TTL duration must not be null");
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, ttl));
        } catch (Exception e) {
            throw new RedisStorageException("EXPIRE", key, e.getMessage(), e);
        }
    }

    @Override
    public void hashSet(String key, String field, String value) {
        validateKey(key);
        validateField(field);
        try {
            redisTemplate.opsForHash().put(key, field, value);
        } catch (Exception e) {
            throw new RedisStorageException("HASH_SET", key, e.getMessage(), e);
        }
    }

    @Override
    public Optional<String> hashGet(String key, String field) {
        validateKey(key);
        validateField(field);
        try {
            Object value = redisTemplate.opsForHash().get(key, field);
            return Optional.ofNullable(value != null ? value.toString() : null);
        } catch (Exception e) {
            throw new RedisStorageException("HASH_GET", key, e.getMessage(), e);
        }
    }

    @Override
    public Boolean hashDelete(String key, String field) {
        validateKey(key);
        validateField(field);
        try {
            Long deleted = redisTemplate.opsForHash().delete(key, field);
            return deleted != null && deleted > 0;
        } catch (Exception e) {
            throw new RedisStorageException("HASH_DELETE", key, e.getMessage(), e);
        }
    }

    private void validateKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Redis key must not be null or blank");
        }
    }

    private void validateField(String field) {
        if (field == null || field.isBlank()) {
            throw new IllegalArgumentException("Redis hash field must not be null or blank");
        }
    }
}
