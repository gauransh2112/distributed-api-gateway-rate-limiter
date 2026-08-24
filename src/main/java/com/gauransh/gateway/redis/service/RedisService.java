package com.gauransh.gateway.redis.service;

import com.gauransh.gateway.redis.exception.RedisStorageException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Storage Abstraction Contract for Redis operations.
 *
 * <p>Provides low-level key-value, counter, TTL, and hash storage primitives over Redis.
 * Application logic and rate limiting algorithms interact with Redis exclusively through
 * this contract.</p>
 */
public interface RedisService {

    /**
     * Sets a string value for the specified key.
     *
     * @param key   the Redis key
     * @param value the string value to set
     * @throws RedisStorageException if storage operation fails
     */
    void set(String key, String value);

    /**
     * Sets a string value with an automatic expiration time (TTL).
     *
     * @param key   the Redis key
     * @param value the string value to set
     * @param ttl   the expiration duration
     * @throws RedisStorageException if storage operation fails
     */
    void setWithTtl(String key, String value, Duration ttl);

    /**
     * Retrieves the string value associated with the specified key.
     *
     * @param key the Redis key
     * @return Optional containing the string value if present, or empty
     * @throws RedisStorageException if read operation fails
     */
    Optional<String> get(String key);

    /**
     * Atomically increments the numeric value of a key by 1.
     *
     * @param key the Redis key
     * @return the updated counter value after incrementing
     * @throws RedisStorageException if operation fails
     */
    Long increment(String key);

    /**
     * Atomically increments the numeric value of a key by the specified amount.
     *
     * @param key    the Redis key
     * @param amount the value to increment by
     * @return the updated counter value after incrementing
     * @throws RedisStorageException if operation fails
     */
    Long incrementBy(String key, long amount);

    /**
     * Atomically decrements the numeric value of a key by 1.
     *
     * @param key the Redis key
     * @return the updated counter value after decrementing
     * @throws RedisStorageException if operation fails
     */
    Long decrement(String key);

    /**
     * Deletes the specified key from Redis.
     *
     * @param key the Redis key
     * @return true if the key existed and was deleted, false otherwise
     * @throws RedisStorageException if delete operation fails
     */
    Boolean delete(String key);

    /**
     * Checks whether the specified key exists in Redis.
     *
     * @param key the Redis key
     * @return true if key exists, false otherwise
     * @throws RedisStorageException if existence check fails
     */
    Boolean exists(String key);

    /**
     * Sets an explicit expiration duration (TTL) on an existing key.
     *
     * @param key the Redis key
     * @param ttl the expiration duration
     * @return true if expiration was set, false if key does not exist or failed
     * @throws RedisStorageException if expire operation fails
     */
    Boolean expire(String key, Duration ttl);

    /**
     * Sets a field-value pair in a Redis hash.
     *
     * @param key   the Redis hash key
     * @param field the hash field name
     * @param value the string value
     * @throws RedisStorageException if hash operation fails
     */
    void hashSet(String key, String field, String value);

    /**
     * Retrieves the value of a field from a Redis hash.
     *
     * @param key   the Redis hash key
     * @param field the hash field name
     * @return Optional containing field value if present, or empty
     * @throws RedisStorageException if hash read operation fails
     */
    Optional<String> hashGet(String key, String field);

    /**
     * Deletes a field from a Redis hash.
     *
     * @param key   the Redis hash key
     * @param field the hash field name
     * @return true if field existed and was removed, false otherwise
     * @throws RedisStorageException if hash delete operation fails
     */
    Boolean hashDelete(String key, String field);
}
