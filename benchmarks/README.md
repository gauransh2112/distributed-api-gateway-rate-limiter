# Benchmarks — Distributed API Gateway

This directory contains standalone benchmark utilities and empirical performance reports for the in-memory rate limiting algorithms.

## 📁 Structure
- `reports/algorithm_benchmark_report.md`: Single-threaded algorithm comparison (Sprint 8).
- `reports/contention_benchmark_report.md`: Multi-threaded contention measurement (Sprint 15).

## 🚀 Execution Instructions
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

## ⚠️ Limitations Disclaimer
The Sprint 8 harness measures single-threaded in-memory method invocation overhead under controlled logical clock progression. The Sprint 15 contention runner measures multi-threaded behaviour of the same algorithms. Both are project engineering measurement tools, not a replacement for JMH or distributed load testing frameworks, and their results apply only to the workload and machine described in each report.
