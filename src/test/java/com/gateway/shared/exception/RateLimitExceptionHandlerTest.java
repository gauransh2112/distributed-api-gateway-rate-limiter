package com.gateway.shared.exception;

import com.gateway.ratelimiter.exception.RateLimitExceededException;
import com.gateway.shared.model.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("GlobalExceptionHandler should map RateLimitExceededException to HTTP 429 and include Retry-After header")
    void shouldHandleRateLimitExceededException() {
        RateLimitExceededException exception = new RateLimitExceededException(
                "Quota exceeded for window",
                Duration.ofSeconds(15)
        );

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleRateLimitExceededException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals("15", response.getHeaders().getFirst("Retry-After"));

        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.success());
        assertNotNull(body.error());
        assertEquals("RATE_LIMIT_EXCEEDED", body.error().code());
        assertEquals("Quota exceeded for window", body.error().message());

        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) body.error().details();
        assertNotNull(details);
        assertEquals(15L, details.get("retryAfter"));
    }
}
