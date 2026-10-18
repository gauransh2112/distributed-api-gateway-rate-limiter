# Benchmarks — Distributed API Gateway

This directory contains standalone benchmark utilities and empirical performance reports for the in-memory rate limiting algorithms.

## 📁 Structure
- `reports/algorithm_benchmark_report.md`: Single-threaded algorithm comparison (Sprint 8).
- `reports/contention_benchmark_report.md`: Multi-threaded contention measurement (Sprint 15).
- `reports/horizontal_scaling_benchmark_report.md`: Cluster scaling & horizontal load benchmark across 1, 3, and 5 nodes (Sprint 17).

## 🚀 Execution Instructions

### Algorithm Benchmark (Sprint 8)
```bash
.\mvnw.cmd test-compile
.\mvnw.cmd exec:java "-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.AlgorithmBenchmarkRunner" "-Dexec.classpathScope=test"
```

## 🧵 Contention Benchmark (Sprint 15)

Measures how the existing `ConcurrentHashMap.compute()` design behaves under concurrent load:
same-key versus multi-key distributions at 8, 32 and 64 threads across all five algorithms.

```bash
.\mvnw.cmd test-compile
.\mvnw.cmd exec:java "-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.ContentionBenchmarkRunner" "-Dexec.classpathScope=test"
```

## 🌐 Horizontal Scaling Benchmark (Sprint 17)

Measures multi-gateway coordination, load distribution, and shared-quota enforcement across 1, 3, and 5 gateway instances backed by a shared Redis container.

### 1. Start Clustered Runtime (High-Capacity Overlay)
```bash
docker compose -f docker/docker-compose.cluster.yml -f docker/docker-compose.5nodes.yml -f docker/docker-compose.scaling-capacity.yml up -d
```

### 2. Execute Performance Benchmark Across All Topologies
```bash
.\mvnw.cmd test-compile
.\mvnw.cmd exec:java "-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.ClusterScalingBenchmarkRunner" "-Dexec.classpathScope=test" "-Dexec.args=--topology=all --requests=300 --concurrency=30 --warmup=30"
```

### 3. Verify Shared-Quota Enforcement (Canonical Capacity 5)
Reconcile without the scaling capacity overlay, then run with zero warm-up:
```bash
docker compose -f docker/docker-compose.cluster.yml -f docker/docker-compose.5nodes.yml up -d
.\mvnw.cmd exec:java "-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.ClusterScalingBenchmarkRunner" "-Dexec.classpathScope=test" "-Dexec.args=--topology=3 --requests=20 --concurrency=1 --warmup=0"
.\mvnw.cmd exec:java "-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.ClusterScalingBenchmarkRunner" "-Dexec.classpathScope=test" "-Dexec.args=--topology=5 --requests=20 --concurrency=1 --warmup=0"
```

## ⚠️ Limitations Disclaimer
The Sprint 8 harness measures single-threaded in-memory method invocation overhead under controlled logical clock progression. The Sprint 15 contention runner measures multi-threaded behaviour of the same algorithms. The Sprint 17 cluster scaling harness measures multi-gateway coordination and shared Redis state under co-located container execution on a single physical host.

All benchmarks are focused project engineering measurement tools, not a replacement for JMH or distributed load testing frameworks. Importantly:
- **Single-Host Boundary**: The Sprint 17 benchmark executed with all gateway instances, Redis, mock backend, and the test runner co-located on a single physical host. The experiment does **not** demonstrate multi-host horizontal throughput scalability.
- **Response Classification**: Because reverse-proxy request forwarding is scheduled for Sprint 19/20, requests admitted by the rate limiter return HTTP 404 with rate-limit headers and are classified as `ADMITTED_ROUTING_PENDING`. They demonstrate rate-limiter admission across nodes rather than application-routing success.
- **Latency Metrics**: Mean latency is omitted from runner summary output and recorded as unavailable in campaign logs; it is not estimated or derived from percentile values.
