package com.gauransh.gateway.ratelimiter.benchmark;

import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone benchmark runner for measuring HTTP throughput, latency distributions,
 * and load balancing characteristics across single-node and multi-node Gateway cluster topologies.
 *
 * <p><strong>Engineering Context:</strong>
 * Evaluates real network I/O, OS process concurrency, and distributed Redis coordination across
 * live containerized instances. Supports 1-node baseline, 3-node cluster, and 5-node cluster topologies.</p>
 *
 * <p><strong>Key Design Principles:</strong>
 * <ul>
 *   <li><strong>Zero Production Code Alterations:</strong> Operates strictly in test scope against documented endpoints.</li>
 *   <li><strong>Bounded Concurrency Driver:</strong> Strictly bounds maximum simultaneous in-flight HTTP requests
 *       to {@code concurrency = N} using a dedicated pool of N virtual worker loops.</li>
 *   <li><strong>Evidence-Based Response Classification:</strong> Never infers admission from HTTP 404 alone;
 *       requires presence of {@code X-RateLimit-Remaining} or {@code X-RateLimit-Limit} headers.</li>
 *   <li><strong>Semantic Error Differentiation:</strong> Distinguishes application success (2xx), verified rate-limiter
 *       admission on pending routes (404 + headers), rate-limit rejections (429), Redis backend unavailability (503),
 *       and unexpected transport/server errors.</li>
 *   <li><strong>Key Isolation:</strong> Each benchmark execution generates a distinct UUID client key, ensuring
 *       zero cross-run Redis state contamination without relying on broad key deletions or flushes.</li>
 * </ul>
 */
public class ClusterScalingBenchmarkRunner {

    public static final String DEFAULT_PATH = "/api/v1/gateway/anything";
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Target deployment topology for benchmarking.
     */
    public enum ClusterTopology {
        SINGLE_NODE("1-Node Baseline", List.of("http://localhost:18081")),
        THREE_NODES("3-Node Cluster", List.of("http://localhost:18081", "http://localhost:18082", "http://localhost:18083")),
        FIVE_NODES("5-Node Cluster", List.of("http://localhost:18081", "http://localhost:18082", "http://localhost:18083", "http://localhost:18084", "http://localhost:18085"));

        private final String displayName;
        private final List<String> nodeUrls;

        ClusterTopology(String displayName, List<String> nodeUrls) {
            this.displayName = displayName;
            this.nodeUrls = nodeUrls;
        }

        public String getDisplayName() {
            return displayName;
        }

        public List<String> getNodeUrls() {
            return nodeUrls;
        }
    }

    /**
     * HTTP Response category according to API and rate-limiter contracts.
     */
    public enum ResponseCategory {
        /** Application-level success (HTTP 200..299). */
        ADMITTED_APPLICATION_SUCCESS,
        /** Request passed the RateLimitFilter but targeted an unmapped route (HTTP 404 with verified X-RateLimit-* headers). */
        ADMITTED_ROUTING_PENDING,
        /** Request rejected by the rate limiter due to quota exhaustion (HTTP 429 Too Many Requests). */
        RATE_LIMITED,
        /** Request refused due to Redis backend failure under FAIL_CLOSED policy (HTTP 503 Service Unavailable). */
        BACKEND_UNAVAILABLE,
        /** Unexpected status code or transport error (e.g. HTTP 500, unverified 404 without rate-limit headers, connection drop). */
        UNEXPECTED_ERROR;

        /**
         * Classifies an HTTP response using both status code and rate-limit header evidence.
         *
         * @param statusCode HTTP status code
         * @param hasRateLimitHeaders true if X-RateLimit-Remaining or X-RateLimit-Limit header is present
         * @return category
         */
        public static ResponseCategory classify(int statusCode, boolean hasRateLimitHeaders) {
            if (statusCode == 429) {
                return RATE_LIMITED;
            }
            if (statusCode == 503) {
                return BACKEND_UNAVAILABLE;
            }
            if (statusCode >= 200 && statusCode < 300) {
                return ADMITTED_APPLICATION_SUCCESS;
            }
            if (statusCode == 404 && hasRateLimitHeaders) {
                // Header evidence proves the request successfully passed through RateLimitFilter
                return ADMITTED_ROUTING_PENDING;
            }
            // An HTTP 404 without rate-limit headers is NOT verified admission; it indicates routing/proxy misdirection
            return UNEXPECTED_ERROR;
        }

