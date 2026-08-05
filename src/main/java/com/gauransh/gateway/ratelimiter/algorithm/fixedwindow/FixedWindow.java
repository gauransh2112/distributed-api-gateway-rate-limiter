package com.gauransh.gateway.ratelimiter.algorithm.fixedwindow;

import java.time.Instant;

/**
 * Immutable value object representing an active rate limit window for a specific client key.
 *
 * <p>Stores the window boundary start time (in epoch milliseconds) and the accumulated
 * request count within that window duration.</p>
 *
 * @param windowStartEpochMillis start timestamp of the active window in epoch milliseconds
 * @param requestCount total number of requests observed within this active window
 */
public record FixedWindow(
        long windowStartEpochMillis,
        long requestCount
) {
    public FixedWindow {
        if (windowStartEpochMillis < 0) {
            throw new IllegalArgumentException("windowStartEpochMillis cannot be negative");
        }
        if (requestCount < 0) {
            throw new IllegalArgumentException("requestCount cannot be negative");
        }
    }

    /**
     * Determines whether this window has expired relative to the given current window start timestamp.
     *
     * @param currentWindowStartEpochMillis start timestamp of the current window boundary
     * @return true if this window belongs to a previous window frame; false otherwise
     */
    public boolean isExpired(long currentWindowStartEpochMillis) {
        return this.windowStartEpochMillis < currentWindowStartEpochMillis;
    }

    /**
     * Calculates the exact instant when this fixed window resets and a new window begins.
     *
     * @param windowDurationMillis window duration in milliseconds
     * @return Instant marking the window reset boundary
     */
    public Instant getResetTime(long windowDurationMillis) {
        return Instant.ofEpochMilli(this.windowStartEpochMillis + windowDurationMillis);
    }
}
