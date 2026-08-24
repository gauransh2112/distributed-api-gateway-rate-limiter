package com.gauransh.gateway.redis.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gauransh.gateway.redis.exception.RedisStorageException;

import java.util.Objects;

/**
 * Jackson {@link ObjectMapper} based implementation of {@link RedisSerializer}.
 *
 * @param <T> the object type
 */
public class JacksonRedisSerializer<T> implements RedisSerializer<T> {

    private final ObjectMapper objectMapper;

    public JacksonRedisSerializer() {
        this(new ObjectMapper());
    }

    public JacksonRedisSerializer(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }

    @Override
    public String serialize(T object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RedisStorageException("SERIALIZE", "N/A", "Failed to serialize object to JSON: " + e.getMessage(), e);
        }
    }

    @Override
    public T deserialize(String json, Class<T> targetClass) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Objects.requireNonNull(targetClass, "Target class must not be null");
        try {
            return objectMapper.readValue(json, targetClass);
        } catch (JsonProcessingException e) {
            throw new RedisStorageException("DESERIALIZE", "N/A", "Failed to deserialize JSON to target class [" + targetClass.getName() + "]: " + e.getMessage(), e);
        }
    }
}
