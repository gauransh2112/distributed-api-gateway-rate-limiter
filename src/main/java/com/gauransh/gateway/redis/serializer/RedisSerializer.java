package com.gauransh.gateway.redis.serializer;

import com.gauransh.gateway.redis.exception.RedisStorageException;

/**
 * Contract for serializing domain objects to JSON strings and deserializing JSON strings back to Java types.
 *
 * @param <T> the object type
 */
public interface RedisSerializer<T> {

    /**
     * Serializes an object instance to a JSON string representation.
     *
     * @param object the object to serialize
     * @return JSON string representation
     * @throws RedisStorageException if serialization fails
     */
    String serialize(T object);

    /**
     * Deserializes a JSON string into an instance of target class.
     *
     * @param json        the JSON string to deserialize
     * @param targetClass the target class type
     * @return object instance
     * @throws RedisStorageException if deserialization fails
     */
    T deserialize(String json, Class<T> targetClass);
}
