package com.gauransh.gateway.ratelimiter.concurrency;

import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link RateLimitContext} is deeply immutable and therefore safe to publish across
 * threads.
 *
 * <p>The record previously copied its header map with {@code Map.copyOf}, which is a shallow copy:
 * the map became unmodifiable while the {@code List} values stayed mutable, so a context that
 * documented itself as immutable was not. The Shared package rules state that published objects
 * must be immutable and that mutable shared state is prohibited, and safe publication under the
 * Java Memory Model depends on it. These tests pin that guarantee down at both levels.</p>
 */
@DisplayName("RateLimitContext — deep immutability and safe publication")
class RateLimitContextImmutabilityTest {

    private static final Instant TIMESTAMP = Instant.parse("2026-09-17T12:00:00.000Z");

    @Test
    @DisplayName("The header map itself rejects mutation")
    void testHeaderMapIsUnmodifiable() {
        RateLimitContext context = contextWithHeaders(mutableHeaders());

        assertThrows(UnsupportedOperationException.class,
                () -> context.requestHeaders().put("X-Injected", List.of("value")));
        assertThrows(UnsupportedOperationException.class,
                () -> context.requestHeaders().remove(RateLimitConstants.HEADER_CLIENT_ID));
    }

    @Test
    @DisplayName("Nested header value lists reject mutation")
    void testNestedHeaderListsAreUnmodifiable() {
        RateLimitContext context = contextWithHeaders(mutableHeaders());

        List<String> values = context.requestHeaders().get(RateLimitConstants.HEADER_CLIENT_ID);
        assertThrows(UnsupportedOperationException.class, () -> values.add("injected"));
        assertThrows(UnsupportedOperationException.class, () -> values.set(0, "tampered"));
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    @DisplayName("Mutating the source map after construction does not affect the context")
    void testSourceMapMutationDoesNotLeakIn() {
        Map<String, List<String>> source = mutableHeaders();
        RateLimitContext context = contextWithHeaders(source);

        source.put("X-Added-Later", new ArrayList<>(List.of("nope")));

        assertEquals(1, context.requestHeaders().size(), "context must not observe later source edits");
        assertTrue(context.getFirstHeader("X-Added-Later").isEmpty());
    }

    @Test
    @DisplayName("Mutating a source value list after construction does not affect the context")
    void testSourceListMutationDoesNotLeakIn() {
        Map<String, List<String>> source = mutableHeaders();
        List<String> originalValues = source.get(RateLimitConstants.HEADER_CLIENT_ID);
        RateLimitContext context = contextWithHeaders(source);

        originalValues.add("injected");
        originalValues.set(0, "tampered");

        assertEquals(List.of("client-a"), context.requestHeaders().get(RateLimitConstants.HEADER_CLIENT_ID),
                "the context must hold its own copy of every header value list");
        assertEquals(Optional.of("client-a"), context.getFirstHeader(RateLimitConstants.HEADER_CLIENT_ID));
    }

    @Test
    @DisplayName("A null header map yields an empty map rather than a null reference")
    void testNullHeadersYieldEmptyMap() {
        RateLimitContext context = contextWithHeaders(null);

        assertTrue(context.requestHeaders().isEmpty());
        assertTrue(context.getFirstHeader(RateLimitConstants.HEADER_CLIENT_ID).isEmpty());
    }

    @Test
    @DisplayName("A null header value element is tolerated and preserved, exactly as before the deep copy")
    void testNullHeaderElementIsToleratedAndPreserved() {
        Map<String, List<String>> source = new HashMap<>();
        source.put(RateLimitConstants.HEADER_CLIENT_ID, new ArrayList<>(Arrays.asList("client-a", null)));

        RateLimitContext context = assertDoesNotThrow(() -> contextWithHeaders(source),
                "a null header element must not become invalid because the copy got deeper");

        List<String> values = context.requestHeaders().get(RateLimitConstants.HEADER_CLIENT_ID);
        assertEquals(2, values.size(), "the null element must be preserved, not dropped");
        assertEquals("client-a", values.get(0));
        assertNull(values.get(1), "the null element must survive the defensive copy");

        // Unchanged from the original implementation: the first element is returned as-is.
        assertEquals(Optional.of("client-a"), context.getFirstHeader(RateLimitConstants.HEADER_CLIENT_ID));
    }

    @Test
    @DisplayName("A leading null header element still yields an empty Optional, exactly as before")
    void testNullFirstHeaderElementYieldsEmptyOptional() {
        Map<String, List<String>> source = new HashMap<>();
        source.put(RateLimitConstants.HEADER_CLIENT_ID, new ArrayList<>(Collections.singletonList(null)));

        RateLimitContext context = assertDoesNotThrow(() -> contextWithHeaders(source));

        assertEquals(1, context.requestHeaders().get(RateLimitConstants.HEADER_CLIENT_ID).size());
        assertTrue(context.getFirstHeader(RateLimitConstants.HEADER_CLIENT_ID).isEmpty(),
                "a null first value must resolve to an empty Optional, as it did before");
    }

    @Test
    @DisplayName("A null-containing header list is still deeply immutable")
    void testNullElementListRemainsUnmodifiable() {
        Map<String, List<String>> source = new HashMap<>();
        source.put(RateLimitConstants.HEADER_CLIENT_ID, new ArrayList<>(Arrays.asList("client-a", null)));
        RateLimitContext context = contextWithHeaders(source);

        List<String> values = context.requestHeaders().get(RateLimitConstants.HEADER_CLIENT_ID);
        assertThrows(UnsupportedOperationException.class, () -> values.add("injected"));
        assertThrows(UnsupportedOperationException.class, () -> values.set(0, "tampered"));
        assertThrows(UnsupportedOperationException.class, values::clear);
    }

    @Test
    @DisplayName("Mutating a null-containing source list after construction does not leak in")
    void testNullElementSourceMutationDoesNotLeakIn() {
        Map<String, List<String>> source = new HashMap<>();
        List<String> originalValues = new ArrayList<>(Arrays.asList("client-a", null));
        source.put(RateLimitConstants.HEADER_CLIENT_ID, originalValues);
        RateLimitContext context = contextWithHeaders(source);

        originalValues.add("injected");
        originalValues.set(0, "tampered");

        List<String> values = context.requestHeaders().get(RateLimitConstants.HEADER_CLIENT_ID);
        assertEquals(2, values.size());
        assertEquals("client-a", values.get(0));
        assertNull(values.get(1));
    }

    @Test
    @DisplayName("One context published to many threads yields identical reads under contention")
    void testSafePublicationAcrossThreads() throws Exception {
        int threads = 40;
        RateLimitContext context = contextWithHeaders(mutableHeaders());

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return context.getFirstHeader(RateLimitConstants.HEADER_CLIENT_ID).orElse("<missing>");
                }));
            }

            assertTrue(readyLatch.await(30, TimeUnit.SECONDS), "workers failed to reach the start barrier");
            startLatch.countDown();

            for (Future<String> future : futures) {
                assertEquals("client-a", future.get(30, TimeUnit.SECONDS),
                        "every thread must observe the same fully constructed header state");
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Map<String, List<String>> mutableHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put(RateLimitConstants.HEADER_CLIENT_ID, new ArrayList<>(List.of("client-a")));
        return headers;
    }

    private static RateLimitContext contextWithHeaders(Map<String, List<String>> headers) {
        return new RateLimitContext("client-a", "/api/v1/resource", "GET", TIMESTAMP, "127.0.0.1", headers);
    }
}
