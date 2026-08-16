# Benchmarks — Distributed API Gateway

This directory contains standalone benchmark utilities and empirical performance reports for the in-memory rate limiting algorithms.

## 📁 Structure
- `reports/algorithm_benchmark_report.md`: Latest benchmark report.

## 🚀 Execution Instructions
```bash
.\mvnw.cmd test-compile
.\mvnw.cmd exec:java "-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.AlgorithmBenchmarkRunner" "-Dexec.classpathScope=test"
```

## ⚠️ Limitations Disclaimer
This custom benchmark harness measures single-threaded in-memory method invocation overhead under controlled logical clock progression. It is a project engineering measurement tool, not a replacement for JMH or distributed load testing frameworks.
