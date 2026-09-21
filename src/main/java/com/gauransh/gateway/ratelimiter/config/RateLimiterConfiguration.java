package com.gauransh.gateway.ratelimiter.config;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.FixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.RedisFixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.leakybucket.LeakyBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.RedisSlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.SlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.RedisSlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.TokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.noop.NoOpRateLimiter;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitPolicyResolver;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.service.RedisService;
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
            Clock clock,
            @Autowired(required = false) RedisService redisService,
            @Autowired(required = false) RedisKeyBuilder redisKeyBuilder
    ) {
        if (properties.getAlgorithm() == RateLimiterAlgorithm.FIXED_WINDOW) {
            // Distributed storage is selected through the existing Redis configuration block
            // rather than a separate algorithm constant: the algorithm is the same Fixed Window
            // counter either way, only the store differs.
            if (properties.getRedis().isEnabled()) {
                requireRedisBeans(redisService, redisKeyBuilder);
                return new RedisFixedWindowRateLimiter(
                        properties, keyResolver, policyResolver, clock, redisService, redisKeyBuilder);
            }
            return new FixedWindowRateLimiter(properties, keyResolver, policyResolver, clock);
        }
        if (properties.getAlgorithm() == RateLimiterAlgorithm.SLIDING_WINDOW_COUNTER) {
            if (properties.getRedis().isEnabled()) {
                requireRedisBeans(redisService, redisKeyBuilder);
                return new RedisSlidingWindowCounterRateLimiter(
                        properties, keyResolver, policyResolver, clock, redisService, redisKeyBuilder);
            }
            return new SlidingWindowCounterRateLimiter(properties, keyResolver, policyResolver, clock);
        }
        if (properties.getAlgorithm() == RateLimiterAlgorithm.SLIDING_WINDOW_LOG) {
            if (properties.getRedis().isEnabled()) {
                requireRedisBeans(redisService, redisKeyBuilder);
                return new RedisSlidingWindowLogRateLimiter(
                        properties, keyResolver, policyResolver, clock, redisService, redisKeyBuilder);
            }
            return new SlidingWindowLogRateLimiter(properties, keyResolver, policyResolver, clock);
        }
        if (properties.getAlgorithm() == RateLimiterAlgorithm.TOKEN_BUCKET) {
            return new TokenBucketRateLimiter(properties, keyResolver, policyResolver, clock);
        }
        if (properties.getAlgorithm() == RateLimiterAlgorithm.LEAKY_BUCKET) {
            return new LeakyBucketRateLimiter(properties, keyResolver, policyResolver, clock);
        }
        return new NoOpRateLimiter();
    }

    /**
     * Fails fast when distributed rate limiting is requested without the Redis storage layer,
     * rather than silently falling back to per-instance in-memory counting.
     */
    private static void requireRedisBeans(RedisService redisService, RedisKeyBuilder redisKeyBuilder) {
        if (redisService == null || redisKeyBuilder == null) {
            throw new IllegalStateException(
                    "Redis-backed rate limiting is enabled (gateway.rate-limit.redis.enabled=true) "
                            + "but the Redis storage beans are unavailable");
        }
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemUTC();
    }
}

