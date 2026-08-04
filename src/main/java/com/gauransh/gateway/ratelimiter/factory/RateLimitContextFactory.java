package com.gauransh.gateway.ratelimiter.factory;

import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitKeyResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Factory component creating immutable {@link RateLimitContext} instances from HTTP requests.
 *
 * <p>Single responsibility: extracts request metadata, injects deterministic clock timestamps,
 * resolves partition keys via {@link RateLimitKeyResolver}, and constructs context in one pass.</p>
 */
@Component
public class RateLimitContextFactory {

    private final RateLimitKeyResolver keyResolver;
    private final Clock clock;

    public RateLimitContextFactory(RateLimitKeyResolver keyResolver, Clock clock) {
        this.keyResolver = Objects.requireNonNull(keyResolver, "keyResolver must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Creates a fully initialized {@link RateLimitContext} from an incoming HTTP servlet request.
     *
     * @param request HTTP request
     * @return rate limit context
     */
    public RateLimitContext createContext(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        Map<String, List<String>> headers = extractHeaders(request);
        String ipAddress = extractClientIp(request);
        Instant timestamp = Instant.now(clock);

        RateLimitContext preliminaryContext = new RateLimitContext(
                null,
                request.getRequestURI(),
                request.getMethod(),
                timestamp,
                ipAddress,
                headers
        );

        String resolvedClientId = keyResolver.resolveKey(preliminaryContext);

        return new RateLimitContext(
                resolvedClientId,
                request.getRequestURI(),
                request.getMethod(),
                timestamp,
                ipAddress,
                headers
        );
    }

    private Map<String, List<String>> extractHeaders(HttpServletRequest request) {
        Map<String, List<String>> map = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                Enumeration<String> values = request.getHeaders(name);
                map.put(name, Collections.list(values));
            }
        }
        return map;
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader(RateLimitConstants.HEADER_X_FORWARDED_FOR);
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : RateLimitConstants.DEFAULT_LOCAL_IP;
    }
}
