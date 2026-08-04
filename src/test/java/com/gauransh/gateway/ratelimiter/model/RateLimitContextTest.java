package com.gauransh.gateway.ratelimiter.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitContextTest {

    @Test
    @DisplayName("Should initialize RateLimitContext and fallback clientId to ipAddress when clientId is null")
    void shouldFallbackClientIdToIpAddressWhenNull() {
        Instant now = Instant.now();
        RateLimitContext context = new RateLimitContext(
                null,
                "/api/v1/resource",
                "GET",
                now,
                "192.168.1.100",
                Map.of("X-API-Key", List.of("test-key"))
        );

        assertEquals("192.168.1.100", context.clientId());
        assertEquals("/api/v1/resource", context.endpoint());
        assertEquals("GET", context.httpMethod());
        assertEquals(now, context.timestamp());
        assertEquals("192.168.1.100", context.ipAddress());
    }

    @Test
    @DisplayName("Should extract header value case-insensitively using getFirstHeader")
    void shouldExtractHeaderCaseInsensitively() {
        RateLimitContext context = new RateLimitContext(
                "client-123",
                "/api/v1/orders",
                "POST",
                Instant.now(),
                "10.0.0.1",
                Map.of("X-Custom-Header", List.of("Value1", "Value2"))
        );

        Optional<String> headerVal = context.getFirstHeader("x-custom-header");
        assertTrue(headerVal.isPresent());
        assertEquals("Value1", headerVal.get());

        Optional<String> missingHeader = context.getFirstHeader("Non-Existent");
        assertTrue(missingHeader.isEmpty());
    }

    @Test
    @DisplayName("Should throw NullPointerException when mandatory attributes are null")
    void shouldThrowExceptionWhenMandatoryFieldsNull() {
        assertThrows(NullPointerException.class, () ->
                new RateLimitContext("client", null, "GET", Instant.now(), "127.0.0.1", Map.of())
        );

        assertThrows(NullPointerException.class, () ->
                new RateLimitContext("client", "/path", null, Instant.now(), "127.0.0.1", Map.of())
        );

        assertThrows(NullPointerException.class, () ->
                new RateLimitContext("client", "/path", "GET", null, "127.0.0.1", Map.of())
        );

        assertThrows(NullPointerException.class, () ->
                new RateLimitContext("client", "/path", "GET", Instant.now(), null, Map.of())
        );
    }
}
