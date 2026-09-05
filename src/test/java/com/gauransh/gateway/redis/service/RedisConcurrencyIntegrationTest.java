package com.gauransh.gateway.redis.service;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.net.Socket;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live multi-threaded concurrency integration test for {@link RedisService} atomic primitives.
 *
 * <p>Verifies zero race conditions, zero lost updates, and atomic single-winner behavior under high contention.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
class RedisConcurrencyIntegrationTest {

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
        }
    }

    @Test
    @DisplayName("Verify atomic increment under 30 concurrent thread contention with zero lost updates")
    void testConcurrentAtomicIncrement() throws InterruptedException {
        testKey = keyBuilder.buildKey("ratelimiter", "concurrency", UUID.randomUUID().toString());
        int threadCount = 30;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    redisService.increment(testKey);
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

        Optional<String> finalValue = redisService.get(testKey);
        assertThat(finalValue).contains(String.valueOf(threadCount));
    }

    @Test
    @DisplayName("Verify atomic setIfAbsent single-winner guarantee under 30 concurrent threads")
    void testConcurrentSetIfAbsentSingleWinner() throws InterruptedException {
        testKey = keyBuilder.buildKey("ratelimiter", "lock", UUID.randomUUID().toString());
        int threadCount = 30;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String threadId = "thread-" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    Boolean set = redisService.setIfAbsent(testKey, threadId);
                    if (Boolean.TRUE.equals(set)) {
                        successCount.incrementAndGet();
                    }
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

        assertThat(successCount.get()).isEqualTo(1);
    }
}
