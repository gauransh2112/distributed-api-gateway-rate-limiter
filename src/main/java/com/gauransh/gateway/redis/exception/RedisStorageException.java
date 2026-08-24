package com.gauransh.gateway.redis.exception;

import com.gauransh.gateway.shared.exception.GatewayException;

/**
 * Domain exception for failures during Redis storage operations.
 *
 * <p>Wraps underlying Spring Data Redis and Lettuce driver exceptions, providing contextual
 * diagnostics such as the target key and operation name.</p>
 */
public class RedisStorageException extends RedisException {

    private final String operation;
    private final String key;

    public RedisStorageException(String operation, String key, String message, Throwable cause) {
        super("REDIS_STORAGE_ERROR", String.format("Redis operation [%s] failed for key [%s]: %s", operation, key, message), cause);
        this.operation = operation;
        this.key = key;
    }

    public RedisStorageException(String operation, String key, String message) {
        super("REDIS_STORAGE_ERROR", String.format("Redis operation [%s] failed for key [%s]: %s", operation, key, message));
        this.operation = operation;
        this.key = key;
    }

    public String getOperation() {
        return operation;
    }

    public String getKey() {
        return key;
    }
}