        public boolean isAdmitted() {
            return this == ADMITTED_APPLICATION_SUCCESS || this == ADMITTED_ROUTING_PENDING;
        }
    }

    /**
     * Execution configuration for a benchmark run.
     */
    public record BenchmarkConfig(
            ClusterTopology topology,
            List<String> targetUrls,
            String requestPath,
            int totalRequests,
            int concurrency,
            int warmupRequests,
            String clientId,
            Duration requestTimeout
    ) {
        public BenchmarkConfig {
            Objects.requireNonNull(topology, "topology must not be null");
            Objects.requireNonNull(targetUrls, "targetUrls must not be null");
            if (targetUrls.isEmpty()) {
                throw new IllegalArgumentException("targetUrls must not be empty");
            }
            if (totalRequests <= 0) {
                throw new IllegalArgumentException("totalRequests must be strictly positive");
            }
            if (concurrency <= 0) {
                throw new IllegalArgumentException("concurrency must be strictly positive");
            }
            if (warmupRequests < 0) {
                throw new IllegalArgumentException("warmupRequests must not be negative");
            }
            requestPath = requestPath != null ? requestPath : DEFAULT_PATH;
            clientId = clientId != null ? clientId : "bench-" + topology.name().toLowerCase() + "-" + UUID.randomUUID();
            requestTimeout = requestTimeout != null ? requestTimeout : DEFAULT_REQUEST_TIMEOUT;
        }

        public static BenchmarkConfig createDefault(ClusterTopology topology, int totalRequests, int concurrency) {
            return createDefault(topology, totalRequests, concurrency, Math.min(50, totalRequests / 10));
        }

        public static BenchmarkConfig createDefault(ClusterTopology topology, int totalRequests, int concurrency, int warmupRequests) {
            return new BenchmarkConfig(
                    topology,
                    topology.getNodeUrls(),
                    DEFAULT_PATH,
                    totalRequests,
                    concurrency,
                    warmupRequests,
                    "bench-" + topology.name().toLowerCase() + "-" + UUID.randomUUID(),
                    DEFAULT_REQUEST_TIMEOUT
            );
        }
    }

    /**
     * Comprehensive result record for a cluster benchmark execution.
     */
    public record ClusterBenchmarkResult(
            BenchmarkConfig config,
            BenchmarkMetricsCollector.RunMetrics runMetrics,
            int admittedAppSuccessCount,
            int admittedRoutingPendingCount,
            int rateLimitedCount,
            int backendUnavailableCount,
            int unexpectedErrorCount,
            int maxObservedConcurrency,
            Map<String, Integer> requestsPerNode,
            Map<String, Integer> admittedPerNode,
            Map<String, Integer> rateLimitedPerNode,
            List<String> errorSamples
    ) {
        public int getTotalAdmitted() {
            return admittedAppSuccessCount + admittedRoutingPendingCount;
        }

        public double getThroughputRps() {
            return runMetrics.getRps();
        }

        public double getMeanLatencyMicros() {
            return runMetrics.getAverageLatencyMicros();
        }

        public double getP50LatencyMicros() {
            return runMetrics.getPercentileLatencyMicros(0.50);
        }

        public double getP95LatencyMicros() {
            return runMetrics.getPercentileLatencyMicros(0.95);
        }

        public double getP99LatencyMicros() {
            return runMetrics.getPercentileLatencyMicros(0.99);
        }

        public String toSummaryString() {
            return String.format(
                    "Topology: %-15s | Total: %5d | Admitted (2xx/404+H): %4d/%4d | 429s: %5d | 503s: %3d | Errors: %3d | Peak In-Flight: %3d | RPS: %8.1f | P50: %6.1f µs | P95: %6.1f µs | P99: %6.1f µs",
                    config.topology().getDisplayName(),
                    runMetrics.totalRequests(),
                    admittedAppSuccessCount,
                    admittedRoutingPendingCount,
                    rateLimitedCount,
                    backendUnavailableCount,
                    unexpectedErrorCount,
                    maxObservedConcurrency,
                    getThroughputRps(),
                    getP50LatencyMicros(),
                    getP95LatencyMicros(),
                    getP99LatencyMicros()
            );
        }
    }

