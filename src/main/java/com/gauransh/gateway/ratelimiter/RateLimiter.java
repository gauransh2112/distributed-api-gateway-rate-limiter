package com.gauransh.gateway.ratelimiter;

import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;

/**
 * Core contract for rate limiting evaluation engines in the Distributed API Gateway.
 *
 * <p>Implementations evaluate whether a given {@link RateLimitContext} violates configured
 * traffic policies and return a {@link RateLimitDecision}. All rate limiting algorithms
 * (e.g. NoOp, Fixed Window, Sliding Window, Token Bucket) implement this contract.</p>
 */
public interface RateLimiter {

    /**
     * Evaluates a rate limit check for the provided request context.
     *
     * @param context contextual metadata of the incoming request
     * @return decision indicating whether the request is allowed or rejected
     */
    RateLimitDecision allowRequest(RateLimitContext context);
}
