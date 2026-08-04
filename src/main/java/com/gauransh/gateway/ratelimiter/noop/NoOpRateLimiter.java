package com.gauransh.gateway.ratelimiter.noop;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * No-operation rate limiter implementation.
 *
 * <p>Always permits requests without applying quota restrictions or emitting dummy headers.</p>
 */
@Component("noOpRateLimiter")
@Primary
public class NoOpRateLimiter implements RateLimiter {

    @Override
    public RateLimitDecision allowRequest(RateLimitContext context) {
        return RateLimitDecision.unlimited(RateLimitConstants.REASON_NO_OP);
    }
}
