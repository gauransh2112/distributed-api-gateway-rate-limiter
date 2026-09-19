package com.gauransh.gateway.ratelimiter.benchmark;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.FixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.leakybucket.LeakyBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.SlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.TokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterAlgorithm;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Multi-threaded contention benchmark for the in-memory rate limiting algorithms.
 *
 * <p><strong>This is measurement infrastructure, not a correctness test.</strong> Correctness is
 * owned by the test suite; this runner only observes how the existing implementation behaves under
 * concurrent load. It never modifies production code and never asserts anything.</p>
 *
 * <h2>Why this exists separately from {@link AlgorithmBenchmarkRunner}</h2>
 *
 * <p>The Sprint 8 harness measures a <em>sequential timeline</em>: a pre-generated array of request
 * contexts replayed by one thread while a logical clock advances between requests. Its own report
 * states the gap this runner fills — <em>"Single-Threaded Execution: measures single-thread decision
 * throughput; multi-threaded lock contention is evaluated in Phase 5."</em></p>
 *
 * <p>Contention measurement needs the opposite execution model: many threads striking the same
 * instant simultaneously. Those models cannot share a driver without distorting one of them, so
 * this runner is separate. It does reuse {@link BenchmarkMetricsCollector} unchanged — that class is
 * pure statistics with no threading assumptions.</p>
 *
 * <h2>Subject under measurement</h2>
 *
 * <p>{@code ConcurrentHashMap.compute()}, the mechanism every algorithm uses to make its per-key
 * state transition atomic. The question is how throughput and latency behave as threads contend on
 * one key versus spreading across independent keys.</p>
 *
 * <h2>Method</h2>
 *
 * <ul>
 *   <li><strong>Fixed clock.</strong> Time does not advance during a run, so no window rolls, no
 *       tokens refill and no water leaks. Every request performs the identical state transition,
 *       which isolates the concurrency mechanism from time-dependent branching.</li>
 *   <li><strong>Large capacity.</strong> Every request is admitted, so every thread takes the
 *       heavier "allowed" path that actually writes state.</li>
 *   <li><strong>Pre-built contexts.</strong> Each worker reuses one {@link RateLimitContext}, so the
 *       measured region excludes context construction (which performs a deep header copy).</li>
 *   <li><strong>Coordinated start.</strong> Workers park on a start latch after signalling
 *       readiness; the timer starts only once every worker has arrived, so thread creation and
 *       scheduling setup fall outside the measured region. No {@code Thread.sleep} is used for
 *       coordination.</li>
 *   <li><strong>Warm-up.</strong> Each configuration runs a discarded concurrent warm-up pass on a
 *       separate limiter instance so the JIT has compiled the concurrent path before measurement.</li>
 *   <li><strong>Repetition.</strong> Every configuration runs {@value #RUNS_PER_CONFIG} times;
 *       results are reported as mean and standard deviation across runs.</li>
 * </ul>
 *
 * <h2>Execution</h2>
 *
 * <pre>
 * mvn test-compile
 * mvn exec:java -Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.ContentionBenchmarkRunner -Dexec.classpathScope=test
 * </pre>
 */
public final class ContentionBenchmarkRunner {

    /** Thread counts sampled, chosen to straddle typical core counts. */
    private static final int[] THREAD_COUNTS = {8, 32, 64};

    private static final int OPS_PER_THREAD = 10_000;
    private static final int WARMUP_OPS_PER_THREAD = 2_000;
    private static final int RUNS_PER_CONFIG = 3;

    /** Capacity high enough that no request is rejected, keeping every thread on the write path. */
    private static final long CAPACITY = 1_000_000_000L;

    private static final Instant BASE_TIME = Instant.parse("2026-09-19T12:00:00.000Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(BASE_TIME, ZoneOffset.UTC);

    private static final Path REPORT_PATH = Path.of("benchmarks", "reports", "contention_benchmark_report.md");

    private ContentionBenchmarkRunner() {
    }

    /** How request keys are distributed across worker threads. */
    private enum KeyDistribution {
        /** Every thread targets one shared key — maximum same-key contention. */
        SAME_KEY("Same-key (1 key)"),
        /** Every thread targets its own key — independent keys, ideally parallel. */
        MULTI_KEY("Multi-key (1 key per thread)");

        private final String label;

        KeyDistribution(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Contention benchmark - subject: ConcurrentHashMap.compute()");
        System.out.printf("threads=%s  opsPerThread=%d  runsPerConfig=%d%n",
                java.util.Arrays.toString(THREAD_COUNTS), OPS_PER_THREAD, RUNS_PER_CONFIG);

        Map<String, List<BenchmarkMetricsCollector.AggregatedMetrics>> resultsByAlgorithm = new LinkedHashMap<>();

        for (RateLimiterAlgorithm algorithm : measuredAlgorithms()) {
            List<BenchmarkMetricsCollector.AggregatedMetrics> results = new ArrayList<>();
            for (KeyDistribution distribution : KeyDistribution.values()) {
                for (int threadCount : THREAD_COUNTS) {
                    BenchmarkMetricsCollector.RunMetrics[] runs =
                            new BenchmarkMetricsCollector.RunMetrics[RUNS_PER_CONFIG];
                    for (int run = 0; run < RUNS_PER_CONFIG; run++) {
                        runs[run] = executeRun(algorithm, distribution, threadCount);
                    }
                    String scenario = distribution.label() + " @ " + threadCount + " threads";
                    results.add(BenchmarkMetricsCollector.aggregate(algorithm.name(), scenario, runs));
                    System.out.printf("  %-24s %-34s done%n", algorithm.name(), scenario);
                }
            }
            resultsByAlgorithm.put(algorithm.name(), results);
        }

        writeReport(resultsByAlgorithm);
        System.out.println("Report written to " + REPORT_PATH);
    }

    private static RateLimiterAlgorithm[] measuredAlgorithms() {
        return new RateLimiterAlgorithm[]{
                RateLimiterAlgorithm.FIXED_WINDOW,
                RateLimiterAlgorithm.SLIDING_WINDOW_COUNTER,
                RateLimiterAlgorithm.SLIDING_WINDOW_LOG,
                RateLimiterAlgorithm.TOKEN_BUCKET,
                RateLimiterAlgorithm.LEAKY_BUCKET
        };
    }

    /**
     * Executes one measured run: warm-up, coordinated concurrent burst, metric collection.
     */
    private static BenchmarkMetricsCollector.RunMetrics executeRun(
            RateLimiterAlgorithm algorithm,
            KeyDistribution distribution,
            int threadCount) throws Exception {

        RateLimiterProperties properties = benchmarkProperties();

        // Warm-up on a throwaway instance so the JIT compiles the concurrent path before measuring.
        drive(createLimiter(algorithm, properties), distribution, threadCount, WARMUP_OPS_PER_THREAD);

        RateLimiter measuredLimiter = createLimiter(algorithm, properties);

        System.gc();
        long baselineHeapBytes = usedHeapBytes();

        DriveResult result = drive(measuredLimiter, distribution, threadCount, OPS_PER_THREAD);

        long finalHeapBytes = usedHeapBytes();

        return new BenchmarkMetricsCollector.RunMetrics(
                threadCount * OPS_PER_THREAD,
                result.allowed(),
                result.rejected(),
                result.errors(),
                result.wallClockNanos(),
                result.latenciesNanos(),
                baselineHeapBytes,
                finalHeapBytes
        );
    }

    /**
     * Releases {@code threadCount} workers simultaneously against {@code limiter} and returns the
     * merged measurements.
     *
     * <p>The measured region begins only after every worker has signalled readiness, so thread
     * creation and pool warm-up are excluded. Each worker times its own operations and writes into
     * its own array, so workers never share a counter during measurement.</p>
     */
    private static DriveResult drive(
            RateLimiter limiter,
            KeyDistribution distribution,
            int threadCount,
            int opsPerThread) throws Exception {

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<WorkerResult>> futures = new ArrayList<>(threadCount);

        try {
            for (int t = 0; t < threadCount; t++) {
                final RateLimitContext context = contextFor(distribution, t);
                futures.add(executor.submit(() -> {
                    long[] latencies = new long[opsPerThread];
                    int allowed = 0;
                    int rejected = 0;
                    int errors = 0;

                    readyLatch.countDown();
                    startLatch.await();

                    for (int i = 0; i < opsPerThread; i++) {
                        long startNanos = System.nanoTime();
                        try {
                            RateLimitDecision decision = limiter.allowRequest(context);
                            if (decision.allowed()) {
                                allowed++;
                            } else {
                                rejected++;
                            }
                        } catch (RuntimeException e) {
                            errors++;
                        }
                        latencies[i] = System.nanoTime() - startNanos;
                    }
                    return new WorkerResult(latencies, allowed, rejected, errors);
                }));
            }

            if (!readyLatch.await(60, TimeUnit.SECONDS)) {
                throw new IllegalStateException("workers failed to reach the start barrier");
            }

            long wallStartNanos = System.nanoTime();
            startLatch.countDown();

            List<WorkerResult> workerResults = new ArrayList<>(threadCount);
            for (Future<WorkerResult> future : futures) {
                workerResults.add(future.get(120, TimeUnit.SECONDS));
            }
            long wallClockNanos = System.nanoTime() - wallStartNanos;

            return merge(workerResults, threadCount, opsPerThread, wallClockNanos);
        } finally {
            executor.shutdownNow();
        }
    }

    private static DriveResult merge(List<WorkerResult> workerResults, int threadCount, int opsPerThread,
                                     long wallClockNanos) {
        long[] merged = new long[threadCount * opsPerThread];
        int index = 0;
        int allowed = 0;
        int rejected = 0;
        int errors = 0;

        for (WorkerResult worker : workerResults) {
            System.arraycopy(worker.latenciesNanos(), 0, merged, index, worker.latenciesNanos().length);
            index += worker.latenciesNanos().length;
            allowed += worker.allowed();
            rejected += worker.rejected();
            errors += worker.errors();
        }
        return new DriveResult(merged, allowed, rejected, errors, wallClockNanos);
    }

    private static RateLimitContext contextFor(KeyDistribution distribution, int threadIndex) {
        String clientId = distribution == KeyDistribution.SAME_KEY
                ? "contention-shared-key"
                : "contention-key-" + threadIndex;
        return new RateLimitContext(
                clientId,
                "/api/v1/benchmark",
                "GET",
                BASE_TIME,
                "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId))
        );
    }

    private static RateLimiterProperties benchmarkProperties() {
        RateLimiterProperties properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setDefaultWindow(Duration.ofMinutes(10));
        properties.setRefillRate(CAPACITY);
        return properties;
    }

    private static RateLimiter createLimiter(RateLimiterAlgorithm algorithm, RateLimiterProperties properties) {
        return switch (algorithm) {
            case FIXED_WINDOW -> new FixedWindowRateLimiter(properties, FIXED_CLOCK);
            case SLIDING_WINDOW_COUNTER -> new SlidingWindowCounterRateLimiter(properties, FIXED_CLOCK);
            case SLIDING_WINDOW_LOG -> new SlidingWindowLogRateLimiter(properties, FIXED_CLOCK);
            case TOKEN_BUCKET -> new TokenBucketRateLimiter(properties, FIXED_CLOCK);
            case LEAKY_BUCKET -> new LeakyBucketRateLimiter(properties, FIXED_CLOCK);
            default -> throw new IllegalArgumentException("Unsupported benchmark algorithm: " + algorithm);
        };
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private record WorkerResult(long[] latenciesNanos, int allowed, int rejected, int errors) {}

    private record DriveResult(long[] latenciesNanos, int allowed, int rejected, int errors, long wallClockNanos) {}

    // ---------------------------------------------------------------- reporting

    private static void writeReport(Map<String, List<BenchmarkMetricsCollector.AggregatedMetrics>> resultsByAlgorithm)
            throws IOException {
        Files.createDirectories(REPORT_PATH.getParent());
        try (Writer writer = Files.newBufferedWriter(REPORT_PATH, StandardCharsets.UTF_8)) {
            writer.write("# Contention Benchmark Report — In-Memory Rate Limiters\n\n");
            writer.write("Subject under measurement: `ConcurrentHashMap.compute()`\n\n");
            writer.write("Generated: " + DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + "\n\n");

            writer.write("> **Scope of these numbers.** They describe this workload on this machine only. ");
            writer.write("They are an engineering measurement, not a general performance claim, and they do ");
            writer.write("not establish that any mechanism is universally faster or slower.\n\n---\n\n");

            writeEnvironment(writer);
            writeMethod(writer);

            writer.write("## Results\n\n");
            for (Map.Entry<String, List<BenchmarkMetricsCollector.AggregatedMetrics>> entry
                    : resultsByAlgorithm.entrySet()) {
                writer.write("### " + entry.getKey() + "\n\n");
                writer.write("| Scenario | Threads | Total ops | Throughput (ops/s) | StdDev (ops/s) "
                        + "| Avg latency (us) | P50 (us) | P95 (us) | P99 (us) | Allowed | Rejected | Errors |\n");
                writer.write("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|\n");
                for (BenchmarkMetricsCollector.AggregatedMetrics metrics : entry.getValue()) {
                    String scenario = metrics.scenarioName();
                    int threads = Integer.parseInt(scenario.replaceAll(".*@ (\\d+) threads", "$1"));
                    writer.write(String.format(
                            "| %s | %d | %,d | %,.0f | %,.0f | %.2f | %.2f | %.2f | %.2f | %,d | %,d | %d |%n",
                            scenario.replaceAll(" @ \\d+ threads", ""),
                            threads,
                            metrics.totalRequestsPerRun(),
                            metrics.meanRps(),
                            metrics.stdDevRps(),
                            metrics.meanAverageLatencyMicros(),
                            metrics.aggregateP50Micros(),
                            metrics.aggregateP95Micros(),
                            metrics.aggregateP99Micros(),
                            metrics.avgAllowedRequests(),
                            metrics.avgRejectedRequests(),
                            metrics.totalErrors()));
                }
                writer.write("\n");
            }

            writeLimitations(writer);
        }
    }

    private static void writeEnvironment(Writer writer) throws IOException {
        Runtime runtime = Runtime.getRuntime();
        writer.write("## Environment\n\n");
        writer.write("| Property | Value |\n|---|---|\n");
        writer.write("| Java version | " + System.getProperty("java.version") + " |\n");
        writer.write("| JVM | " + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.vm.version") + " |\n");
        writer.write("| OS | " + System.getProperty("os.name") + " "
                + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ") |\n");
        writer.write("| Available processors | " + runtime.availableProcessors() + " |\n");
        writer.write("| Max heap | " + (runtime.maxMemory() / (1024 * 1024)) + " MB |\n");
        writer.write("| Redis | not used — all measured algorithms are in-memory |\n\n");
    }

    private static void writeMethod(Writer writer) throws IOException {
        writer.write("## Method\n\n");
        writer.write("| Parameter | Value |\n|---|---|\n");
        writer.write("| Thread counts | " + java.util.Arrays.toString(THREAD_COUNTS) + " |\n");
        writer.write("| Operations per thread | " + String.format("%,d", OPS_PER_THREAD) + " |\n");
        writer.write("| Warm-up operations per thread | " + String.format("%,d", WARMUP_OPS_PER_THREAD)
                + " (discarded, separate limiter instance) |\n");
        writer.write("| Runs per configuration | " + RUNS_PER_CONFIG + " |\n");
        writer.write("| Key distributions | same-key (1 shared key); multi-key (1 key per thread) |\n");
        writer.write("| Clock strategy | fixed clock — time does not advance during a run |\n");
        writer.write("| Capacity | " + String.format("%,d", CAPACITY)
                + " (every request admitted, so every thread takes the state-writing path) |\n");
        writer.write("| Start coordination | ready latch + start latch; timer starts after all workers park |\n");
        writer.write("| Latency measurement | `System.nanoTime()` around each `allowRequest` call |\n\n");
    }

    private static void writeLimitations(Writer writer) throws IOException {
        writer.write("## Limitations\n\n");
        writer.write("1. **Per-operation timing overhead.** Two `System.nanoTime()` calls wrap each operation. "
                + "At these latencies that overhead is a material fraction of the measurement. It is constant "
                + "across configurations, so comparisons remain meaningful while absolute values are inflated.\n");
        writer.write("2. **Fixed clock.** No window rolls, no refill and no leak occurs during a run. This "
                + "isolates the concurrency mechanism but does not represent production time progression.\n");
        writer.write("3. **Sliding Window Log accumulates state.** With a fixed clock nothing expires, so its "
                + "timestamp deque grows for the whole run. Its numbers therefore include growth and allocation "
                + "costs the other algorithms do not incur.\n");
        writer.write("4. **Shared machine.** Measurements were taken in a containerised environment without "
                + "CPU pinning or isolation. Run-to-run variance is reported as standard deviation.\n");
        writer.write("5. **Not JMH.** No fork isolation, no dead-code-elimination guards, no blackholes. "
                + "This is a focused engineering experiment, not a microbenchmark-grade harness.\n");
        writer.write("6. **One implementation only.** No competing implementation was built, so these numbers "
                + "describe current behaviour and do not compare mechanisms against each other.\n");
    }
}
