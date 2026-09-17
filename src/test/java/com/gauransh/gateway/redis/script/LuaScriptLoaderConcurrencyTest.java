package com.gauransh.gateway.redis.script;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the Lua script cache behaves correctly when many request threads use it at once.
 *
 * <p>{@code LuaScriptLoader} holds the only long-lived mutable state in the Redis module: the
 * name-to-SHA cache. Every request that executes a script reads it, and cache-miss recovery writes
 * to it, so reads and writes genuinely overlap in production.</p>
 *
 * <p>Redis itself is not the subject here and is replaced by a deterministic stub: the property
 * under test is JVM-side cache integrity, not Redis semantics. Real Lua execution against live
 * Redis under contention is already covered by {@code LuaConcurrencyIntegrationTest}.</p>
 */
@DisplayName("LuaScriptLoader — script cache integrity under contention")
class LuaScriptLoaderConcurrencyTest {

    private static final int THREADS = 50;
    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    private StubRedisTemplate redisTemplate;
    private LuaScriptLoader loader;

    @BeforeEach
    void setUp() {
        redisTemplate = new StubRedisTemplate(SHA);
        loader = new LuaScriptLoader(redisTemplate, List.of(LuaScriptLoader.INCREMENT_SCRIPT));
        loader.initialize();
    }

    @Test
    @DisplayName("Concurrent getScript calls all observe the same fully loaded script")
    void testConcurrentReadsAreConsistent() throws Exception {
        List<LuaScript> results = runConcurrently(THREADS, () -> loader.getScript(LuaScriptLoader.INCREMENT_SCRIPT));

        assertEquals(THREADS, results.size());
        for (LuaScript script : results) {
            assertNotNull(script, "a reader must never observe a missing script");
            assertEquals(LuaScriptLoader.INCREMENT_SCRIPT, script.getName());
            assertEquals(SHA, script.getSha(), "every reader must observe the same cached SHA");
            assertTrue(script.getBody().contains("INCRBY"), "the script body must not be torn");
        }
    }

    @Test
    @DisplayName("Concurrent reloads converge on one consistent cache entry")
    void testConcurrentReloadsConverge() throws Exception {
        int callsAfterStartupRegistration = redisTemplate.callCount();

        List<LuaScript> results = runConcurrently(THREADS, () -> loader.reload(LuaScriptLoader.INCREMENT_SCRIPT));

        assertEquals(THREADS, results.size());
        for (LuaScript script : results) {
            assertEquals(SHA, script.getSha());
            assertTrue(script.getBody().contains("INCRBY"));
        }
        // Reload is idempotent: the body never changes, so the cached SHA must be unchanged too.
        assertEquals(SHA, loader.getScript(LuaScriptLoader.INCREMENT_SCRIPT).getSha());
        assertEquals(THREADS, redisTemplate.callCount() - callsAfterStartupRegistration,
                "each reload must register the script with Redis exactly once");
    }

    @Test
    @DisplayName("Readers never observe a torn entry while reloads replace it concurrently")
    void testConcurrentReadsDuringReloads() throws Exception {
        int readers = THREADS;
        int writers = 10;
        int total = readers + writers;

        ExecutorService executor = Executors.newFixedThreadPool(total);
        CountDownLatch readyLatch = new CountDownLatch(total);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<LuaScript>> futures = new ArrayList<>(total);

        try {
            for (int i = 0; i < total; i++) {
                final boolean isWriter = i < writers;
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return isWriter
                            ? loader.reload(LuaScriptLoader.INCREMENT_SCRIPT)
                            : loader.getScript(LuaScriptLoader.INCREMENT_SCRIPT);
                }));
            }

            assertTrue(readyLatch.await(30, TimeUnit.SECONDS), "workers failed to reach the start barrier");
            startLatch.countDown();

            for (Future<LuaScript> future : futures) {
                LuaScript script = future.get(30, TimeUnit.SECONDS);
                assertNotNull(script, "no thread may observe a missing script mid-reload");
                assertEquals(SHA, script.getSha());
                assertTrue(script.getBody().contains("INCRBY"));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Releases {@code threads} workers simultaneously and collects their results, rethrowing any
     * worker exception through {@link Future#get(long, TimeUnit)}.
     */
    private List<LuaScript> runConcurrently(int threads, ScriptOperation operation) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<LuaScript>> futures = new ArrayList<>(threads);
        List<LuaScript> results = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return operation.run();
                }));
            }

            assertTrue(readyLatch.await(30, TimeUnit.SECONDS), "workers failed to reach the start barrier");
            startLatch.countDown();

            for (Future<LuaScript> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
        } finally {
            executor.shutdownNow();
        }
        return results;
    }

    @FunctionalInterface
    private interface ScriptOperation {
        LuaScript run();
    }

    /**
     * Deterministic stand-in for {@link StringRedisTemplate} that returns a fixed SHA for every
     * {@code SCRIPT LOAD} without contacting Redis, and counts invocations atomically.
     */
    private static final class StubRedisTemplate extends StringRedisTemplate {

        private final String sha;
        private final AtomicInteger calls = new AtomicInteger();

        private StubRedisTemplate(String sha) {
            this.sha = sha;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(RedisCallback<T> action) {
            calls.incrementAndGet();
            return (T) sha;
        }

        int callCount() {
            return calls.get();
        }
    }
}
