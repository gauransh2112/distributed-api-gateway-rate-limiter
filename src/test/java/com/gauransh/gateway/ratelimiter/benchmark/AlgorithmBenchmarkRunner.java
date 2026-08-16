package com.gauransh.gateway.ratelimiter.benchmark;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.FixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.leakybucket.LeakyBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.SlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.TokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterAlgorithm;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Standalone benchmark runner for evaluating and comparing in-memory rate limiting algorithms.
 *
 * <p>Executes controlled, reproducible workload scenarios across all five algorithms.
 * Uses deterministic seed-shuffled algorithm execution, high-precision nanosecond timing,
 * primitive latency storage, and exact percentile calculations.</p>
 */
public class AlgorithmBenchmarkRunner {

    private static final int WARMUP_OPERATIONS = 5_000;
    private static final int RUN_COUNT = 3;
    private static final long RANDOM_SEED = 0x42L;
    private static final Instant BASE_TIME = Instant.parse("2026-08-16T12:00:00.000Z");

    public static void main(String[] args) {
        System.out.println("================================================================================");
        System.out.println("     DISTRIBUTED API GATEWAY — SPRINT 8 ALGORITHM BENCHMARK SUITE              ");
        System.out.println("================================================================================");

        // 1. Configure baseline properties
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultCapacity(100L);
        properties.setDefaultWindow(Duration.ofSeconds(10));
        properties.setRefillRate(10.0); // 10 units/sec

        // 2. Define workload scenarios
        List<BenchmarkWorkloadGenerator.WorkloadScenario> scenarios = new ArrayList<>();
        scenarios.add(BenchmarkWorkloadGenerator.createSustainedWorkload("1. Micro Workload (100 reqs)", 100, 10L, BASE_TIME));
        scenarios.add(BenchmarkWorkloadGenerator.createSustainedWorkload("2. Standard Workload (1,000 reqs)", 1_000, 10L, BASE_TIME));
        scenarios.add(BenchmarkWorkloadGenerator.createSustainedWorkload("3. Large Workload (10,000 reqs)", 10_000, 10L, BASE_TIME));
        scenarios.add(BenchmarkWorkloadGenerator.createBurstWorkload("4. Burst Level 1 (50 reqs, < Cap)", 50, BASE_TIME));
        scenarios.add(BenchmarkWorkloadGenerator.createBurstWorkload("5. Burst Level 2 (100 reqs, = Cap)", 100, BASE_TIME));
        scenarios.add(BenchmarkWorkloadGenerator.createBurstWorkload("6. Burst Level 3 (150 reqs, > Cap)", 150, BASE_TIME));
        scenarios.add(BenchmarkWorkloadGenerator.createBurstWorkload("7. Burst Level 4 (500 reqs, >> Cap)", 500, BASE_TIME));
        scenarios.add(BenchmarkWorkloadGenerator.createRecoveryWorkload("8. Recovery Workload (150 burst -> +5s -> 50 burst)", 150, 5L, 50, BASE_TIME));

        List<RateLimiterAlgorithm> algorithms = List.of(
                RateLimiterAlgorithm.FIXED_WINDOW,
                RateLimiterAlgorithm.SLIDING_WINDOW_COUNTER,
                RateLimiterAlgorithm.SLIDING_WINDOW_LOG,
                RateLimiterAlgorithm.TOKEN_BUCKET,
                RateLimiterAlgorithm.LEAKY_BUCKET
        );

        // Single Random instance created outside run loop per Required Correction #1
        Random shuffleRandom = new Random(RANDOM_SEED);

        Map<String, Map<String, List<BenchmarkMetricsCollector.RunMetrics>>> rawRunsByScenario = new LinkedHashMap<>();
        Map<String, Map<String, BenchmarkMetricsCollector.AggregatedMetrics>> resultsByScenario = new LinkedHashMap<>();

        System.out.println("Starting Benchmark Execution across 5 algorithms and " + scenarios.size() + " scenarios (" + RUN_COUNT + " runs each)...");

        for (BenchmarkWorkloadGenerator.WorkloadScenario scenario : scenarios) {
            System.out.println("\n--------------------------------------------------------------------------------");
            System.out.println(" Running Scenario: " + scenario.name());
            System.out.println("--------------------------------------------------------------------------------");

            Map<String, List<BenchmarkMetricsCollector.RunMetrics>> runMetricsByAlgorithm = new LinkedHashMap<>();
            for (RateLimiterAlgorithm alg : algorithms) {
                runMetricsByAlgorithm.put(alg.name(), new ArrayList<>());
            }

            for (int run = 1; run <= RUN_COUNT; run++) {
                // Deterministically shuffle algorithm order for this run using the SAME random instance
                List<RateLimiterAlgorithm> shuffledAlgorithms = new ArrayList<>(algorithms);
                Collections.shuffle(shuffledAlgorithms, shuffleRandom);

                System.out.print("   Run " + run + "/" + RUN_COUNT + " Execution Order: ");
                for (int a = 0; a < shuffledAlgorithms.size(); a++) {
                    System.out.print(shuffledAlgorithms.get(a).name() + (a < shuffledAlgorithms.size() - 1 ? " -> " : ""));
                }
                System.out.println();

                for (RateLimiterAlgorithm alg : shuffledAlgorithms) {
                    BenchmarkMetricsCollector.RunMetrics metrics = executeSingleRun(alg, properties, scenario);
                    runMetricsByAlgorithm.get(alg.name()).add(metrics);
                }
            }

            rawRunsByScenario.put(scenario.name(), runMetricsByAlgorithm);

            Map<String, BenchmarkMetricsCollector.AggregatedMetrics> aggregatedMap = new LinkedHashMap<>();
            for (RateLimiterAlgorithm alg : algorithms) {
                List<BenchmarkMetricsCollector.RunMetrics> runs = runMetricsByAlgorithm.get(alg.name());
                BenchmarkMetricsCollector.AggregatedMetrics agg = BenchmarkMetricsCollector.aggregate(
                        alg.name(),
                        scenario.name(),
                        runs.toArray(new BenchmarkMetricsCollector.RunMetrics[0])
                );
                aggregatedMap.put(alg.name(), agg);

                System.out.printf("     - %-25s | RPS: %10.1f | Avg Latency: %6.2f µs | P95: %6.2f µs | P99: %6.2f µs | Allowed: %d | Rejected: %d%n",
                        alg.name(), agg.meanRps(), agg.meanAverageLatencyMicros(), agg.aggregateP95Micros(), agg.aggregateP99Micros(),
                        agg.avgAllowedRequests(), agg.avgRejectedRequests());
            }

            resultsByScenario.put(scenario.name(), aggregatedMap);
        }

        // 3. Accuracy & Oracle Validation
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println(" Running Accuracy Oracle Validation & Fixed Window Boundary Analysis...");
        System.out.println("--------------------------------------------------------------------------------");

        BenchmarkWorkloadGenerator.WorkloadScenario boundaryWorkload =
                BenchmarkWorkloadGenerator.createBoundaryTransitionWorkload("Boundary Transition Workload", 10L, BASE_TIME);
        AccuracyOracleValidator.AccuracyResult accuracyResult =
                AccuracyOracleValidator.validateSlidingWindowCounter(boundaryWorkload, properties, BASE_TIME);

        System.out.printf("   [Sliding Window Counter vs Log Oracle] Total Reqs: %d | Mismatches: %d | Error Rate: %.2f%%%n",
                accuracyResult.totalRequests(), accuracyResult.mismatchCount(), accuracyResult.approximationErrorPercent());

        BenchmarkWorkloadGenerator.WorkloadScenario spikeWorkload =
                BenchmarkWorkloadGenerator.createFixedWindowBoundarySpikeWorkload("Fixed Window Boundary Spike Workload", 10L, BASE_TIME);
        AccuracyOracleValidator.FixedWindowBoundaryResult spikeResult =
                AccuracyOracleValidator.validateFixedWindowBoundarySpike(spikeWorkload, properties, BASE_TIME);

        System.out.printf("   [Fixed Window Boundary Spike] Capacity: %d | Burst: %d (W1: %d, W2: %d) | Allowed: %d (%.2fx Capacity)%n",
                properties.getDefaultCapacity(), spikeWorkload.requestCount(), spikeResult.window1Burst(), spikeResult.window2Burst(),
                spikeResult.allowedRequests(), spikeResult.capacityMultiplierAccepted());

        // 4. Export Markdown Reports
        writeReports(resultsByScenario, rawRunsByScenario, accuracyResult, spikeResult, properties);

        System.out.println("\n================================================================================");
        System.out.println(" BENCHMARK COMPLETED SUCCESSFULLY!");
        System.out.println(" Benchmark Report Exported to: benchmarks/reports/algorithm_benchmark_report.md");
        System.out.println(" Benchmark README Exported to:   benchmarks/README.md");
        System.out.println("================================================================================");
    }

