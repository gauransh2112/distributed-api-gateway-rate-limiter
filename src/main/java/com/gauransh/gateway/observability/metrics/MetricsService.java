package com.gauransh.gateway.observability.metrics;

import com.gauransh.gateway.observability.model.GatewayMetricsResponse;
import com.gauransh.gateway.observability.model.JvmMetricsResponse;
import com.gauransh.gateway.observability.model.RateLimiterMetricsResponse;
import com.gauransh.gateway.observability.model.RedisMetricsResponse;
import com.gauransh.gateway.observability.model.TrafficStatsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/**
 * Core observability metrics engine accumulating operational telemetry across the gateway.
 *
 * <p>Designed for thread-safe, non-blocking metrics publication on the request path
 * using LongAdder primitives and bounded-memory latency reservoirs.</p>
 */
@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final long startTimeMs = System.currentTimeMillis();

    // Gateway HTTP Traffic Counters
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder successfulRequests = new LongAdder();
    private final LongAdder failedRequests = new LongAdder();
    private final LatencyReservoir gatewayLatencyReservoir = new LatencyReservoir(4096);
    private final RollingTrafficWindow rollingTrafficWindow = new RollingTrafficWindow();

    // Rate Limiter Telemetry
    private final LongAdder allowedRateLimitRequests = new LongAdder();
    private final LongAdder blockedRateLimitRequests = new LongAdder();
    private final LongAdder failedRateLimitRequests = new LongAdder();
    private final LatencyReservoir rateLimitLatencyReservoir = new LatencyReservoir(2048);
    private final AtomicReference<String> activeAlgorithm = new AtomicReference<>("TOKEN_BUCKET");
    private final ConcurrentHashMap<String, AlgorithmCounters> algorithmBreakdown = new ConcurrentHashMap<>();

    // Redis Command & Execution Counters
    private final LongAdder redisCommandsExecuted = new LongAdder();
    private final LongAdder redisLuaExecutions = new LongAdder();
    private final LatencyReservoir redisLatencyReservoir = new LatencyReservoir(2048);

    private final RedisConnectionFactory redisConnectionFactory;

    public MetricsService(@Autowired(required = false) RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * Records completion of an HTTP request through the gateway.
     *
     * @param statusCode HTTP status code returned
     * @param latency duration taken to process the request
     */
    public void recordHttpRequest(int statusCode, Duration latency) {
        totalRequests.increment();
        double latencyMs = latency.toNanos() / 1_000_000.0;
        gatewayLatencyReservoir.record(latencyMs);

        boolean success = statusCode >= 200 && statusCode < 400;
        if (success) {
            successfulRequests.increment();
        } else {
            failedRequests.increment();
        }

        rollingTrafficWindow.record(success, latencyMs);
    }

    /**
     * Records an evaluation decision from the rate limiter.
     *
     * @param algorithm rate limiting algorithm name
     * @param allowed whether request was admitted
     * @param latency duration taken for the evaluation
     */
    public void recordRateLimitDecision(String algorithm, boolean allowed, Duration latency) {
        double latencyMs = latency.toNanos() / 1_000_000.0;
        rateLimitLatencyReservoir.record(latencyMs);
        redisLatencyReservoir.record(latencyMs);

        redisCommandsExecuted.increment();
        redisLuaExecutions.increment();

        if (algorithm != null && !algorithm.isBlank()) {
            activeAlgorithm.set(algorithm);
            AlgorithmCounters counters = algorithmBreakdown.computeIfAbsent(algorithm, k -> new AlgorithmCounters());
            if (allowed) {
                counters.allowed.increment();
            } else {
                counters.blocked.increment();
            }
        }

        if (allowed) {
            allowedRateLimitRequests.increment();
        } else {
            blockedRateLimitRequests.increment();
        }
    }

    /**
     * Records a rate limiter evaluation failure (e.g. Redis unavailable).
     *
     * @param algorithm rate limiting algorithm name
     * @param latency duration before failure surfaced
     */
    public void recordRateLimitFailure(String algorithm, Duration latency) {
        failedRateLimitRequests.increment();
        double latencyMs = latency.toNanos() / 1_000_000.0;
        rateLimitLatencyReservoir.record(latencyMs);

        if (algorithm != null && !algorithm.isBlank()) {
            AlgorithmCounters counters = algorithmBreakdown.computeIfAbsent(algorithm, k -> new AlgorithmCounters());
            counters.failed.increment();
        }
    }

    /**
     * Constructs {@link GatewayMetricsResponse} for {@code GET /api/v1/monitoring/metrics}.
     */
    public GatewayMetricsResponse getGatewayMetrics() {
        long total = totalRequests.sum();
        long success = successfulRequests.sum();
        long errors = failedRequests.sum();

        long uptimeSec = Math.max(1, (System.currentTimeMillis() - startTimeMs) / 1000L);
        double rps = Math.round(((double) total / uptimeSec) * 100.0) / 100.0;

        double successRate = total > 0 ? Math.round(((double) success / total * 100.0) * 100.0) / 100.0 : 100.0;
        double errorRate = total > 0 ? Math.round(((double) errors / total * 100.0) * 100.0) / 100.0 : 0.0;
        double avgLatency = gatewayLatencyReservoir.getMean();

        return new GatewayMetricsResponse(total, rps, successRate, errorRate, avgLatency);
    }

    /**
     * Constructs {@link JvmMetricsResponse} for {@code GET /api/v1/monitoring/jvm}.
     */
    public JvmMetricsResponse getJvmMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long heapUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long heapMaxMb = runtime.maxMemory() / (1024 * 1024);

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int activeThreads = threadBean.getThreadCount();

        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuUsage = 0.0;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            double load = sunOsBean.getProcessCpuLoad();
            if (load >= 0.0) {
                cpuUsage = Math.round(load * 10000.0) / 100.0;
            }
        }

        long uptimeSeconds = (System.currentTimeMillis() - startTimeMs) / 1000L;
        return new JvmMetricsResponse(heapUsedMb, heapMaxMb, cpuUsage, activeThreads, uptimeSeconds);
    }

    /**
     * Constructs {@link RedisMetricsResponse} for {@code GET /api/v1/monitoring/redis}.
     */
    public RedisMetricsResponse getRedisMetrics() {
        String status = "DOWN";
        long connections = 0;
        long totalCommands = redisCommandsExecuted.sum();
        long luaExecutions = redisLuaExecutions.sum();
        double avgLatency = redisLatencyReservoir.getMean();

        if (redisConnectionFactory != null) {
            long pingStart = System.nanoTime();
            try (RedisConnection connection = redisConnectionFactory.getConnection()) {
                String pingResult = connection.ping();
                if ("PONG".equalsIgnoreCase(pingResult)) {
                    status = "UP";
                    double pingMs = (System.nanoTime() - pingStart) / 1_000_000.0;
                    redisLatencyReservoir.record(pingMs);
                    avgLatency = redisLatencyReservoir.getMean();

                    try {
                        Properties info = connection.serverCommands().info("clients");
                        if (info != null && info.containsKey("connected_clients")) {
                            connections = Long.parseLong(info.getProperty("connected_clients"));
                        }
                    } catch (Exception ignored) {
                        // Ignore INFO parsing failure in constrained testing environments
                    }
                }
            } catch (Exception e) {
                status = "DOWN";
                log.debug("Redis metrics probe failed: {}", e.getMessage());
            }
        }

        return new RedisMetricsResponse(status, avgLatency, connections, totalCommands, luaExecutions);
    }

    /**
     * Constructs {@link TrafficStatsResponse} for {@code GET /api/v1/monitoring/traffic}.
     */
    public TrafficStatsResponse getTrafficStats(String periodStr) {
        Duration duration = parsePeriod(periodStr);
        RollingTrafficWindow.TrafficStats stats = rollingTrafficWindow.aggregate(duration);
        return new TrafficStatsResponse(
                stats.requests(),
                stats.successful(),
                stats.failed(),
                stats.averageLatencyMs()
        );
    }

    /**
     * Constructs {@link RateLimiterMetricsResponse} for {@code GET /api/v1/monitoring/ratelimiter}.
     */
    public RateLimiterMetricsResponse getRateLimiterMetrics() {
        long allowed = allowedRateLimitRequests.sum();
        long blocked = blockedRateLimitRequests.sum();
        String algorithm = activeAlgorithm.get();
        double avgDecisionLatency = rateLimitLatencyReservoir.getMean();

        return new RateLimiterMetricsResponse(allowed, blocked, algorithm, avgDecisionLatency);
    }

    public LatencyReservoir getGatewayLatencyReservoir() {
        return gatewayLatencyReservoir;
    }

    public LatencyReservoir getRateLimitLatencyReservoir() {
        return rateLimitLatencyReservoir;
    }

    public ConcurrentHashMap<String, AlgorithmCounters> getAlgorithmBreakdown() {
        return algorithmBreakdown;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    private Duration parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            return Duration.ofMinutes(1);
        }
        return switch (period.toLowerCase().trim()) {
            case "5m" -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "1h" -> Duration.ofHours(1);
            default -> Duration.ofMinutes(1);
        };
    }

    public static final class AlgorithmCounters {
        public final LongAdder allowed = new LongAdder();
        public final LongAdder blocked = new LongAdder();
        public final LongAdder failed = new LongAdder();
    }
}
