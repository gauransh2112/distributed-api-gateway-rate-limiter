package com.gauransh.gateway.ratelimiter.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
 * @param requestHeaders deeply unmodifiable map of HTTP request headers
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
        requestHeaders = deepCopyHeaders(requestHeaders);
    }

    /**
     * Creates a deeply unmodifiable copy of the supplied header map.
     *
     * <p>{@link Map#copyOf(Map)} alone is a shallow copy: it makes the map unmodifiable but keeps
     * the caller's {@link List} instances as values, leaving them mutable. A context is therefore
     * only genuinely immutable, and safe to publish across threads, once each value list is copied
     * as well.</p>
     *
     * <p>Each value list is copied into a private {@link ArrayList} and wrapped with
     * {@link Collections#unmodifiableList(List)} rather than copied with {@link List#copyOf(java.util.Collection)}.
     * Both produce a list that rejects mutation, but {@code List.copyOf} additionally rejects null
     * elements, which would turn a defensive copy into an input-validation change: header values
     * that this type previously accepted would start throwing. The backing list is created here and
     * never escapes, so the wrapper is effectively immutable while remaining null-tolerant.</p>
     *
     * @param headers source header map, may be null
     * @return an unmodifiable map whose value lists are themselves unmodifiable
     */
    private static Map<String, List<String>> deepCopyHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, List<String>> copy = new LinkedHashMap<>(headers.size());
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        return Map.copyOf(copy);
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
