package com.gateway.ratelimiter.resolver;

import com.gateway.ratelimiter.model.RateLimitContext;
import com.gateway.ratelimiter.model.RateLimitPolicy;

/**
 * Strategy contract for determining which rate limit policy applies to a given request.
 *
 * <p>Allows dynamic resolution of rate limit thresholds based on client tier (e.g. Free vs Enterprise),
 * target API route, or request context attributes.</p>
 */
@FunctionalInterface
public interface RateLimitPolicyResolver {

    /**
     * Resolves the matching {@link RateLimitPolicy} for the provided request context.
     *
     * @param context request context
     * @return applicable rate limit policy
     */
    RateLimitPolicy resolvePolicy(RateLimitContext context);
}
