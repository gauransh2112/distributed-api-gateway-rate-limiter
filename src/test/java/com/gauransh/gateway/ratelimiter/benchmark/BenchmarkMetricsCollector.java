package com.gauransh.gateway.ratelimiter.benchmark;

import java.util.Arrays;
import java.util.Objects;

/**
 * Metric collector for rate limiter benchmark runs.
 *
 * <p>Captures zero-allocation primitive latency data, decision outcomes, execution duration,
 * and empirical JVM heap memory delta estimates. Calculates percentiles, RPS, and multi-run statistics.</p>
 */
public class BenchmarkMetricsCollector {

    /**
     * Metrics recorded for a single benchmark run.
     *
     * @param totalRequests total workload requests evaluated
     * @param allowedRequests number of allowed decisions
     * @param rejectedRequests number of rejected decisions
     * @param errorCount number of unexpected exceptions
     * @param totalDurationNanos total elapsed measurement duration in nanoseconds
     * @param rawLatenciesNanos primitive array of per-request nanosecond latencies
     * @param baselineHeapBytes JVM heap memory before workload execution
     * @param finalHeapBytes JVM heap memory after workload execution
     */
    public record RunMetrics(
            int totalRequests,
            int allowedRequests,
            int rejectedRequests,
            int errorCount,
            long totalDurationNanos,
            long[] rawLatenciesNanos,
            long baselineHeapBytes,
            long finalHeapBytes
    ) {
        public double getAverageLatencyMicros() {
            if (totalRequests == 0 || rawLatenciesNanos == null || rawLatenciesNanos.length == 0) {
                return 0.0;
            }
            long sum = 0L;
            for (long lat : rawLatenciesNanos) {
                sum += lat;
            }
            return (sum / (double) totalRequests) / 1000.0;
        }

        public double getPercentileLatencyMicros(double percentile) {
            if (rawLatenciesNanos == null || rawLatenciesNanos.length == 0) {
                return 0.0;
            }
            long[] sorted = Arrays.copyOf(rawLatenciesNanos, rawLatenciesNanos.length);
            Arrays.sort(sorted);
            int index = (int) Math.round(percentile * (sorted.length - 1));
            index = Math.max(0, Math.min(sorted.length - 1, index));
            return sorted[index] / 1000.0;
        }

        public double getRps() {
            if (totalDurationNanos == 0L) {
                return 0.0;
            }
            double seconds = totalDurationNanos / 1_000_000_000.0;
            return totalRequests / seconds;
        }

        public long getHeapDeltaBytes() {
            return finalHeapBytes - baselineHeapBytes;
        }
    }

    /**
     * Aggregated metrics across multiple independent benchmark runs.
     */
    public record AggregatedMetrics(
            String algorithmName,
            String scenarioName,
            int runCount,
            int totalRequestsPerRun,
            int avgAllowedRequests,
            int avgRejectedRequests,
            int totalErrors,
            double meanRps,
            double stdDevRps,
            double meanAverageLatencyMicros,
            double stdDevAverageLatencyMicros,
            double aggregateP50Micros,
            double aggregateP95Micros,
            double aggregateP99Micros,
            long meanHeapDeltaBytes
    ) {}

    /**
     * Aggregates multiple run metrics into a single summary statistical record.
     *
     * @param algorithmName name of algorithm
     * @param scenarioName name of scenario
     * @param runs list of individual run metrics
     * @return aggregated metrics summary
     */
    public static AggregatedMetrics aggregate(String algorithmName, String scenarioName, RunMetrics[] runs) {
        Objects.requireNonNull(runs, "runs must not be null");
        if (runs.length == 0) {
            throw new IllegalArgumentException("runs array must not be empty");
        }

        int runCount = runs.length;
        int reqsPerRun = runs[0].totalRequests();

        double sumRps = 0.0;
        double sumAvgLat = 0.0;
        long sumAllowed = 0L;
        long sumRejected = 0L;
        int totalErrors = 0;
        long sumHeapDelta = 0L;

        // Calculate total samples for combined quantile array
        int totalSamples = 0;
        for (RunMetrics run : runs) {
            if (run.rawLatenciesNanos() != null) {
                totalSamples += run.rawLatenciesNanos().length;
            }
        }

        long[] combinedLatencies = new long[totalSamples];
        int sampleIdx = 0;

        for (RunMetrics run : runs) {
            sumRps += run.getRps();
            sumAvgLat += run.getAverageLatencyMicros();
            sumAllowed += run.allowedRequests();
            sumRejected += run.rejectedRequests();
            totalErrors += run.errorCount();
            sumHeapDelta += run.getHeapDeltaBytes();

            if (run.rawLatenciesNanos() != null) {
                for (long lat : run.rawLatenciesNanos()) {
                    combinedLatencies[sampleIdx++] = lat;
                }
            }
        }

        double meanRps = sumRps / runCount;
        double meanAvgLat = sumAvgLat / runCount;
        int avgAllowed = (int) Math.round((double) sumAllowed / runCount);
        int avgRejected = (int) Math.round((double) sumRejected / runCount);
        long meanHeapDelta = sumHeapDelta / runCount;

        // Calculate standard deviations
        double sumSqDevRps = 0.0;
        double sumSqDevLat = 0.0;
        for (RunMetrics run : runs) {
            double devRps = run.getRps() - meanRps;
            sumSqDevRps += devRps * devRps;

            double devLat = run.getAverageLatencyMicros() - meanAvgLat;
            sumSqDevLat += devLat * devLat;
        }

        double stdDevRps = (runCount > 1) ? Math.sqrt(sumSqDevRps / (runCount - 1)) : 0.0;
        double stdDevLat = (runCount > 1) ? Math.sqrt(sumSqDevLat / (runCount - 1)) : 0.0;

        // Aggregate quantiles across combined samples
        Arrays.sort(combinedLatencies);
        double aggregateP50 = calculateQuantileMicros(combinedLatencies, 0.50);
        double aggregateP95 = calculateQuantileMicros(combinedLatencies, 0.95);
        double aggregateP99 = calculateQuantileMicros(combinedLatencies, 0.99);

        return new AggregatedMetrics(
                algorithmName,
                scenarioName,
                runCount,
                reqsPerRun,
                avgAllowed,
                avgRejected,
                totalErrors,
                meanRps,
                stdDevRps,
                meanAvgLat,
                stdDevLat,
                aggregateP50,
                aggregateP95,
                aggregateP99,
                meanHeapDelta
        );
    }

    private static double calculateQuantileMicros(long[] sortedNanos, double percentile) {
        if (sortedNanos == null || sortedNanos.length == 0) {
            return 0.0;
        }
        int index = (int) Math.round(percentile * (sortedNanos.length - 1));
        index = Math.max(0, Math.min(sortedNanos.length - 1, index));
        return sortedNanos[index] / 1000.0;
    }
}
