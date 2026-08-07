package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter;

import java.time.Instant;

/**
 * Immutable value object representing a client's rate limit window state for the Sliding Window Counter algorithm.
 *
 * <p>Stores the current window boundary start timestamp in epoch milliseconds,
 * the accumulated request count in the current window, and the accepted request count
 * from the immediately preceding window frame.</p>

 * @param currentWindowStartEpochMillis start timestamp of the active window frame in epoch milliseconds
 * @param currentCount total requests accepted within the active window frame
 * @param previousCount total requests accepted within the immediately preceding window frame
 */
public record SlidingWindowCounter(
        long currentWindowStartEpochMillis,
        long currentCount,
        long previousCount
) {
    public SlidingWindowCounter {
        if (currentWindowStartEpochMillis < 0) {
            throw new IllegalArgumentException("currentWindowStartEpochMillis cannot be negative");
        }
        if (currentCount < 0) {
            throw new IllegalArgumentException("currentCount cannot be negative");
        }
        if (previousCount < 0) {
            throw new IllegalArgumentException("previousCount cannot be negative");
        }
    }

    /**
     * Calculates the estimated request count within the rolling sliding window frame.
     *
     * @param nowMillis current epoch millisecond timestamp
     * @param windowDurationMillis window duration in milliseconds
     * @return weighted estimated request count over the sliding window
     */
    public double calculateWeightedCount(long nowMillis, long windowDurationMillis) {
        if (windowDurationMillis <= 0) {
            throw new IllegalArgumentException("windowDurationMillis must be strictly positive");
        }
        if (nowMillis < currentWindowStartEpochMillis) {
            return currentCount;
        }

        long elapsedMillis = nowMillis - currentWindowStartEpochMillis;
        if (elapsedMillis >= windowDurationMillis) {
            return 0.0;
        }

        double previousWeight = (double) (windowDurationMillis - elapsedMillis) / windowDurationMillis;
        return (previousCount * previousWeight) + currentCount;
    }

    /**
     * Calculates the exact instant when the active sliding window frame resets.
     *
     * @param windowDurationMillis window duration in milliseconds
     * @return Instant marking the current window reset boundary
     */
    public Instant getResetTime(long windowDurationMillis) {
        return Instant.ofEpochMilli(currentWindowStartEpochMillis + windowDurationMillis);
    }
}
