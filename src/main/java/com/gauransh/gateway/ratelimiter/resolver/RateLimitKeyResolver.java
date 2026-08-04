package com.gauransh.gateway.ratelimiter.resolver;

import com.gauransh.gateway.ratelimiter.model.RateLimitContext;

/**
 * Strategy contract for resolving the unique partition key used for rate limiting.
 *
 * <p>Extracts identification criteria (e.g. JWT Subject, API Key header, Client IP,
 * User ID) from the request context.</p>
 */
@FunctionalInterface
public interface RateLimitKeyResolver {

    /**
     * Resolves the rate limit partition key for the given request context.
     *
     * @param context request context containing HTTP metadata
     * @return unique string partition key for tracking rate limits
     */
    String resolveKey(RateLimitContext context);
}
