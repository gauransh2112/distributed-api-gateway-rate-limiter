package com.gauransh.gateway.ratelimiter.algorithm.leakybucket;

/**
 * Immutable value object representing the runtime leaky bucket state for a single client partition key.
 *
 * @param waterLevel current volume/request units accumulated in the bucket
 * @param lastLeakTimestampMillis epoch timestamp in milliseconds when the bucket state was last updated/leaked
 */
public record LeakyBucket(
        double waterLevel,
        long lastLeakTimestampMillis
) {}
