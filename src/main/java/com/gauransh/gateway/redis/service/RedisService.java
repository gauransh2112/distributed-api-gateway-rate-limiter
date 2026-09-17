package com.gauransh.gateway.redis.service;

import com.gauransh.gateway.redis.exception.LuaExecutionException;
import com.gauransh.gateway.redis.exception.LuaScriptNotLoadedException;
import com.gauransh.gateway.redis.exception.RedisStorageException;
import com.gauransh.gateway.redis.script.LuaExecutor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Storage Abstraction Contract for Redis operations.
 *
 * <p>Provides low-level key-value, counter, TTL, atomic primitive, hash storage, and atomic Lua
 * execution capabilities over Redis. Application logic and rate limiting algorithms interact with
 * Redis exclusively through this contract.</p>
 *
 * <p>Single-command primitives are atomic on their own. Multi-step state transitions that must be
 * atomic as a whole are executed through {@link #executeLua(String, Class, List, List)}.</p>
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
     * Atomically sets a key to value only if key does not exist (Redis {@code SETNX}).
     *
     * @param key   the Redis key
     * @param value the string value
     * @return true if key was set, false if key already existed
     * @throws RedisStorageException if storage operation fails
     */
    Boolean setIfAbsent(String key, String value);

    /**
     * Atomically sets a key to value with expiration duration only if key does not exist (Redis {@code SETNX EX}).
     *
     * @param key   the Redis key
     * @param value the string value
     * @param ttl   the expiration duration
     * @return true if key was set, false if key already existed
     * @throws RedisStorageException if storage operation fails
     */
    Boolean setIfAbsentWithTtl(String key, String value, Duration ttl);

    /**
     * Atomically sets key to value and returns its old value (Redis {@code GETSET}).
     *
     * @param key   the Redis key
     * @param value the new string value
     * @return Optional containing previous string value if key existed, or empty
     * @throws RedisStorageException if storage operation fails
     */
    Optional<String> getAndSet(String key, String value);

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
     * Atomically decrements the numeric value of a key by the specified amount.
     *
     * @param key    the Redis key
     * @param amount the value to decrement by
     * @return the updated counter value after decrementing
     * @throws RedisStorageException if operation fails
     */
    Long decrementBy(String key, long amount);

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
     * Sets an explicit expiration duration (TTL) on an existing key (Redis {@code EXPIRE}).
     *
     * @param key the Redis key
     * @param ttl the expiration duration
     * @return true if expiration was set, false if key does not exist or failed
     * @throws RedisStorageException if expire operation fails
     */
    Boolean expire(String key, Duration ttl);

    /**
     * Sets an explicit expiration timestamp (Instant) on an existing key (Redis {@code EXPIREAT}).
     *
     * @param key      the Redis key
     * @param expireAt the target expiration timestamp
     * @return true if expiration was set, false if key does not exist or failed
     * @throws RedisStorageException if expireAt operation fails
     */
    Boolean expireAt(String key, Instant expireAt);

    /**
     * Gets the remaining TTL duration of a key.
     *
     * @param key the Redis key
     * @return Optional containing remaining Duration if key exists and has TTL, or empty if key missing / no TTL
     * @throws RedisStorageException if TTL query fails
     */
    Optional<Duration> getTtl(String key);

    /**
     * Removes the expiration from a key, turning it into a persistent key (Redis {@code PERSIST}).
     *
     * @param key the Redis key
     * @return true if expiration was removed, false if key does not exist or had no expiration
     * @throws RedisStorageException if persist operation fails
     */
    Boolean persist(String key);

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
     * Atomically sets field in hash to value only if field does not exist (Redis {@code HSETNX}).
     *
     * @param key   the Redis hash key
     * @param field the hash field name
     * @param value the string value
     * @return true if field was set, false if field already existed
     * @throws RedisStorageException if hash operation fails
     */
    Boolean hashSetIfAbsent(String key, String field, String value);

    /**
     * Atomically increments numeric value of a hash field by specified amount (Redis {@code HINCRBY}).
     *
     * @param key    the Redis hash key
     * @param field  the hash field name
     * @param amount the increment amount
     * @return updated field counter value
     * @throws RedisStorageException if hash operation fails
     */
    Long hashIncrement(String key, String field, long amount);

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

    /**
     * Executes a registered Lua script atomically, delegating to the module's {@link LuaExecutor}.
     *
     * <p>Used for workflows whose individual Redis commands would otherwise interleave with those of
     * other Gateway instances, such as "read state, calculate, update state, refresh TTL".</p>
     *
     * @param scriptName the logical script name (for example {@code increment.lua})
     * @param resultType the expected reply type
     * @param keys       the Redis keys the script operates on, mapped to the script's {@code KEYS} table
     * @param args       the script arguments, mapped to the script's {@code ARGV} table
     * @param <T>        the reply type
     * @return the script result, decoded as {@code resultType}
     * @throws LuaScriptNotLoadedException if the script is unknown or cannot be registered with Redis
     * @throws LuaExecutionException       if the script fails during execution
     */
    <T> T executeLua(String scriptName, Class<T> resultType, List<String> keys, List<String> args);
}
