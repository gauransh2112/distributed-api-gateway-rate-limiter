package com.gauransh.gateway.ratelimiter.resolver;

import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import org.springframework.stereotype.Component;

/**
 * Default implementation of {@link RateLimitKeyResolver}.
 *
 * <p>Resolves partition key by prioritizing explicitly configured headers (e.g. X-API-Key,
 * X-Client-Id, Authorization subject) before falling back to the client IP address.</p>
 */
@Component("defaultRateLimitKeyResolver")
public class DefaultRateLimitKeyResolver implements RateLimitKeyResolver {

    @Override
    public String resolveKey(RateLimitContext context) {
        if (context == null) {
            return RateLimitConstants.DEFAULT_ANONYMOUS_KEY;
        }
        return context.getFirstHeader(RateLimitConstants.HEADER_API_KEY)
                .or(() -> context.getFirstHeader(RateLimitConstants.HEADER_CLIENT_ID))
                .orElseGet(context::clientId);
    }
}
