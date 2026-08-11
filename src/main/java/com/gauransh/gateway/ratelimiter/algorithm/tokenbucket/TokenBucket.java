package com.gauransh.gateway.ratelimiter.algorithm.tokenbucket;

/**
 * Immutable value object representing the runtime token bucket state for a single client partition key.
 *
 * @param tokens current number of available tokens in the bucket
 * @param lastRefillTimestampMillis epoch timestamp in milliseconds when the bucket was last refilled
 */
public record TokenBucket(
        double tokens,
        long lastRefillTimestampMillis
) {}