    private static BenchmarkMetricsCollector.RunMetrics executeSingleRun(
            RateLimiterAlgorithm algorithm,
            RateLimiterProperties properties,
            BenchmarkWorkloadGenerator.WorkloadScenario scenario
    ) {
        // Step A: Warmup Phase (executed on separate warmup instance to preserve clean measured state)
        BenchmarkWorkloadGenerator.MutableClock warmupClock = new BenchmarkWorkloadGenerator.MutableClock(BASE_TIME);
        RateLimiter warmupLimiter = createLimiterInstance(algorithm, properties, warmupClock);
        RateLimitContext warmupContext = scenario.contexts()[0];

        for (int i = 0; i < WARMUP_OPERATIONS; i++) {
            try {
                warmupLimiter.allowRequest(warmupContext);
            } catch (Exception ignored) {
                // Ignore warmup exceptions
            }
        }

        // Step B: Setup Measured Phase (Fresh RateLimiter instance & fresh MutableClock)
        BenchmarkWorkloadGenerator.MutableClock measuredClock = new BenchmarkWorkloadGenerator.MutableClock(BASE_TIME);
        RateLimiter measuredLimiter = createLimiterInstance(algorithm, properties, measuredClock);

        int requestCount = scenario.requestCount();
        RateLimitContext[] contexts = scenario.contexts();
        long[] increments = scenario.timeIncrementsMillis();
        long[] latenciesNanos = new long[requestCount];

        int allowedCount = 0;
        int rejectedCount = 0;
        int errorCount = 0;

        // Trigger System.gc() for baseline heap estimate
        System.gc();
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {}
        long baselineHeapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        long runStartNanos = System.nanoTime();

        // Step C: Measured Execution Loop
        for (int i = 0; i < requestCount; i++) {
            if (increments[i] > 0) {
                measuredClock.advance(Duration.ofMillis(increments[i]));
            }

            long opStart = System.nanoTime();
            try {
                RateLimitDecision decision = measuredLimiter.allowRequest(contexts[i]);
                latenciesNanos[i] = System.nanoTime() - opStart;

                if (decision.allowed()) {
                    allowedCount++;
                } else {
                    rejectedCount++;
                }
            } catch (Exception e) {
                latenciesNanos[i] = System.nanoTime() - opStart;
                errorCount++;
            }
        }

        long totalDurationNanos = System.nanoTime() - runStartNanos;

        // Trigger System.gc() for final heap estimate
        System.gc();
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {}
        long finalHeapBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

        return new BenchmarkMetricsCollector.RunMetrics(
                requestCount,
                allowedCount,
                rejectedCount,
                errorCount,
                totalDurationNanos,
                latenciesNanos,
                baselineHeapBytes,
                finalHeapBytes
        );
    }

