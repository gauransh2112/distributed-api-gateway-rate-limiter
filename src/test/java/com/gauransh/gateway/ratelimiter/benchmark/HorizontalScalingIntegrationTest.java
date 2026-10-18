package com.gauransh.gateway.ratelimiter.benchmark;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.RedisFixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.RedisTokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.benchmark.BenchmarkWorkloadGenerator.MutableClock;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.service.RedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live multi-node concurrency integration test verifying shared Redis quota enforcement
 * across independent {@link com.gauransh.gateway.ratelimiter.RateLimiter} instances.
 *
 * <p><strong>Engineering Context:</strong>
 * In a distributed gateway cluster, each node process (e.g. gateway-1 through gateway-5) maintains
 * its own JVM heap, thread pools, and local object instances. Statelessness dictates that rate
 * limiting decisions must coordinate solely through atomic shared Redis operations rather than
 * local counters. This test instantiates multiple isolated rate limiter instances wired to the same
 * Redis backend to prove that:</p>
 * <ul>
 *   <li>Quota is global across all nodes (no per-node quota multiplier or leakage).</li>
 *   <li>Concurrent requests dispatched to different nodes coordinate atomically.</li>
 *   <li>Token bucket refill kinetics remain globally synchronized across nodes.</li>
 *   <li>Client isolation is preserved across the cluster.</li>
 * </ul>
 *
 * <p><strong>Scope Disclaimer:</strong>
 * This integration test verifies distributed rate-limiting state consistency across independent
 * rate limiter instances sharing a single Redis backend. It proves that quota enforcement is shared
 * and race-condition free across concurrent threads and simulated nodes, but does <em>not</em>
 * measure live multi-container network I/O, JVM container overhead, or containerized HTTP routing
 * (which belong to the explicit cluster benchmark runner).</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
@DisplayName("Horizontal Scaling Integration Test — Shared Multi-Node Redis Quota")
class HorizontalScalingIntegrationTest {

    private static final Instant BASE_TIME = Instant.parse("2026-10-01T12:00:00.000Z");

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String clientId;