    /**
     * Executes a benchmark campaign against the specified configuration using Java 21 HttpClient.
     *
     * @param config benchmark configuration
     * @return benchmark measurements and statistics
     */
    public ClusterBenchmarkResult run(BenchmarkConfig config) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // 1. Warm-up Phase (metrics and latencies discarded to isolate connection setup and JIT compilation)
        if (config.warmupRequests() > 0) {
            BenchmarkConfig warmupConfig = new BenchmarkConfig(
                    config.topology(),
                    config.targetUrls(),
                    config.requestPath(),
                    config.warmupRequests(),
                    config.concurrency(),
                    0,
                    config.clientId() + "-warmup",
                    config.requestTimeout()
            );
            executeWorkload(client, warmupConfig, config.warmupRequests(), config.concurrency());
        }

        // 2. Measured Execution Phase
        return executeWorkload(client, config, config.totalRequests(), config.concurrency());
    }

    /**
     * Executes a bounded-concurrency workload where maximum simultaneous in-flight requests cannot exceed N.
     */
    ClusterBenchmarkResult executeWorkload(
            HttpClient client,
            BenchmarkConfig config,
            int requestCount,
            int concurrency
    ) {
        List<String> targetUrls = config.targetUrls();
        int nodeCount = targetUrls.size();

        long[] rawLatenciesNanos = new long[requestCount];
        AtomicInteger admittedAppSuccessCounter = new AtomicInteger(0);
        AtomicInteger admittedRoutingPendingCounter = new AtomicInteger(0);
        AtomicInteger rateLimitedCounter = new AtomicInteger(0);
        AtomicInteger backendUnavailableCounter = new AtomicInteger(0);
        AtomicInteger unexpectedErrorCounter = new AtomicInteger(0);

        AtomicInteger nextRequestIndex = new AtomicInteger(0);
        AtomicInteger inFlightCounter = new AtomicInteger(0);
        AtomicInteger maxInFlightObserved = new AtomicInteger(0);

        Map<String, AtomicInteger> nodeTotalMap = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> nodeAdmittedMap = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> nodeRateLimitedMap = new ConcurrentHashMap<>();
        List<String> errorSamples = Collections.synchronizedList(new ArrayList<>());

        for (String url : targetUrls) {
            nodeTotalMap.put(url, new AtomicInteger(0));
            nodeAdmittedMap.put(url, new AtomicInteger(0));
            nodeRateLimitedMap.put(url, new AtomicInteger(0));
        }

        // Exactly 'concurrency' worker tasks are spawned
        int workerCount = Math.min(concurrency, requestCount);
        CountDownLatch workersReady = new CountDownLatch(workerCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch workersDone = new CountDownLatch(workerCount);

        long startNanos;
        long endNanos;

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int w = 0; w < workerCount; w++) {
                executor.submit(() -> {
                    workersReady.countDown();
                    try {
                        startLatch.await();

                        int reqIdx;
                        // Workers pull work sequentially, bounding simultaneous in-flight requests to workerCount <= concurrency
                        while ((reqIdx = nextRequestIndex.getAndIncrement()) < requestCount) {
                            int currentInFlight = inFlightCounter.incrementAndGet();
                            maxInFlightObserved.accumulateAndGet(currentInFlight, Math::max);

                            final String targetBaseUrl = targetUrls.get(reqIdx % nodeCount);
                            final String fullUrl = targetBaseUrl + config.requestPath();

                            long reqStart = System.nanoTime();
                            try {
                                HttpRequest request = HttpRequest.newBuilder()
                                        .uri(URI.create(fullUrl))
                                        .header(RateLimitConstants.HEADER_CLIENT_ID, config.clientId())
                                        .timeout(config.requestTimeout())
                                        .GET()
                                        .build();

                                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                                long reqDuration = System.nanoTime() - reqStart;

                                rawLatenciesNanos[reqIdx] = reqDuration;
                                nodeTotalMap.get(targetBaseUrl).incrementAndGet();

                                boolean hasHeaders = response.headers().firstValue(RateLimitConstants.HEADER_REMAINING).isPresent()
                                        || response.headers().firstValue(RateLimitConstants.HEADER_LIMIT).isPresent();

                                ResponseCategory category = ResponseCategory.classify(response.statusCode(), hasHeaders);
                                switch (category) {
                                    case ADMITTED_APPLICATION_SUCCESS -> {
                                        admittedAppSuccessCounter.incrementAndGet();
                                        nodeAdmittedMap.get(targetBaseUrl).incrementAndGet();
                                    }
                                    case ADMITTED_ROUTING_PENDING -> {
                                        admittedRoutingPendingCounter.incrementAndGet();
                                        nodeAdmittedMap.get(targetBaseUrl).incrementAndGet();
                                    }
                                    case RATE_LIMITED -> {
                                        rateLimitedCounter.incrementAndGet();
                                        nodeRateLimitedMap.get(targetBaseUrl).incrementAndGet();
                                    }
                                    case BACKEND_UNAVAILABLE -> backendUnavailableCounter.incrementAndGet();
                                    case UNEXPECTED_ERROR -> {
                                        unexpectedErrorCounter.incrementAndGet();
                                        if (errorSamples.size() < 10) {
                                            errorSamples.add(String.format("Status %d (headers=%b) on %s",
                                                    response.statusCode(), hasHeaders, fullUrl));
                                        }
                                    }
                                }
                            } catch (HttpTimeoutException te) {
                                rawLatenciesNanos[reqIdx] = System.nanoTime() - reqStart;
                                nodeTotalMap.get(targetBaseUrl).incrementAndGet();
                                unexpectedErrorCounter.incrementAndGet();
                                if (errorSamples.size() < 10) {
                                    errorSamples.add("Timeout: " + fullUrl);
                                }
                            } catch (IOException | InterruptedException ex) {
                                rawLatenciesNanos[reqIdx] = System.nanoTime() - reqStart;
                                nodeTotalMap.get(targetBaseUrl).incrementAndGet();
                                unexpectedErrorCounter.incrementAndGet();
                                if (errorSamples.size() < 10) {
                                    errorSamples.add("Transport failure: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                                }
                                if (ex instanceof InterruptedException) {
                                    Thread.currentThread().interrupt();
                                    break;
                                }
                            } finally {
                                inFlightCounter.decrementAndGet();
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        workersDone.countDown();
                    }
                });
            }

            try {
                workersReady.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            startNanos = System.nanoTime();
            startLatch.countDown(); // Release all workers simultaneously

            try {
                workersDone.await(120, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            endNanos = System.nanoTime();
        }

        long totalDurationNanos = endNanos - startNanos;

        Map<String, Integer> reqPerNode = new LinkedHashMap<>();
        Map<String, Integer> admPerNode = new LinkedHashMap<>();
        Map<String, Integer> rlmPerNode = new LinkedHashMap<>();
        for (String url : targetUrls) {
            reqPerNode.put(url, nodeTotalMap.get(url).get());
            admPerNode.put(url, nodeAdmittedMap.get(url).get());
            rlmPerNode.put(url, nodeRateLimitedMap.get(url).get());
        }

        int totalAdmitted = admittedAppSuccessCounter.get() + admittedRoutingPendingCounter.get();
        BenchmarkMetricsCollector.RunMetrics runMetrics = new BenchmarkMetricsCollector.RunMetrics(
                requestCount,
                totalAdmitted,
                rateLimitedCounter.get(),
                unexpectedErrorCounter.get() + backendUnavailableCounter.get(),
                totalDurationNanos,
                rawLatenciesNanos,
                0L,
                0L
        );

        return new ClusterBenchmarkResult(
                config,
                runMetrics,
                admittedAppSuccessCounter.get(),
                admittedRoutingPendingCounter.get(),
                rateLimitedCounter.get(),
                backendUnavailableCounter.get(),
                unexpectedErrorCounter.get(),
                maxInFlightObserved.get(),
                reqPerNode,
                admPerNode,
                rlmPerNode,
                errorSamples
        );
    }

    /**
     * Standalone CLI entrypoint.
     *
     * <pre>
     * Usage:
     *   .\mvnw.cmd test-compile
     *   .\mvnw.cmd exec:java -Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.ClusterScalingBenchmarkRunner -Dexec.classpathScope=test -Dexec.args="--help"
     * </pre>
     */
    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("   DISTRIBUTED API GATEWAY — SPRINT 17 CLUSTER SCALING BENCHMARK RUNNER        ");
        System.out.println("================================================================================");

        List<String> argList = Arrays.asList(args);
        if (argList.contains("--help") || argList.contains("-h")) {
            printHelp();
            return;
        }

        int totalRequests = parseArg(argList, "--requests", 300);
        int concurrency = parseArg(argList, "--concurrency", 30);
        int defaultWarmup = Math.min(50, totalRequests / 10);
        int warmup = parseArg(argList, "--warmup", defaultWarmup);
        String targetTopologyArg = parseStringArg(argList, "--topology", "all");
        boolean dryRun = argList.contains("--dry-run");

        System.out.printf("Configuration: requests=%d, concurrency=%d, warmup=%d, topology=%s, dryRun=%b%n%n",
                totalRequests, concurrency, warmup, targetTopologyArg, dryRun);

        if (dryRun) {
            System.out.println("Dry-run validation successful. No network traffic dispatched.");
            return;
        }

        ClusterScalingBenchmarkRunner runner = new ClusterScalingBenchmarkRunner();
        List<ClusterTopology> topologiesToRun = resolveTopologies(targetTopologyArg);

        System.out.println("Running benchmark campaign across " + topologiesToRun.size() + " topology configuration(s)...");
        System.out.println("--------------------------------------------------------------------------------");

        List<ClusterBenchmarkResult> results = new ArrayList<>();
        for (ClusterTopology topology : topologiesToRun) {
            BenchmarkConfig config = BenchmarkConfig.createDefault(topology, totalRequests, concurrency, warmup);
            System.out.printf("Benchmarking %s (nodes: %s, clientId: %s, warmup: %d)...%n",
                    topology.getDisplayName(), topology.getNodeUrls(), config.clientId(), config.warmupRequests());

            ClusterBenchmarkResult result = runner.run(config);
            results.add(result);
            System.out.println("Result: " + result.toSummaryString());
            if (!result.errorSamples().isEmpty()) {
                System.out.println("  Sample Errors: " + result.errorSamples());
            }
            System.out.println();
        }

        System.out.println("================================================================================");
        System.out.println("                           BENCHMARK CAMPAIGN SUMMARY                           ");
        System.out.println("================================================================================");
        for (ClusterBenchmarkResult res : results) {
            System.out.println(res.toSummaryString());
        }
        System.out.println("================================================================================");
    }

    private static List<ClusterTopology> resolveTopologies(String arg) {
        return switch (arg.toLowerCase()) {
            case "1", "single" -> List.of(ClusterTopology.SINGLE_NODE);
            case "3", "three" -> List.of(ClusterTopology.THREE_NODES);
            case "5", "five" -> List.of(ClusterTopology.FIVE_NODES);
            default -> List.of(ClusterTopology.SINGLE_NODE, ClusterTopology.THREE_NODES, ClusterTopology.FIVE_NODES);
        };
    }

    private static int parseArg(List<String> args, String prefix, int defaultValue) {
        for (String arg : args) {
            if (arg.startsWith(prefix + "=")) {
                try {
                    return Integer.parseInt(arg.substring((prefix + "=").length()));
                } catch (NumberFormatException ignored) {}
            }
        }
        return defaultValue;
    }

    private static String parseStringArg(List<String> args, String prefix, String defaultValue) {
        for (String arg : args) {
            if (arg.startsWith(prefix + "=")) {
                return arg.substring((prefix + "=").length());
            }
        }
        return defaultValue;
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  mvn exec:java -Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.ClusterScalingBenchmarkRunner -Dexec.classpathScope=test -Dexec.args=\"[options]\"");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --topology=[1|3|5|all]   Select target topology (default: all)");
        System.out.println("  --requests=[N]          Total request count per topology (default: 300)");
        System.out.println("  --concurrency=[N]       Virtual thread concurrency limit (default: 30)");
        System.out.println("  --warmup=[N]            Warm-up request count (default: min(50, requests/10), 0 to disable)");
        System.out.println("  --dry-run               Validate configuration without sending network traffic");
        System.out.println("  --help, -h              Print this help menu");
    }
}
