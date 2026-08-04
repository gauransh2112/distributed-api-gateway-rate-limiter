package com.gateway.ratelimiter.writer;

import com.gateway.ratelimiter.constant.RateLimitConstants;
import com.gateway.ratelimiter.model.RateLimitDecision;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Component responsible for serializing rate limit evaluation decisions into HTTP response headers.
 *
 * <p>Single responsibility: appends standard headers (X-RateLimit-Limit, X-RateLimit-Remaining,
 * X-RateLimit-Reset) to the outgoing {@link HttpServletResponse}.</p>
 */
@Component
public class RateLimitHeaderWriter {

    /**
     * Writes rate limiting headers into the HTTP servlet response.
     *
     * @param response HTTP response
     * @param decision rate limit evaluation decision
     */
    public void writeHeaders(HttpServletResponse response, RateLimitDecision decision) {
        Objects.requireNonNull(response, "response must not be null");
        if (decision == null || !decision.includesHeaders()) {
            return;
        }

        response.setHeader(RateLimitConstants.HEADER_LIMIT, String.valueOf(decision.limit()));
        response.setHeader(RateLimitConstants.HEADER_REMAINING, String.valueOf(Math.max(0, decision.remainingRequests())));
        if (decision.resetTime() != null) {
            response.setHeader(RateLimitConstants.HEADER_RESET, String.valueOf(decision.resetTime().getEpochSecond()));
        }
    }
}
