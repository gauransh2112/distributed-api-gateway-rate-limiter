package com.gateway.ratelimiter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Strongly typed configuration properties for rate limiting.
 *
 * <p>Binds configuration under prefix {@code gateway.rate-limit}. Designed to accommodate
 * algorithm strategies, capacity thresholds, window durations, burst capacities, and
 * distributed store settings (Redis).</p>
 */
@ConfigurationProperties(prefix = "gateway.rate-limit")
public class RateLimiterProperties {

    /**
     * Whether rate limiting enforcement is enabled globally.
     */
    private boolean enabled = true;

    /**
     * Selected rate limiting algorithm strategy.
     */
    private RateLimiterAlgorithm algorithm = RateLimiterAlgorithm.NO_OP;

    /**
     * Default maximum request capacity per window.
     */
    private long defaultCapacity = 100;

    /**
     * Default evaluation window duration.
     */
    private Duration defaultWindow = Duration.ofMinutes(1);

    /**
     * Default burst capacity permitted above standard rate limits.
     */
    private long burstCapacity = 20;

    /**
     * Default token refill rate per second (for Token Bucket algorithm).
     */
    private double refillRate = 10.0;

    /**
     * Redis configuration block for distributed rate limiting.
     */
    private RedisProperties redis = new RedisProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public RateLimiterAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(RateLimiterAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public long getDefaultCapacity() {
        return defaultCapacity;
    }

    public void setDefaultCapacity(long defaultCapacity) {
        this.defaultCapacity = defaultCapacity;
    }

    public Duration getDefaultWindow() {
        return defaultWindow;
    }

    public void setDefaultWindow(Duration defaultWindow) {
        this.defaultWindow = defaultWindow;
    }

    public long getBurstCapacity() {
        return burstCapacity;
    }

    public void setBurstCapacity(long burstCapacity) {
        this.burstCapacity = burstCapacity;
    }

    public double getRefillRate() {
        return refillRate;
    }

    public void setRefillRate(double refillRate) {
        this.refillRate = refillRate;
    }

    public RedisProperties getRedis() {
        return redis;
    }

    public void setRedis(RedisProperties redis) {
        this.redis = redis;
    }

    public static class RedisProperties {
        private boolean enabled = false;
        private String keyPrefix = "rate-limit:";
        private Duration timeout = Duration.ofMillis(500);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }
}
