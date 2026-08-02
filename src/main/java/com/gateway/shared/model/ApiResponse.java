package com.gateway.shared.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Standard API envelope for system status and administrative endpoints.
 *
 * @param <T> the type of the payload data
 * @param success indicating whether the operation was successful
 * @param data payload data, populated when success is true
 * @param error error details, populated when success is false
 * @param timestamp UTC timestamp when the response was constructed
 * @param requestId correlation ID for tracing the request
 */
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        Instant timestamp,
        String requestId
) {

    public ApiResponse {
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    /**
     * Creates a successful API response envelope.
     *
     * @param data response payload
     * @param requestId request correlation ID
     * @param <T> type of data payload
     * @return successful ApiResponse
     */
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(true, data, null, Instant.now(), requestId);
    }

    /**
     * Creates an error API response envelope.
     *
     * @param error error payload
     * @param requestId request correlation ID
     * @param <T> type of data payload
     * @return error ApiResponse
     */
    public static <T> ApiResponse<T> error(ApiError error, String requestId) {
        return new ApiResponse<>(false, null, error, Instant.now(), requestId);
    }

    /**
     * Error detail representation.
     *
     * @param code machine-readable error code
     * @param message human-readable error description
     * @param details optional extra details or validation failure list
     */
    public record ApiError(
            String code,
            String message,
            Object details
    ) {
        public ApiError {
            Objects.requireNonNull(code, "code must not be null");
            Objects.requireNonNull(message, "message must not be null");
        }

        public static ApiError of(String code, String message) {
            return new ApiError(code, message, null);
        }

        public static ApiError of(String code, String message, Object details) {
            return new ApiError(code, message, details);
        }
    }
}
