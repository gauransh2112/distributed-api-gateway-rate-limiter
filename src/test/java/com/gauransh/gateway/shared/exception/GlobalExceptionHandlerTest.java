package com.gauransh.gateway.shared.exception;

import com.gauransh.gateway.shared.model.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GlobalExceptionHandler Unit Tests")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Should map NoResourceFoundException to HTTP 404 with canonical ROUTE-001 error envelope")
    void shouldHandleNoResourceFoundException() {
        NoResourceFoundException exception = new NoResourceFoundException(HttpMethod.GET, "api/v1/gateway/scaling");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleNoResourceFoundException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.success());
        assertNull(body.data());
        assertNotNull(body.timestamp());
        assertNotNull(body.requestId());

        ApiResponse.ApiError error = body.error();
        assertNotNull(error);
        assertEquals("ROUTE-001", error.code());
        assertEquals("No configured route matches the incoming request.", error.message());
        assertNull(error.details());
    }

    @Test
    @DisplayName("Should map generic unexpected Exception to HTTP 500 with INTERNAL_SERVER_ERROR")
    void shouldHandleGenericException() {
        Exception exception = new RuntimeException("Unexpected database failure");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGenericException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.success());
        assertNotNull(body.timestamp());
        assertNotNull(body.requestId());

        ApiResponse.ApiError error = body.error();
        assertNotNull(error);
        assertEquals("INTERNAL_SERVER_ERROR", error.code());
        assertEquals("An unexpected internal error occurred.", error.message());
    }

    @Test
    @DisplayName("Should map GatewayException to HTTP 500 preserving domain error code")
    void shouldHandleGatewayException() {
        GatewayException exception = new GatewayException("CUSTOM_GATEWAY_ERROR", "Custom gateway domain error occurred");

        ResponseEntity<ApiResponse<Void>> response = exceptionHandler.handleGatewayException(exception);

        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());

        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.success());
        assertNotNull(body.error());
        assertEquals("CUSTOM_GATEWAY_ERROR", body.error().code());
        assertEquals("Custom gateway domain error occurred", body.error().message());
    }
}
