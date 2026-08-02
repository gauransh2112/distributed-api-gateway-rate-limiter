package com.gauransh.gateway.shared.exception;

/**
 * Base unchecked exception for all Distributed API Gateway domain and operational failures.
 */
public class GatewayException extends RuntimeException {

    private final String errorCode;

    public GatewayException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public GatewayException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