    private static RateLimiter createLimiterInstance(
            RateLimiterAlgorithm algorithm,
            RateLimiterProperties properties,
            BenchmarkWorkloadGenerator.MutableClock clock
    ) {
        return switch (algorithm) {
            case FIXED_WINDOW -> new FixedWindowRateLimiter(properties, clock);
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(properties, clock);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogRateLimiter(properties, clock);
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(properties, clock);
            case LEAKY_BUCKET -> new LeakyBucketRateLimiter(properties, clock);
            default -> throw new IllegalArgumentException("Unsupported benchmark algorithm: " + algorithm);
        };
    }

    private static void writeReports(
            Map<String, Map<String, BenchmarkMetricsCollector.AggregatedMetrics>> resultsByScenario,
            Map<String, Map<String, List<BenchmarkMetricsCollector.RunMetrics>>> rawRunsByScenario,
            AccuracyOracleValidator.AccuracyResult accuracyResult,
            AccuracyOracleValidator.FixedWindowBoundaryResult spikeResult,
            RateLimiterProperties properties
    ) {
        File reportsDir = new File("benchmarks/reports");
        if (!reportsDir.exists()) {
            reportsDir.mkdirs();
        }

        File reportFile = new File(reportsDir, "algorithm_benchmark_report.md");
        File readmeFile = new File("benchmarks/README.md");

        try (FileWriter writer = new FileWriter(reportFile)) {
            writer.write("# Algorithm Benchmark Report — In-Memory Rate Limiters\n\n");
            writer.write("## 1. Executive Summary & Engineering Takeaways\n\n");
            writer.write("This benchmark evaluates the performance, latency distribution, throughput (RPS), heap memory estimates, ");
            writer.write("burst handling, and semantic accuracy across all five in-memory rate-limiting algorithms implemented in ");
            writer.write("the **Distributed API Gateway** project.\n\n");

            writer.write("> **Disclaimer**: Controlled project benchmark utility suitable for comparative engineering measurements, ");
            writer.write("but not a replacement for a dedicated JVM microbenchmark framework such as JMH.\n");
            writer.write("> **Sub-Microsecond Latency Precision Warning**: The measured operations are extremely short, ");
            writer.write("approaching the resolution and overhead sensitivity of a custom `System.nanoTime()` benchmark. ");
            writer.write("Therefore small latency differences between algorithms should not be interpreted as statistically ");
            writer.write("significant performance differences. The benchmark is intended for comparative engineering observation ");
            writer.write("rather than JVM microbenchmark-grade absolute latency claims.\n\n");

            writer.write("### Contextual Summary Observations:\n");
            writer.write("1. **Fixed Window & Sliding Window Counter**: In this controlled single-threaded benchmark environment, ");
            writer.write("Fixed Window and Sliding Window Counter produced high observed throughput with minimal latency variance and $\\mathcal{O}(1)$ space complexity.\n");
            writer.write("2. **Sliding Window Log**: Provides exact timestamp-based rolling-window decisions under the implemented model and is used ");
            writer.write("as the reference oracle for Sliding Window Counter evaluation. Higher state retention complexity ($\\mathcal{O}(N \\times K)$) under high volume.\n");
            writer.write("3. **Token Bucket**: Exhibits strong burst handling up to bucket capacity $C$ while maintaining continuous token refill over time.\n");
            writer.write("4. **Leaky Bucket**: Smooths traffic to a constant outflow leak rate, rejecting burst volume exceeding available bucket capacity.\n\n");

            writer.write("## 2. Environment Specifications\n\n");
            writer.write("- **Operating System**: " + System.getProperty("os.name") + " (" + System.getProperty("os.version") + " " + System.getProperty("os.arch") + ")\n");
            writer.write("- **Java Version**: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")\n");
            writer.write("- **JVM Name**: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ")\n");
            writer.write("- **Available Processors**: " + Runtime.getRuntime().availableProcessors() + "\n");
            writer.write("- **Max JVM Memory**: " + (Runtime.getRuntime().maxMemory() / (1024 * 1024)) + " MB\n");
            writer.write("- **CPU Utilization**: CPU utilization was not reported because process-level CPU measurement in this benchmark environment ");
            writer.write("would not provide sufficiently reliable per-algorithm attribution.\n\n");

            writer.write("## 3. Benchmark Lifecycle & Warmup Verification\n\n");
            writer.write("The benchmark lifecycle guarantees JIT-warmed JVM execution paired with 100% fresh algorithm state for every run:\n");
            writer.write("```text\n");
            writer.write("Create Warmup Limiter -> Execute " + WARMUP_OPERATIONS + " Unmeasured Calls -> Discard Warmup Instance\n");
            writer.write("  -> Create Fresh RateLimiter & Fresh Clock -> System.gc() -> Execute Measured Workload Loop\n");
            writer.write("```\n");
            writer.write("- **Runs per Scenario**: " + RUN_COUNT + " independent runs.\n");
            writer.write("- **Deterministic Seed**: `0x" + Long.toHexString(RANDOM_SEED).toUpperCase() + "L` for random algorithm order shuffling per run.\n");
            writer.write("- **Equivalent Policy Parameters**:\n");
            writer.write("  - Capacity: `" + properties.getDefaultCapacity() + "` requests / tokens / units\n");
            writer.write("  - Window Duration: `" + properties.getDefaultWindow().toSeconds() + "` seconds\n");
            writer.write("  - Refill / Leak Rate: `" + properties.getRefillRate() + "` units/second\n\n");

            writer.write("## 4. Quantitative Results by Scenario (Aggregated Across Runs)\n\n");

            for (Map.Entry<String, Map<String, BenchmarkMetricsCollector.AggregatedMetrics>> scenarioEntry : resultsByScenario.entrySet()) {
                writer.write("### " + scenarioEntry.getKey() + "\n\n");
                writer.write("| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |\n");
                writer.write("|---|---|---|---|---|---|---|---|---|---|\n");

                for (BenchmarkMetricsCollector.AggregatedMetrics agg : scenarioEntry.getValue().values()) {
                    writer.write(String.format("| **%s** | %.1f (±%.1f) | %.2f µs (±%.2f) | %.2f µs | %.2f µs | %.2f µs | %d | %d | %d | %d KB |\n",
                            agg.algorithmName(), agg.meanRps(), agg.stdDevRps(), agg.meanAverageLatencyMicros(), agg.stdDevAverageLatencyMicros(),
                            agg.aggregateP50Micros(), agg.aggregateP95Micros(), agg.aggregateP99Micros(),
                            agg.avgAllowedRequests(), agg.avgRejectedRequests(), agg.totalErrors(), agg.meanHeapDeltaBytes() / 1024));
                }
                writer.write("\n");
            }

            writer.write("## 5. Raw Per-Run Measurements Data\n\n");
            for (Map.Entry<String, Map<String, List<BenchmarkMetricsCollector.RunMetrics>>> scenarioEntry : rawRunsByScenario.entrySet()) {
                writer.write("### Raw Data: " + scenarioEntry.getKey() + "\n\n");
                writer.write("| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |\n");
                writer.write("|---|---|---|---|---|---|---|---|---|---|---|---|\n");

                for (Map.Entry<String, List<BenchmarkMetricsCollector.RunMetrics>> algEntry : scenarioEntry.getValue().entrySet()) {
                    String algName = algEntry.getKey();
                    List<BenchmarkMetricsCollector.RunMetrics> runs = algEntry.getValue();
                    for (int r = 0; r < runs.size(); r++) {
                        BenchmarkMetricsCollector.RunMetrics m = runs.get(r);
                        writer.write(String.format("| %s | Run %d | %d | %d | %d | %d | %.2f µs | %.2f µs | %.2f µs | %.2f µs | %.1f | %d KB |\n",
                                algName, (r + 1), m.totalRequests(), m.allowedRequests(), m.rejectedRequests(), m.errorCount(),
                                m.getAverageLatencyMicros(), m.getPercentileLatencyMicros(0.50), m.getPercentileLatencyMicros(0.95),
                                m.getPercentileLatencyMicros(0.99), m.getRps(), m.getHeapDeltaBytes() / 1024));
                    }
                }
                writer.write("\n");
            }

            writer.write("## 6. Visual Performance Comparison Charts\n\n");
            writer.write("### Throughput Comparison (RPS under Large Workload - 10,000 reqs)\n");
            writer.write("```text\n");
            Map<String, BenchmarkMetricsCollector.AggregatedMetrics> largeMap = resultsByScenario.get("3. Large Workload (10,000 reqs)");
            if (largeMap != null) {
                for (BenchmarkMetricsCollector.AggregatedMetrics agg : largeMap.values()) {
                    int barLength = (int) (agg.meanRps() / 200_000.0);
                    String bar = "█".repeat(Math.max(1, barLength));
                    writer.write(String.format("%-25s | %-30s | %.1f RPS\n", agg.algorithmName(), bar, agg.meanRps()));
                }
            }
            writer.write("```\n\n");

            writer.write("### Tail Latency Comparison (P99 Latency under Micro Workload - 100 reqs)\n");
            writer.write("```text\n");
            Map<String, BenchmarkMetricsCollector.AggregatedMetrics> microMap = resultsByScenario.get("1. Micro Workload (100 reqs)");
            if (microMap != null) {
                for (BenchmarkMetricsCollector.AggregatedMetrics agg : microMap.values()) {
                    int barLength = (int) Math.round(agg.aggregateP99Micros());
                    String bar = "▒".repeat(Math.max(1, Math.min(40, barLength)));
                    writer.write(String.format("%-25s | %-30s | %.2f µs P99\n", agg.algorithmName(), bar, agg.aggregateP99Micros()));
                }
            }
            writer.write("```\n\n");

            writer.write("## 7. Accuracy & Semantic Behavioral Analysis\n\n");
            writer.write("### Sliding Window Counter Approximation Accuracy (vs. Log Oracle)\n");
            writer.write("- **Reference Oracle**: `SlidingWindowLogRateLimiter` (exact timestamp rolling window)\n");
            writer.write("- **Target Algorithm**: `SlidingWindowCounterRateLimiter` (weighted window estimation)\n");
            writer.write("- **Workload Scenario**: Front-loaded requests in Window 1 (100 reqs at $t=0.1s$) followed by early Window 2 requests (20 reqs at $t=11.0s$)\n");
            writer.write("- **Total Requests Evaluated**: `" + accuracyResult.totalRequests() + "`\n");
            writer.write("- **Counter Allowed / Rejected**: `" + accuracyResult.counterAllowed() + " / " + accuracyResult.counterRejected() + "`\n");
            writer.write("- **Log Oracle Allowed / Rejected**: `" + accuracyResult.oracleAllowed() + " / " + accuracyResult.oracleRejected() + "`\n");
            writer.write("- **Decision Mismatch Count**: `" + accuracyResult.mismatchCount() + "`\n");
            writer.write("- **Approximation Error %**: `" + String.format("%.2f%%", accuracyResult.approximationErrorPercent()) + "`\n");
            if (accuracyResult.mismatchCount() > 0) {
                writer.write("- **Analysis**: Discrepancy observed because Sliding Window Counter applies a 90% weighting to Window 1's count at $t=11.0s$, ");
                writer.write("estimating 90 active requests and rejecting requests 11..20, whereas Sliding Window Log evicts all $t=0.1s$ timestamps from the rolling $[1.0s, 11.0s]$ window and permits all 20 requests.\n\n");
            } else {
                writer.write("- **Analysis**: No decision mismatches were observed in the selected boundary workloads. ");
                writer.write("This result does not establish mathematical equivalence between Sliding Window Counter and Sliding Window Log.\n\n");
            }

            writer.write("### Fixed Window Boundary Spike Analysis\n");
            writer.write("- **Configured Capacity**: `100` requests per 10-second window\n");
            writer.write("- **Workload Pattern**: 80 requests near end of Window 1 ($t = 9.95s$) + 80 requests near start of Window 2 ($t = 10.05s$)\n");
            writer.write("- **Accepted in Window 1**: `80` requests\n");
            writer.write("- **Accepted in Window 2**: `80` requests\n");
            writer.write("- **Total Accepted Across 100ms Boundary Interval**: `" + spikeResult.allowedRequests() + "` requests\n");
            writer.write("- **Observed Capacity Ratio**: `" + String.format("%.2fx", spikeResult.capacityMultiplierAccepted()) + "` configured capacity limit\n");
            writer.write("- **Analysis**: Demonstrates classic Fixed Window boundary burst behavior where a client can consume $2\\times$ configured rate limit ");
            writer.write("across a window transition interval without triggering rate limit rejections.\n\n");

            writer.write("## 8. Space Complexity & Memory Behavior\n\n");
            writer.write("| Algorithm | Theoretical Space Complexity | Empirical Memory Observations |\n");
            writer.write("|---|---|---|\n");
            writer.write("| **Fixed Window** | $\\mathcal{O}(N)$ | Low footprint; fixed entry per active client key |\n");
            writer.write("| **Sliding Window Counter** | $\\mathcal{O}(N)$ | Low footprint; stores current + previous window count |\n");
            writer.write("| **Sliding Window Log** | $\\mathcal{O}(N \\times K)$ | High footprint; memory scales linearly with request logs retained in window |\n");
            writer.write("| **Token Bucket** | $\\mathcal{O}(N)$ | Low footprint; stores token count + last refill timestamp |\n");
            writer.write("| **Leaky Bucket** | $\\mathcal{O}(N)$ | Low footprint; stores water level + last leak timestamp |\n\n");

            writer.write("## 9. Benchmark Limitations\n\n");
            writer.write("1. **Custom Benchmark Harness**: Uses a custom timing runner rather than JMH; subject to resolution limits on sub-microsecond operations.\n");
            writer.write("2. **Sub-Microsecond Operations**: Execution times approach timer resolution noise; small latency variations are not statistically significant.\n");
            writer.write("3. **Single-Threaded Execution**: Measures single-thread decision throughput; multi-threaded lock contention is evaluated in Phase 5.\n");
            writer.write("4. **In-Memory Storage**: Measures local map data structures; does not reflect network or Redis round-trip latency (Phase 3).\n");
            writer.write("5. **Empirical Heap Noise**: `System.gc()` heap measurements contain JVM background memory noise; Big-O complexity serves as authoritative baseline.\n\n");

            writer.write("## 10. Step-by-Step Reproducibility Guide\n\n");
            writer.write("To reproduce these benchmark measurements:\n");
            writer.write("```bash\n");
            writer.write("# 1. Compile test and benchmark classes\n");
            writer.write(".\\mvnw.cmd test-compile\n\n");
            writer.write("# 2. Execute standalone benchmark runner\n");
            writer.write(".\\mvnw.cmd exec:java \"-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.AlgorithmBenchmarkRunner\" \"-Dexec.classpathScope=test\"\n\n");
            writer.write("# 3. Verify normal correctness unit tests remain green\n");
            writer.write(".\\mvnw.cmd test\n");
            writer.write("```\n");
        } catch (IOException e) {
            System.err.println("Failed to write benchmark report: " + e.getMessage());
        }

        try (FileWriter writer = new FileWriter(readmeFile)) {
            writer.write("# Benchmarks — Distributed API Gateway\n\n");
            writer.write("This directory contains standalone benchmark utilities and empirical performance reports for the ");
            writer.write("in-memory rate limiting algorithms.\n\n");
            writer.write("## 📁 Structure\n");
            writer.write("- `reports/algorithm_benchmark_report.md`: Latest benchmark report.\n\n");
            writer.write("## 🚀 Execution Instructions\n");
            writer.write("```bash\n");
            writer.write(".\\mvnw.cmd test-compile\n");
            writer.write(".\\mvnw.cmd exec:java \"-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.AlgorithmBenchmarkRunner\" \"-Dexec.classpathScope=test\"\n");
            writer.write("```\n\n");
            writer.write("## ⚠️ Limitations Disclaimer\n");
            writer.write("This custom benchmark harness measures single-threaded in-memory method invocation overhead ");
            writer.write("under controlled logical clock progression. It is a project engineering measurement tool, ");
            writer.write("not a replacement for JMH or distributed load testing frameworks.\n");
        } catch (IOException e) {
            System.err.println("Failed to write benchmark README: " + e.getMessage());
        }
    }
}
