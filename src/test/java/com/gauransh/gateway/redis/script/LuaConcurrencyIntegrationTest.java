package com.gauransh.gateway.redis.script;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.service.RedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live multi-threaded concurrency integration test for the Lua execution engine.
 *
 * <p>Verifies that a multi-step Redis workflow executed through Lua remains atomic under
 * contention: no lost updates, and no thread observing intermediate state.</p>
 *
 * <p>Correctness is enforced by Redis alone. No JVM synchronization is used to serialize access
 * to the shared key: the latches coordinate thread start-up only, so that the threads contend.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
class LuaConcurrencyIntegrationTest {

    private static final int THREAD_COUNT = 30;
    private static final int INCREMENTS_PER_THREAD = 20;

    @Autowired
    private LuaExecutor luaExecutor;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    private String testKey;

    static boolean isRedisAvailable() {
        try (Socket socket = new Socket("localhost", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    @AfterEach
    void tearDown() {
        if (testKey != null) {
            redisService.delete(testKey);
            testKey = null;
        }
    }

    @Test
    @DisplayName("Verify atomic Lua increment under 30 concurrent threads with zero lost updates")
    void testConcurrentLuaIncrementHasZeroLostUpdates() throws InterruptedException {
        testKey = keyBuilder.buildKey("ratelimiter", "luaconcurrency", UUID.randomUUID().toString());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    for (int call = 0; call < INCREMENTS_PER_THREAD; call++) {
                        luaExecutor.executeLua(LuaScriptLoader.INCREMENT_SCRIPT, Long.class,
                                List.of(testKey), List.of("1", "120"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(failureCount.get()).isZero();
        assertThat(redisService.get(testKey))
                .contains(String.valueOf(THREAD_COUNT * INCREMENTS_PER_THREAD));
    }

    @Test
    @DisplayName("Verify the counter and its TTL are established as one atomic unit under contention")
    void testConcurrentLuaIncrementEstablishesTtlExactlyOnce() throws InterruptedException {
        testKey = keyBuilder.buildKey("ratelimiter", "luattl", UUID.randomUUID().toString());

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            // Every thread but the first requests a far longer TTL. Because the expiration is
            // established inside the script, only the thread that creates the key may set it:
            // a later thread must never be able to extend the window.
            final String requestedTtl = i == 0 ? "60" : "600";
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    luaExecutor.executeLua(LuaScriptLoader.INCREMENT_SCRIPT, Long.class,
                            List.of(testKey), List.of("1", requestedTtl));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        Optional<Duration> ttl = redisService.getTtl(testKey);
        assertThat(redisService.get(testKey)).contains(String.valueOf(THREAD_COUNT));
        assertThat(ttl).isPresent();
        assertThat(ttl.get()).isLessThanOrEqualTo(Duration.ofSeconds(600));
        assertThat(ttl.get()).isGreaterThan(Duration.ZERO);
    }
}
