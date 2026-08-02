package com.gauransh.gateway.shared.transport;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Transport-agnostic representation of an outgoing API response.
 *
 * <p>Returned by the core Gateway processing engine back to the active transport adapter.</p>
 *
 * @param statusCode HTTP response status code (e.g. 200, 429, 500)
 * @param headers unmodifiable map of HTTP response headers
 * @param body response body payload bytes
 */
public record TransportResponse(
        int statusCode,
        Map<String, List<String>> headers,
        byte[] body
) {

    public TransportResponse {
        headers = headers != null ? Map.copyOf(headers) : Collections.emptyMap();
        body = body != null ? body.clone() : new byte[0];
    }

    /**
     * Helper factory for creating a simple text/JSON response with HTTP status code.
     *
     * @param statusCode HTTP status code
     * @param headers HTTP headers
     * @param body response body
     * @return constructed TransportResponse
     */
    public static TransportResponse of(int statusCode, Map<String, List<String>> headers, byte[] body) {
        return new TransportResponse(statusCode, headers, body);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }
}
