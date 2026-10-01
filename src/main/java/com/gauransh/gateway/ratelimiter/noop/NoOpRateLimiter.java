package com.gauransh.gateway.ratelimiter.noop;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;

/**
 * No-operation rate limiter implementation.
 *
 * <p>Always permits requests without applying quota restrictions or emitting dummy headers.</p>
 */
public class NoOpRateLimiter implements RateLimiter {

    @Override
    public RateLimitDecision allowRequest(RateLimitContext context) {
        return RateLimitDecision.unlimited(RateLimitConstants.REASON_NO_OP);
    }
}
