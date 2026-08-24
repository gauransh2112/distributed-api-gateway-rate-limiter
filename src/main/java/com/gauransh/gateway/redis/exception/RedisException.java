package com.gauransh.gateway.redis.exception;

import com.gauransh.gateway.shared.exception.GatewayException;

/**
 * Base unchecked exception for all Redis module operational and storage failures.
 *
 * <p>Aligned with Primary Class definitions in Engineering Contracts.</p>
 */
public class RedisException extends GatewayException {

    public RedisException(String errorCode, String message) {
        super(errorCode, message);
    }

    public RedisException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
