package com.gauransh.gateway.ratelimiter.config;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.FixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.SlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.noop.NoOpRateLimiter;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitPolicyResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Spring configuration providing default rate limiter bean definitions.
 *
 * <p>Registers an active {@link RateLimiter} bean based on configured
 * {@link RateLimiterProperties#getAlgorithm()} strategy.</p>
 */
@Configuration
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterConfiguration {

    @Bean
    @ConditionalOnMissingBean(RateLimiter.class)
    public RateLimiter rateLimiter(
            RateLimiterProperties properties,
            @Autowired(required = false) RateLimitKeyResolver keyResolver,
            @Autowired(required = false) RateLimitPolicyResolver policyResolver,
            Clock clock
    ) {
        if (properties.getAlgorithm() == RateLimiterAlgorithm.FIXED_WINDOW) {
            return new FixedWindowRateLimiter(properties, keyResolver, policyResolver, clock);
        }
        if (properties.getAlgorithm() == RateLimiterAlgorithm.SLIDING_WINDOW_COUNTER) {
            return new SlidingWindowCounterRateLimiter(properties, keyResolver, policyResolver, clock);
        }
        if (properties.getAlgorithm() == RateLimiterAlgorithm.SLIDING_WINDOW_LOG) {
            return new SlidingWindowLogRateLimiter(properties, keyResolver, policyResolver, clock);
        }
        return new NoOpRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemUTC();
    }
}

