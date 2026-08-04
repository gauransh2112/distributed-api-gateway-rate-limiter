package com.gauransh.gateway.ratelimiter.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Encapsulates all request metadata required for rate limiting decisions.
 *
 * <p>Decouples rate limiting evaluation from transport-specific objects
 * such as {@code HttpServletRequest} or WebFlux request contexts.</p>
 *
 * @param clientId client identifier (e.g. JWT subject, API key, user ID, or IP fallback)
 * @param endpoint target API endpoint path (e.g. /api/v1/orders)
 * @param httpMethod HTTP method (e.g. GET, POST, PUT, DELETE)
 * @param timestamp timestamp when request reached the gateway
 * @param ipAddress client remote IP address
 * @param requestHeaders unmodifiable map of HTTP request headers
 */
public record RateLimitContext(
        String clientId,
        String endpoint,
        String httpMethod,
        Instant timestamp,
        String ipAddress,
        Map<String, List<String>> requestHeaders
) {
    public RateLimitContext {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(httpMethod, "httpMethod must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(ipAddress, "ipAddress must not be null");
        clientId = clientId != null ? clientId : ipAddress;
        requestHeaders = requestHeaders != null ? Map.copyOf(requestHeaders) : Collections.emptyMap();
    }

    /**
     * Helper to extract the first header value matching the given header name.
     *
     * @param headerName header name (case-insensitive)
     * @return optional header value
     */
    public Optional<String> getFirstHeader(String headerName) {
        if (headerName == null || requestHeaders.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> entry : requestHeaders.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    return Optional.ofNullable(values.getFirst());
                }
            }
        }
        return Optional.empty();
    }
}