    static boolean isRedisAvailable() {
        try (Socket socket = new Socket("localhost", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        clientId = "hscale-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        if (clientId != null) {
            Set<String> keys = redisTemplate.keys("*" + clientId.toLowerCase() + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        }
    }

    private RateLimitContext createContext(String client, Instant timestamp) {
        return new RateLimitContext(
                client,
                "/api/v1/resource",
                "GET",
                timestamp,
                "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(client))
        );
    }

    @Test
    @DisplayName("Fixed Window: 5 simulated nodes enforce a single shared quota under concurrent contention")
    void testFixedWindowMultiNodeSharedQuotaConcurrency() throws InterruptedException {
        long capacity = 10L;
        Duration window = Duration.ofMinutes(1);
        MutableClock clock = new MutableClock(BASE_TIME);

        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultCapacity(capacity);
        properties.setDefaultWindow(window);

        // Instantiate 5 independent rate limiter instances simulating 5 distinct Gateway nodes
        int nodeCount = 5;
        List<RedisFixedWindowRateLimiter> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(new RedisFixedWindowRateLimiter(
                    properties,
                    new DefaultRateLimitKeyResolver(),
                    null,
                    clock,
                    redisService,
                    keyBuilder
            ));
        }

        int requestsPerNode = 10;
        int totalRequests = nodeCount * requestsPerNode; // 50 requests total, quota is 10

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch readyLatch = new CountDownLatch(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger allowedCount = new AtomicInteger(0);
        AtomicInteger rejectedCount = new AtomicInteger(0);
        int[] perNodeProcessed = new int[nodeCount];
        ConcurrentLinkedQueue<Integer> nodesExecuting = new ConcurrentLinkedQueue<>();

        for (int req = 0; req < totalRequests; req++) {
            int nodeIndex = req % nodeCount;
            RedisFixedWindowRateLimiter targetNode = nodes.get(nodeIndex);

            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    RateLimitDecision decision = targetNode.allowRequest(createContext(clientId, clock.instant()));
                    nodesExecuting.add(nodeIndex);
                    if (decision.allowed()) {
                        allowedCount.incrementAndGet();
                    } else {
                        rejectedCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release all 50 concurrent requests simultaneously
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).as("All concurrent requests should finish within timeout").isTrue();
        assertThat(allowedCount.get())
                .as("Cumulative allowed requests across all 5 nodes must strictly equal capacity (10), not 10 per node")
                .isEqualTo((int) capacity);
        assertThat(rejectedCount.get())
                .as("Excess requests exceeding global quota must be rejected")
                .isEqualTo(totalRequests - (int) capacity);

        // Verify all 5 nodes actually participated in processing
        for (Integer idx : nodesExecuting) {
            perNodeProcessed[idx]++;
        }
        for (int i = 0; i < nodeCount; i++) {
            assertThat(perNodeProcessed[i])
                    .as("Node %d must have processed requests", i)
                    .isEqualTo(requestsPerNode);
        }
    }

    @Test
    @DisplayName("Token Bucket: 5 simulated nodes enforce burst capacity and shared time-based replenishment")
    void testTokenBucketMultiNodeBurstAndReplenishment() throws InterruptedException {
        long capacity = 10L;
        double refillRate = 2.0; // 2 tokens per second
        MutableClock clock = new MutableClock(BASE_TIME);

        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultCapacity(capacity);
        properties.setRefillRate(refillRate);

        int nodeCount = 5;
        List<RedisTokenBucketRateLimiter> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(new RedisTokenBucketRateLimiter(
                    properties,
                    new DefaultRateLimitKeyResolver(),
                    null,
                    clock,
                    redisService,
                    keyBuilder
            ));
        }

        // --- Step 1: Instantaneous burst across all 5 nodes ---
        int totalBurstRequests = 50;
        ExecutorService burstExecutor = Executors.newFixedThreadPool(totalBurstRequests);
        CountDownLatch burstReady = new CountDownLatch(totalBurstRequests);
        CountDownLatch burstStart = new CountDownLatch(1);
        CountDownLatch burstDone = new CountDownLatch(totalBurstRequests);

        AtomicInteger burstAllowed = new AtomicInteger(0);
        AtomicInteger burstRejected = new AtomicInteger(0);

        for (int req = 0; req < totalBurstRequests; req++) {
            int nodeIndex = req % nodeCount;
            RedisTokenBucketRateLimiter targetNode = nodes.get(nodeIndex);

            burstExecutor.submit(() -> {
                burstReady.countDown();
                try {
                    burstStart.await();
                    RateLimitDecision decision = targetNode.allowRequest(createContext(clientId, clock.instant()));
                    if (decision.allowed()) {
                        burstAllowed.incrementAndGet();
                    } else {
                        burstRejected.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    burstDone.countDown();
                }
            });
        }

        burstReady.await(5, TimeUnit.SECONDS);
        burstStart.countDown();
        burstDone.await(10, TimeUnit.SECONDS);
        burstExecutor.shutdown();

        assertThat(burstAllowed.get())
                .as("Initial burst across 5 nodes must admit exactly bucket capacity (%d)", capacity)
                .isEqualTo((int) capacity);
        assertThat(burstRejected.get())
                .as("Excess burst requests must be rejected")
                .isEqualTo(totalBurstRequests - (int) capacity);

        // --- Step 2: Immediate follow-up before replenishment must be 100% rejected ---
        RateLimitDecision immediate = nodes.get(0).allowRequest(createContext(clientId, clock.instant()));
        assertThat(immediate.allowed())
                .as("Immediate follow-up with exhausted bucket must be rejected")
                .isFalse();

        // --- Step 3: Advance logical clock by 3 seconds (expecting floor(3s * 2 tokens/s) = 6 tokens) ---
        clock.advance(Duration.ofSeconds(3));

        // Dispatch 10 requests across the 5 nodes
        int afterRefillRequests = 10;
        int afterRefillAllowed = 0;
        int afterRefillRejected = 0;

        for (int i = 0; i < afterRefillRequests; i++) {
            RateLimitDecision decision = nodes.get(i % nodeCount).allowRequest(createContext(clientId, clock.instant()));
            if (decision.allowed()) {
                afterRefillAllowed++;
            } else {
                afterRefillRejected++;
            }
        }

        assertThat(afterRefillAllowed)
                .as("Replenished tokens across 5 nodes must equal floor(refillRate * delta) = 6")
                .isEqualTo(6);
        assertThat(afterRefillRejected)
                .as("Requests exceeding replenished token count must be rejected")
                .isEqualTo(afterRefillRequests - 6);
    }

    @Test
    @DisplayName("Client Isolation: Multiple clients consuming quota across 5 nodes remain isolated")
    void testMultiNodeClientIsolation() throws InterruptedException {
        long capacity = 5L;
        MutableClock clock = new MutableClock(BASE_TIME);

        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultCapacity(capacity);
        properties.setDefaultWindow(Duration.ofMinutes(1));

        int nodeCount = 5;
        List<RedisFixedWindowRateLimiter> nodes = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            nodes.add(new RedisFixedWindowRateLimiter(
                    properties,
                    new DefaultRateLimitKeyResolver(),
                    null,
                    clock,
                    redisService,
                    keyBuilder
            ));
        }

        String clientA = "client-A-" + UUID.randomUUID();
        String clientB = "client-B-" + UUID.randomUUID();

        // Client A consumes full capacity across nodes 0 through 4
        for (int i = 0; i < capacity; i++) {
            RateLimitDecision decision = nodes.get(i % nodeCount).allowRequest(createContext(clientA, clock.instant()));
            assertThat(decision.allowed()).as("Client A request %d should be allowed", i).isTrue();
        }

        // Client A's next request is rejected across node 0
        RateLimitDecision clientAOverQuota = nodes.get(0).allowRequest(createContext(clientA, clock.instant()));
        assertThat(clientAOverQuota.allowed()).as("Client A exceeding quota must be rejected").isFalse();

        // Client B simultaneously sends requests across nodes 1 through 4: must all be allowed
        for (int i = 0; i < capacity; i++) {
            RateLimitDecision decision = nodes.get(i % nodeCount).allowRequest(createContext(clientB, clock.instant()));
            assertThat(decision.allowed())
                    .as("Client B must have its own isolated quota unaffected by Client A across nodes")
                    .isTrue();
        }

        // Clean up client keys
        Set<String> keysA = redisTemplate.keys("*" + clientA.toLowerCase() + "*");
        if (keysA != null) redisTemplate.delete(keysA);
        Set<String> keysB = redisTemplate.keys("*" + clientB.toLowerCase() + "*");
        if (keysB != null) redisTemplate.delete(keysB);
    }
}
