package com.gauransh.gateway.shared.exception;

import com.gauransh.gateway.shared.model.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * Global exception handler capturing uncaught domain exceptions and translating
 * them into standard API response envelopes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(GatewayException.class)
    public ResponseEntity<ApiResponse<Void>> handleGatewayException(GatewayException ex) {
        String requestId = UUID.randomUUID().toString();
        log.error("Gateway domain error [requestId={}, code={}]: {}", requestId, ex.getErrorCode(), ex.getMessage());

        ApiResponse.ApiError apiError = ApiResponse.ApiError.of(ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(apiError, requestId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        String requestId = UUID.randomUUID().toString();
        log.error("Unhandled internal server error [requestId={}]: ", requestId, ex);

        ApiResponse.ApiError apiError = ApiResponse.ApiError.of(
                "INTERNAL_SERVER_ERROR",
                "An unexpected internal error occurred."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(apiError, requestId));
    }
}
