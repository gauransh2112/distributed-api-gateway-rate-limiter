package com.gateway.shared.transport;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransportRequestTest {

    @Test
    @DisplayName("TransportRequest should correctly store and retrieve request details")
    void testTransportRequestCreation() {
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"), "X-Api-Key", List.of("secret-123"));
        Map<String, List<String>> params = Map.of("page", List.of("1"), "size", List.of("20"));
        byte[] body = "{\"key\":\"value\"}".getBytes();

        TransportRequest request = new TransportRequest(
                "req-001",
                "POST",
                "/api/v1/resource",
                params,
                headers,
                "192.168.1.100",
                body
        );

        assertEquals("req-001", request.requestId());
        assertEquals("POST", request.method());
        assertEquals("/api/v1/resource", request.path());
        assertEquals("192.168.1.100", request.clientIp());
        assertArrayEquals(body, request.body());
    }

    @Test
    @DisplayName("getFirstHeader should perform case-insensitive matching")
    void testCaseInsensitiveHeaderLookup() {
        Map<String, List<String>> headers = Map.of("Authorization", List.of("Bearer token-abc"));
        TransportRequest request = new TransportRequest(
                "req-002",
                "GET",
                "/test",
                Map.of(),
                headers,
                "127.0.0.1",
                new byte[0]
        );

        Optional<String> authHeaderLower = request.getFirstHeader("authorization");
        Optional<String> authHeaderUpper = request.getFirstHeader("AUTHORIZATION");

        assertTrue(authHeaderLower.isPresent());
        assertEquals("Bearer token-abc", authHeaderLower.get());
        assertTrue(authHeaderUpper.isPresent());
        assertEquals("Bearer token-abc", authHeaderUpper.get());
    }

    @Test
    @DisplayName("getFirstQueryParam should retrieve first value for given param name")
    void testFirstQueryParamLookup() {
        Map<String, List<String>> params = Map.of("filter", List.of("active", "pending"));
        TransportRequest request = new TransportRequest(
                "req-003",
                "GET",
                "/items",
                params,
                Map.of(),
                "127.0.0.1",
                new byte[0]
        );

        Optional<String> filterParam = request.getFirstQueryParam("filter");

        assertTrue(filterParam.isPresent());
        assertEquals("active", filterParam.get());
    }

    @Test
    @DisplayName("TransportRequest should throw NullPointerException for null mandatory arguments")
    void testNullValidation() {
        assertThrows(NullPointerException.class, () -> new TransportRequest(
                null,
                "GET",
                "/path",
                Map.of(),
                Map.of(),
                "127.0.0.1",
                new byte[0]
        ));
    }
}
