package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * State container encapsulating an ordered log of accepted request timestamps for a client key.
 *
 * <p>Uses an internal {@link ArrayDeque} of epoch millisecond timestamps. Because requests arrive
 * chronologically, timestamps in the log are naturally monotonically non-decreasing, allowing
 * $O(k)$ head eviction of expired timestamps where $k$ is the number of expired entries.</p>
 *
 * <p><strong>Thread Safety Invariant:</strong> This class is <em>not</em> internally synchronized.
 * All mutations and reads must occur exclusively inside atomic operations (such as
 * {@link java.util.concurrent.ConcurrentHashMap#compute}) managed by the caller.</p>
 *
 * <p>This is the only mutable object the rate limiter package publishes into a shared map, so the
 * confinement rule above is a hard invariant rather than a style preference:</p>
 * <ul>
 *   <li>An instance must never be returned, cached, or read outside the {@code compute} call that
 *       owns its key. Escaping the remapping function drops the happens-before edge that
 *       {@link java.util.concurrent.ConcurrentHashMap} establishes between successive
 *       {@code compute} calls, and concurrent {@link ArrayDeque} mutation corrupts the deque
 *       silently rather than failing fast.</li>
 *   <li>Adding an accessor that hands this object to a caller re-opens that hole, however
 *       convenient it looks. Derive a value inside {@code compute} and return the value instead.</li>
 * </ul>
 *
 * <p>The invariant is enforced by test, not by the compiler: see
 * {@code SlidingWindowLogStateIntegrityTest}.</p>
 */
public class SlidingWindowLog {

    private final ArrayDeque<Long> timestamps;

    /**
     * Constructs a new empty SlidingWindowLog.
     */
    public SlidingWindowLog() {
        this.timestamps = new ArrayDeque<>();
    }

    /**
     * Evicts all request timestamps from the head of the log that strictly precede the window cutoff.
     *
     * <p>A timestamp {@code t} is expired if {@code t < windowStartCutoffMillis}.
     * Timestamps where {@code t >= windowStartCutoffMillis} remain active in the rolling window.</p>
     *
     * @param windowStartCutoffMillis minimum epoch millisecond timestamp for active requests
     */
    public void evictExpired(long windowStartCutoffMillis) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStartCutoffMillis) {
            timestamps.pollFirst();
        }
    }

    /**
     * Appends a new request timestamp to the tail of the log.
     *
     * @param timestampEpochMillis accepted request timestamp in epoch milliseconds
     */
    public void addTimestamp(long timestampEpochMillis) {
        timestamps.addLast(timestampEpochMillis);
    }

    /**
     * Returns the current number of active timestamps in the log.
     *
     * @return active request count
     */
    public int size() {
        return timestamps.size();
    }

    /**
     * Returns whether the log contains no active request timestamps.
     *
     * @return true if log is empty; false otherwise
     */
    public boolean isEmpty() {
        return timestamps.isEmpty();
    }

    /**
     * Returns the oldest active timestamp at the head of the log without removing it.
     *
     * @return oldest timestamp, or null if log is empty
     */
    public Long getOldestTimestamp() {
        return timestamps.peekFirst();
    }

    /**
     * Returns the newest active timestamp at the tail of the log without removing it.
     *
     * @return newest timestamp, or null if log is empty
     */
    public Long getNewestTimestamp() {
        return timestamps.peekLast();
    }

    /**
     * Clears all timestamps from the log.
     */
    public void clear() {
        timestamps.clear();
    }
}
