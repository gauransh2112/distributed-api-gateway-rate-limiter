package com.gauransh.gateway.shared.transport;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Transport-agnostic representation of an incoming API request.
 *
 * <p>Isolates the core gateway pipeline and rate limiter engine from underlying
 * HTTP transport frameworks (e.g. Spring MVC, Servlet API, Netty, or gRPC adapters).</p>
 *
 * @param requestId unique request identifier for correlation and tracing
 * @param method HTTP method (e.g., GET, POST, PUT, DELETE)
 * @param path request URI path (e.g., /api/v1/orders)
 * @param queryParams unmodifiable map of query parameter keys to parameter values
 * @param headers unmodifiable map of header names (case-insensitive keys) to header values
 * @param clientIp remote client IP address
 * @param body raw request payload body bytes
 */
public record TransportRequest(
        String requestId,
        String method,
        String path,
        Map<String, List<String>> queryParams,
        Map<String, List<String>> headers,
        String clientIp,
        byte[] body
) {

    public TransportRequest {
        Objects.requireNonNull(requestId, "requestId must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(path, "path must not be null");
        queryParams = queryParams != null ? Map.copyOf(queryParams) : Collections.emptyMap();
        headers = headers != null ? Map.copyOf(headers) : Collections.emptyMap();
        Objects.requireNonNull(clientIp, "clientIp must not be null");
        body = body != null ? body.clone() : new byte[0];
    }

    /**
     * Gets the first header value matching the specified header name.
     *
     * @param name header name
     * @return optional containing the first value if present
     */
    public Optional<String> getFirstHeader(String name) {
        if (name == null || headers.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty()) {
                    return Optional.ofNullable(values.getFirst());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Gets the first query parameter matching the specified name.
     *
     * @param name query parameter name
     * @return optional containing parameter value if present
     */
    public Optional<String> getFirstQueryParam(String name) {
        if (name == null || queryParams.isEmpty()) {
            return Optional.empty();
        }
        List<String> values = queryParams.get(name);
        if (values != null && !values.isEmpty()) {
            return Optional.ofNullable(values.getFirst());
        }
        return Optional.empty();
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
