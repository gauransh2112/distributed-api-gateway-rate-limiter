package com.gauransh.gateway.ratelimiter.benchmark;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ClusterScalingBenchmarkRunner Unit Tests")
class ClusterScalingBenchmarkRunnerTest {

    @Test
    @DisplayName("Response classification distinguishes application success, verified routing pending, and unverified 404s")
    void testEvidenceBasedResponseClassification() {
        // Application 2xx is always admitted application success
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(200, false))
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.ADMITTED_APPLICATION_SUCCESS);
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(204, false))
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.ADMITTED_APPLICATION_SUCCESS);

        // 404 WITH rate-limit headers is verified admission on pending routed path
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(404, true))
                .as("HTTP 404 with X-RateLimit-* headers proves admission through RateLimitFilter")
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.ADMITTED_ROUTING_PENDING);

        // CRITICAL CHECK: 404 WITHOUT rate-limit headers is an UNEXPECTED_ERROR, not admitted
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(404, false))
                .as("HTTP 404 without rate-limit headers must NOT be inferred as admitted")
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.UNEXPECTED_ERROR);

        // 429 is always rate-limited
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(429, true))
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.RATE_LIMITED);
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(429, false))
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.RATE_LIMITED);

        // 503 is always backend unavailable under fail-closed
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(503, false))
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.BACKEND_UNAVAILABLE);

        // 500, 502, etc. are unexpected errors
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(500, false))
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.UNEXPECTED_ERROR);
        assertThat(ClusterScalingBenchmarkRunner.ResponseCategory.classify(502, false))
                .isEqualTo(ClusterScalingBenchmarkRunner.ResponseCategory.UNEXPECTED_ERROR);
    }

    @Test
    @DisplayName("Topologies have distinct, non-overlapping port assignments")
    void testTopologyPortAssignments() {
        ClusterScalingBenchmarkRunner.ClusterTopology single = ClusterScalingBenchmarkRunner.ClusterTopology.SINGLE_NODE;
        ClusterScalingBenchmarkRunner.ClusterTopology three = ClusterScalingBenchmarkRunner.ClusterTopology.THREE_NODES;
        ClusterScalingBenchmarkRunner.ClusterTopology five = ClusterScalingBenchmarkRunner.ClusterTopology.FIVE_NODES;

        assertThat(single.getNodeUrls()).containsExactly("http://localhost:18081");
        assertThat(three.getNodeUrls()).containsExactly(
                "http://localhost:18081",
                "http://localhost:18082",
                "http://localhost:18083"
        );
        assertThat(five.getNodeUrls()).containsExactly(
                "http://localhost:18081",
                "http://localhost:18082",
                "http://localhost:18083",
                "http://localhost:18084",
                "http://localhost:18085"
        );
    }

    @Test
    @DisplayName("BenchmarkConfig validates bounds and rejects illegal arguments")
    void testBenchmarkConfigValidation() {
        assertThatThrownBy(() -> new ClusterScalingBenchmarkRunner.BenchmarkConfig(
                ClusterScalingBenchmarkRunner.ClusterTopology.SINGLE_NODE,
                List.of(), // empty URLs
                "/path",
                100,
                10,
                5,
                "client",
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ClusterScalingBenchmarkRunner.BenchmarkConfig(
                ClusterScalingBenchmarkRunner.ClusterTopology.SINGLE_NODE,
                List.of("http://localhost:18081"),
                "/path",
                0, // non-positive requests
                10,
                5,
                "client",
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ClusterScalingBenchmarkRunner.BenchmarkConfig(
                ClusterScalingBenchmarkRunner.ClusterTopology.SINGLE_NODE,
                List.of("http://localhost:18081"),
                "/path",
                100,
                0, // non-positive concurrency
                5,
                "client",
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new ClusterScalingBenchmarkRunner.BenchmarkConfig(
                ClusterScalingBenchmarkRunner.ClusterTopology.SINGLE_NODE,
                List.of("http://localhost:18081"),
                "/path",
                100,
                10,
                -1, // negative warmup
                "client",
                Duration.ofSeconds(1)
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CLI dry-run execution succeeds without sending network traffic")
    void testCliDryRunExecution() {
        ClusterScalingBenchmarkRunner.main(new String[]{"--dry-run", "--requests=100", "--concurrency=10", "--warmup=0", "--topology=1"});
    }

    @Test
    @DisplayName("Request dispatch strictly enforces maximum in-flight concurrency bound and request count")
    void testConcurrencyBoundAndRequestCountEnforcement() throws IOException {
        // Spin up ephemeral local HTTP server to receive benchmark requests
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(ClusterScalingBenchmarkRunner.DEFAULT_PATH, exchange -> {
            try {
                // Introduce small artificial processing time so concurrent requests overlap
                Thread.sleep(15);
            } catch (InterruptedException ignored) {}

            exchange.getResponseHeaders().set("X-RateLimit-Remaining", "4");
            exchange.getResponseHeaders().set("X-RateLimit-Limit", "5");
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        String testUrl = "http://localhost:" + port;

        int requestedConcurrency = 4;
        int totalRequests = 20;

        try {
            ClusterScalingBenchmarkRunner runner = new ClusterScalingBenchmarkRunner();
            ClusterScalingBenchmarkRunner.BenchmarkConfig config = new ClusterScalingBenchmarkRunner.BenchmarkConfig(
                    ClusterScalingBenchmarkRunner.ClusterTopology.SINGLE_NODE,
                    List.of(testUrl),
                    ClusterScalingBenchmarkRunner.DEFAULT_PATH,
                    totalRequests,
                    requestedConcurrency,
                    0, // 0 warmup
                    "test-client",
                    Duration.ofSeconds(5)
            );

            HttpClient client = HttpClient.newHttpClient();
            ClusterScalingBenchmarkRunner.ClusterBenchmarkResult result = runner.executeWorkload(
                    client,
                    config,
                    totalRequests,
                    requestedConcurrency
            );

            // Assertions
            assertThat(result.runMetrics().totalRequests())
                    .as("Total processed requests must exactly match requested count")
                    .isEqualTo(totalRequests);

            assertThat(result.maxObservedConcurrency())
                    .as("Maximum observed in-flight concurrency must NEVER exceed the configured concurrency limit (%d)", requestedConcurrency)
                    .isLessThanOrEqualTo(requestedConcurrency);

            assertThat(result.admittedRoutingPendingCount())
                    .as("All 20 requests carried rate-limit headers and should be classified as admitted routing pending")
                    .isEqualTo(totalRequests);

            assertThat(result.unexpectedErrorCount())
                    .as("Zero unexpected errors should have occurred")
                    .isEqualTo(0);

            assertThat(result.getThroughputRps())
                    .as("Throughput must be positive and non-zero")
                    .isGreaterThan(0.0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Warm-up requests use isolated client ID while measured requests use primary client ID")
    void testWarmupClientIdIsolationAndPrimaryClientId() throws IOException {
        java.util.List<String> receivedClientIds = new java.util.concurrent.CopyOnWriteArrayList<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(ClusterScalingBenchmarkRunner.DEFAULT_PATH, exchange -> {
            String clientId = exchange.getRequestHeaders().getFirst(com.gauransh.gateway.ratelimiter.constant.RateLimitConstants.HEADER_CLIENT_ID);
            if (clientId != null) {
                receivedClientIds.add(clientId);
            }
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", "4");
            exchange.getResponseHeaders().set("X-RateLimit-Limit", "5");
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        String testUrl = "http://localhost:" + port;

        int warmupRequests = 5;
        int totalRequests = 10;
        int concurrency = 2;
        String primaryClientId = "primary-client-123";

        try {
            ClusterScalingBenchmarkRunner runner = new ClusterScalingBenchmarkRunner();
            ClusterScalingBenchmarkRunner.BenchmarkConfig config = new ClusterScalingBenchmarkRunner.BenchmarkConfig(
                    ClusterScalingBenchmarkRunner.ClusterTopology.SINGLE_NODE,
                    List.of(testUrl),
                    ClusterScalingBenchmarkRunner.DEFAULT_PATH,
                    totalRequests,
                    concurrency,
                    warmupRequests,
                    primaryClientId,
                    Duration.ofSeconds(5)
            );

            ClusterScalingBenchmarkRunner.ClusterBenchmarkResult result = runner.run(config);

            assertThat(result.runMetrics().totalRequests()).isEqualTo(totalRequests);
            assertThat(receivedClientIds).hasSize(warmupRequests + totalRequests);

            // First 5 requests must be warm-up requests with isolated client ID
            List<String> warmupClientIds = receivedClientIds.subList(0, warmupRequests);
            assertThat(warmupClientIds)
                    .as("Warm-up requests must all use the isolated client ID")
                    .allMatch(id -> id.equals(primaryClientId + "-warmup"));

            // Remaining 10 requests must be measured requests with primary client ID
            List<String> measuredClientIds = receivedClientIds.subList(warmupRequests, receivedClientIds.size());
            assertThat(measuredClientIds)
                    .as("Measured requests must all use the primary client ID")
                    .allMatch(id -> id.equals(primaryClientId));
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("When warmup is 0, zero warmup requests are dispatched")
    void testZeroWarmupDispatchesNoWarmupRequests() throws IOException {
        java.util.List<String> receivedClientIds = new java.util.concurrent.CopyOnWriteArrayList<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(ClusterScalingBenchmarkRunner.DEFAULT_PATH, exchange -> {
            String clientId = exchange.getRequestHeaders().getFirst(com.gauransh.gateway.ratelimiter.constant.RateLimitConstants.HEADER_CLIENT_ID);
            if (clientId != null) {
                receivedClientIds.add(clientId);
            }
            exchange.getResponseHeaders().set("X-RateLimit-Remaining", "4");
            exchange.getResponseHeaders().set("X-RateLimit-Limit", "5");
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();

        int port = server.getAddress().getPort();
        String testUrl = "http://localhost:" + port;

        int totalRequests = 8;
        int concurrency = 2;
        String primaryClientId = "zero-warmup-client";

        try {
            ClusterScalingBenchmarkRunner runner = new ClusterScalingBenchmarkRunner();
            ClusterScalingBenchmarkRunner.BenchmarkConfig config = new ClusterScalingBenchmarkRunner.BenchmarkConfig(
                    ClusterScalingBenchmarkRunner.ClusterTopology.SINGLE_NODE,
                    List.of(testUrl),
                    ClusterScalingBenchmarkRunner.DEFAULT_PATH,
                    totalRequests,
                    concurrency,
                    0, // 0 warmup
                    primaryClientId,
                    Duration.ofSeconds(5)
            );

            ClusterScalingBenchmarkRunner.ClusterBenchmarkResult result = runner.run(config);

            assertThat(result.runMetrics().totalRequests()).isEqualTo(totalRequests);
            assertThat(receivedClientIds).hasSize(totalRequests);
            assertThat(receivedClientIds)
                    .as("All dispatched requests must be measured requests using primary client ID with zero warm-up")
                    .allMatch(id -> id.equals(primaryClientId));
        } finally {
            server.stop(0);
        }
    }
}
