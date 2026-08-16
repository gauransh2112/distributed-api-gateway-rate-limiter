# Algorithm Benchmark Report — In-Memory Rate Limiters

## 1. Executive Summary & Engineering Takeaways

This benchmark evaluates the performance, latency distribution, throughput (RPS), heap memory estimates, burst handling, and semantic accuracy across all five in-memory rate-limiting algorithms implemented in the **Distributed API Gateway** project.

> **Disclaimer**: Controlled project benchmark utility suitable for comparative engineering measurements, but not a replacement for a dedicated JVM microbenchmark framework such as JMH.
> **Sub-Microsecond Latency Precision Warning**: The measured operations are extremely short, approaching the resolution and overhead sensitivity of a custom `System.nanoTime()` benchmark. Therefore small latency differences between algorithms should not be interpreted as statistically significant performance differences. The benchmark is intended for comparative engineering observation rather than JVM microbenchmark-grade absolute latency claims.

### Contextual Summary Observations:
1. **Fixed Window & Sliding Window Counter**: In this controlled single-threaded benchmark environment, Fixed Window and Sliding Window Counter produced high observed throughput with minimal latency variance and $\mathcal{O}(1)$ space complexity.
2. **Sliding Window Log**: Provides exact timestamp-based rolling-window decisions under the implemented model and is used as the reference oracle for Sliding Window Counter evaluation. Higher state retention complexity ($\mathcal{O}(N \times K)$) under high volume.
3. **Token Bucket**: Exhibits strong burst handling up to bucket capacity $C$ while maintaining continuous token refill over time.
4. **Leaky Bucket**: Smooths traffic to a constant outflow leak rate, rejecting burst volume exceeding available bucket capacity.

## 2. Environment Specifications

- **Operating System**: Windows 11 (10.0 amd64)
- **Java Version**: 21.0.8 (Oracle Corporation)
- **JVM Name**: Java HotSpot(TM) 64-Bit Server VM (21.0.8+12-LTS-250)
- **Available Processors**: 16
- **Max JVM Memory**: 3944 MB
- **CPU Utilization**: CPU utilization was not reported because process-level CPU measurement in this benchmark environment would not provide sufficiently reliable per-algorithm attribution.

## 3. Benchmark Lifecycle & Warmup Verification

The benchmark lifecycle guarantees JIT-warmed JVM execution paired with 100% fresh algorithm state for every run:
```text
Create Warmup Limiter -> Execute 5000 Unmeasured Calls -> Discard Warmup Instance
  -> Create Fresh RateLimiter & Fresh Clock -> System.gc() -> Execute Measured Workload Loop
```
- **Runs per Scenario**: 3 independent runs.
- **Deterministic Seed**: `0x42L` for random algorithm order shuffling per run.
- **Equivalent Policy Parameters**:
  - Capacity: `100` requests / tokens / units
  - Window Duration: `10` seconds
  - Refill / Leak Rate: `10.0` units/second

## 4. Quantitative Results by Scenario (Aggregated Across Runs)

### 1. Micro Workload (100 reqs)

| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |
|---|---|---|---|---|---|---|---|---|---|
| **FIXED_WINDOW** | 579317.7 (±338158.3) | 1.64 µs (±1.54) | 0.30 µs | 0.90 µs | 30.30 µs | 100 | 0 | 0 | -60 KB |
| **SLIDING_WINDOW_COUNTER** | 661144.2 (±389530.2) | 1.25 µs (±1.06) | 0.30 µs | 1.00 µs | 6.70 µs | 100 | 0 | 0 | -54 KB |
| **SLIDING_WINDOW_LOG** | 433341.0 (±234042.8) | 2.15 µs (±1.26) | 0.30 µs | 1.90 µs | 25.70 µs | 100 | 0 | 0 | -52 KB |
| **TOKEN_BUCKET** | 612400.5 (±255324.6) | 1.31 µs (±0.86) | 0.30 µs | 1.00 µs | 21.30 µs | 100 | 0 | 0 | -381 KB |
| **LEAKY_BUCKET** | 558632.2 (±236408.6) | 1.20 µs (±0.65) | 0.30 µs | 0.60 µs | 25.60 µs | 100 | 0 | 0 | -54 KB |

### 2. Standard Workload (1,000 reqs)

| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |
|---|---|---|---|---|---|---|---|---|---|
| **FIXED_WINDOW** | 2203539.2 (±477272.1) | 0.31 µs (±0.09) | 0.20 µs | 0.20 µs | 0.30 µs | 101 | 899 | 0 | 0 KB |
| **SLIDING_WINDOW_COUNTER** | 3556618.8 (±1432479.7) | 0.23 µs (±0.12) | 0.10 µs | 0.20 µs | 0.30 µs | 100 | 900 | 0 | 0 KB |
| **SLIDING_WINDOW_LOG** | 3221451.5 (±1622847.3) | 0.26 µs (±0.11) | 0.20 µs | 0.20 µs | 0.60 µs | 100 | 900 | 0 | 0 KB |
| **TOKEN_BUCKET** | 2408571.2 (±858747.6) | 0.32 µs (±0.13) | 0.20 µs | 0.30 µs | 0.50 µs | 199 | 801 | 0 | 0 KB |
| **LEAKY_BUCKET** | 3252117.4 (±1017745.5) | 0.23 µs (±0.08) | 0.20 µs | 0.30 µs | 0.60 µs | 199 | 801 | 0 | -259 KB |

### 3. Large Workload (10,000 reqs)

| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |
|---|---|---|---|---|---|---|---|---|---|
| **FIXED_WINDOW** | 6214296.1 (±865235.9) | 0.10 µs (±0.01) | 0.10 µs | 0.20 µs | 0.20 µs | 1001 | 8999 | 0 | 0 KB |
| **SLIDING_WINDOW_COUNTER** | 5638624.0 (±543514.4) | 0.12 µs (±0.01) | 0.10 µs | 0.20 µs | 0.20 µs | 992 | 9008 | 0 | 0 KB |
| **SLIDING_WINDOW_LOG** | 4188692.3 (±1019990.7) | 0.19 µs (±0.08) | 0.10 µs | 0.40 µs | 0.70 µs | 1000 | 9000 | 0 | 0 KB |
| **TOKEN_BUCKET** | 5209811.8 (±139631.6) | 0.13 µs (±0.00) | 0.10 µs | 0.20 µs | 0.30 µs | 1099 | 8901 | 0 | 0 KB |
| **LEAKY_BUCKET** | 4272264.8 (±669713.2) | 0.16 µs (±0.03) | 0.10 µs | 0.30 µs | 0.30 µs | 1099 | 8901 | 0 | 0 KB |

### 4. Burst Level 1 (50 reqs, < Cap)

| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |
|---|---|---|---|---|---|---|---|---|---|
| **FIXED_WINDOW** | 664843.0 (±163216.6) | 1.47 µs (±0.40) | 0.20 µs | 0.60 µs | 59.60 µs | 50 | 0 | 0 | 0 KB |
| **SLIDING_WINDOW_COUNTER** | 801615.2 (±378102.5) | 0.86 µs (±0.11) | 0.10 µs | 0.30 µs | 38.50 µs | 50 | 0 | 0 | 0 KB |
| **SLIDING_WINDOW_LOG** | 393291.2 (±125014.1) | 2.64 µs (±0.94) | 0.20 µs | 4.00 µs | 75.80 µs | 50 | 0 | 0 | 0 KB |
| **TOKEN_BUCKET** | 608981.2 (±202749.5) | 1.63 µs (±0.70) | 0.20 µs | 0.60 µs | 54.30 µs | 50 | 0 | 0 | 0 KB |
| **LEAKY_BUCKET** | 332076.5 (±126388.5) | 2.59 µs (±1.71) | 0.20 µs | 1.40 µs | 86.10 µs | 50 | 0 | 0 | 0 KB |

### 5. Burst Level 2 (100 reqs, = Cap)

| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |
|---|---|---|---|---|---|---|---|---|---|
| **FIXED_WINDOW** | 1736758.1 (±591605.5) | 0.55 µs (±0.20) | 0.10 µs | 0.30 µs | 1.20 µs | 100 | 0 | 0 | 0 KB |
| **SLIDING_WINDOW_COUNTER** | 1156667.8 (±570094.6) | 0.91 µs (±0.35) | 0.20 µs | 0.40 µs | 3.80 µs | 100 | 0 | 0 | 0 KB |
| **SLIDING_WINDOW_LOG** | 881432.5 (±154175.5) | 1.08 µs (±0.21) | 0.20 µs | 0.80 µs | 44.60 µs | 100 | 0 | 0 | 0 KB |
| **TOKEN_BUCKET** | 1445755.7 (±654718.8) | 0.75 µs (±0.42) | 0.10 µs | 0.30 µs | 1.20 µs | 100 | 0 | 0 | 0 KB |
| **LEAKY_BUCKET** | 1046170.9 (±249737.5) | 0.91 µs (±0.26) | 0.20 µs | 0.30 µs | 1.60 µs | 100 | 0 | 0 | 0 KB |

### 6. Burst Level 3 (150 reqs, > Cap)

| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |
|---|---|---|---|---|---|---|---|---|---|
| **FIXED_WINDOW** | 2247820.4 (±670676.3) | 0.40 µs (±0.11) | 0.10 µs | 0.20 µs | 0.80 µs | 100 | 50 | 0 | 0 KB |
| **SLIDING_WINDOW_COUNTER** | 1421672.0 (±177385.7) | 0.61 µs (±0.09) | 0.20 µs | 0.30 µs | 1.00 µs | 100 | 50 | 0 | 0 KB |
| **SLIDING_WINDOW_LOG** | 1070462.4 (±509045.2) | 1.08 µs (±0.69) | 0.20 µs | 0.60 µs | 46.90 µs | 100 | 50 | 0 | 0 KB |
| **TOKEN_BUCKET** | 2078469.1 (±765757.3) | 0.47 µs (±0.19) | 0.10 µs | 0.20 µs | 0.60 µs | 100 | 50 | 0 | 0 KB |
| **LEAKY_BUCKET** | 1580836.8 (±250058.2) | 0.56 µs (±0.07) | 0.20 µs | 0.30 µs | 1.00 µs | 100 | 50 | 0 | 0 KB |

### 7. Burst Level 4 (500 reqs, >> Cap)

| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |
|---|---|---|---|---|---|---|---|---|---|
| **FIXED_WINDOW** | 3981289.1 (±1411599.6) | 0.22 µs (±0.09) | 0.10 µs | 0.20 µs | 0.30 µs | 100 | 400 | 0 | 0 KB |
| **SLIDING_WINDOW_COUNTER** | 4298173.1 (±1338051.6) | 0.21 µs (±0.07) | 0.10 µs | 0.20 µs | 0.50 µs | 100 | 400 | 0 | 0 KB |
| **SLIDING_WINDOW_LOG** | 3128835.0 (±621355.4) | 0.26 µs (±0.06) | 0.10 µs | 0.20 µs | 0.80 µs | 100 | 400 | 0 | 0 KB |
| **TOKEN_BUCKET** | 3649563.8 (±1140821.7) | 0.24 µs (±0.09) | 0.10 µs | 0.20 µs | 0.50 µs | 100 | 400 | 0 | 0 KB |
| **LEAKY_BUCKET** | 3109753.5 (±1539311.5) | 0.30 µs (±0.12) | 0.20 µs | 0.30 µs | 0.50 µs | 100 | 400 | 0 | 0 KB |

### 8. Recovery Workload (150 burst -> +5s -> 50 burst)

| Algorithm | Mean RPS | Avg Latency (µs) | P50 (µs) | P95 (µs) | P99 (µs) | Allowed | Rejected | Errors | Heap Delta (Est) |
|---|---|---|---|---|---|---|---|---|---|
| **FIXED_WINDOW** | 1361776.9 (±400889.0) | 0.40 µs (±0.22) | 0.10 µs | 0.20 µs | 0.40 µs | 100 | 100 | 0 | 0 KB |
| **SLIDING_WINDOW_COUNTER** | 1580734.0 (±813930.4) | 0.56 µs (±0.45) | 0.10 µs | 0.20 µs | 1.10 µs | 100 | 100 | 0 | 0 KB |
| **SLIDING_WINDOW_LOG** | 1363228.7 (±455169.3) | 0.53 µs (±0.14) | 0.20 µs | 0.30 µs | 1.90 µs | 100 | 100 | 0 | 0 KB |
| **TOKEN_BUCKET** | 1270094.3 (±324086.8) | 0.49 µs (±0.04) | 0.20 µs | 0.30 µs | 0.60 µs | 150 | 50 | 0 | 0 KB |
| **LEAKY_BUCKET** | 938715.8 (±403628.0) | 0.65 µs (±0.27) | 0.20 µs | 0.30 µs | 0.90 µs | 150 | 50 | 0 | 0 KB |

## 5. Raw Per-Run Measurements Data

### Raw Data: 1. Micro Workload (100 reqs)

| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FIXED_WINDOW | Run 1 | 100 | 100 | 0 | 0 | 3.39 µs | 0.40 µs | 2.00 µs | 104.20 µs | 193798.4 | -181 KB |
| FIXED_WINDOW | Run 2 | 100 | 100 | 0 | 0 | 0.97 µs | 0.20 µs | 0.40 µs | 8.30 µs | 718390.8 | 0 KB |
| FIXED_WINDOW | Run 3 | 100 | 100 | 0 | 0 | 0.55 µs | 0.30 µs | 0.40 µs | 1.20 µs | 825763.8 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 1 | 100 | 100 | 0 | 0 | 2.48 µs | 0.90 µs | 1.40 µs | 6.70 µs | 245158.1 | -164 KB |
| SLIDING_WINDOW_COUNTER | Run 2 | 100 | 100 | 0 | 0 | 0.62 µs | 0.20 µs | 0.30 µs | 1.10 µs | 1017294.0 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 3 | 100 | 100 | 0 | 0 | 0.66 µs | 0.20 µs | 0.30 µs | 0.90 µs | 720980.5 | 0 KB |
| SLIDING_WINDOW_LOG | Run 1 | 100 | 100 | 0 | 0 | 3.59 µs | 0.80 µs | 3.10 µs | 25.70 µs | 168605.6 | -161 KB |
| SLIDING_WINDOW_LOG | Run 2 | 100 | 100 | 0 | 0 | 1.27 µs | 0.30 µs | 1.90 µs | 12.10 µs | 612745.1 | 2 KB |
| SLIDING_WINDOW_LOG | Run 3 | 100 | 100 | 0 | 0 | 1.60 µs | 0.20 µs | 0.30 µs | 21.20 µs | 518672.2 | 0 KB |
| TOKEN_BUCKET | Run 1 | 100 | 100 | 0 | 0 | 2.22 µs | 0.90 µs | 1.20 µs | 21.30 µs | 354484.2 | -1145 KB |
| TOKEN_BUCKET | Run 2 | 100 | 100 | 0 | 0 | 1.21 µs | 0.30 µs | 0.40 µs | 9.20 µs | 617665.2 | 0 KB |
| TOKEN_BUCKET | Run 3 | 100 | 100 | 0 | 0 | 0.50 µs | 0.10 µs | 0.20 µs | 0.70 µs | 865051.9 | 0 KB |
| LEAKY_BUCKET | Run 1 | 100 | 100 | 0 | 0 | 1.83 µs | 0.50 µs | 0.60 µs | 25.60 µs | 310945.3 | -164 KB |
| LEAKY_BUCKET | Run 2 | 100 | 100 | 0 | 0 | 1.21 µs | 0.30 µs | 0.50 µs | 1.50 µs | 583090.4 | 0 KB |
| LEAKY_BUCKET | Run 3 | 100 | 100 | 0 | 0 | 0.54 µs | 0.20 µs | 0.40 µs | 1.20 µs | 781860.8 | 0 KB |

### Raw Data: 2. Standard Workload (1,000 reqs)

| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FIXED_WINDOW | Run 1 | 1000 | 101 | 899 | 0 | 0.34 µs | 0.20 µs | 0.20 µs | 0.40 µs | 1895375.3 | 0 KB |
| FIXED_WINDOW | Run 2 | 1000 | 101 | 899 | 0 | 0.39 µs | 0.20 µs | 0.20 µs | 0.30 µs | 1961938.4 | 0 KB |
| FIXED_WINDOW | Run 3 | 1000 | 101 | 899 | 0 | 0.21 µs | 0.20 µs | 0.20 µs | 0.20 µs | 2753304.0 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 1 | 1000 | 100 | 900 | 0 | 0.36 µs | 0.20 µs | 0.20 µs | 0.50 µs | 1913875.6 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 2 | 1000 | 100 | 900 | 0 | 0.19 µs | 0.10 µs | 0.10 µs | 0.20 µs | 4210526.3 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 3 | 1000 | 100 | 900 | 0 | 0.14 µs | 0.10 µs | 0.10 µs | 0.30 µs | 4545454.5 | 0 KB |
| SLIDING_WINDOW_LOG | Run 1 | 1000 | 100 | 900 | 0 | 0.13 µs | 0.10 µs | 0.10 µs | 0.20 µs | 5091649.7 | 0 KB |
| SLIDING_WINDOW_LOG | Run 2 | 1000 | 100 | 900 | 0 | 0.31 µs | 0.20 µs | 0.20 µs | 0.40 µs | 2388344.9 | 0 KB |
| SLIDING_WINDOW_LOG | Run 3 | 1000 | 100 | 900 | 0 | 0.33 µs | 0.20 µs | 0.30 µs | 1.00 µs | 2184360.0 | 0 KB |
| TOKEN_BUCKET | Run 1 | 1000 | 199 | 801 | 0 | 0.18 µs | 0.10 µs | 0.20 µs | 0.60 µs | 3385240.4 | 0 KB |
| TOKEN_BUCKET | Run 2 | 1000 | 199 | 801 | 0 | 0.36 µs | 0.20 µs | 0.30 µs | 0.30 µs | 2068680.2 | 0 KB |
| TOKEN_BUCKET | Run 3 | 1000 | 199 | 801 | 0 | 0.42 µs | 0.20 µs | 0.30 µs | 0.50 µs | 1771793.1 | 0 KB |
| LEAKY_BUCKET | Run 1 | 1000 | 199 | 801 | 0 | 0.16 µs | 0.10 µs | 0.20 µs | 0.20 µs | 4253509.1 | -778 KB |
| LEAKY_BUCKET | Run 2 | 1000 | 199 | 801 | 0 | 0.31 µs | 0.20 µs | 0.30 µs | 0.90 µs | 2218770.8 | 0 KB |
| LEAKY_BUCKET | Run 3 | 1000 | 199 | 801 | 0 | 0.21 µs | 0.20 µs | 0.20 µs | 0.70 µs | 3284072.2 | 0 KB |

### Raw Data: 3. Large Workload (10,000 reqs)

| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FIXED_WINDOW | Run 1 | 10000 | 1001 | 8999 | 0 | 0.10 µs | 0.10 µs | 0.10 µs | 0.20 µs | 5914360.1 | 0 KB |
| FIXED_WINDOW | Run 2 | 10000 | 1001 | 8999 | 0 | 0.11 µs | 0.10 µs | 0.20 µs | 0.20 µs | 5538938.7 | 0 KB |
| FIXED_WINDOW | Run 3 | 10000 | 1001 | 8999 | 0 | 0.08 µs | 0.10 µs | 0.20 µs | 0.20 µs | 7189589.5 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 1 | 10000 | 992 | 9008 | 0 | 0.11 µs | 0.10 µs | 0.10 µs | 0.20 µs | 6138358.6 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 2 | 10000 | 992 | 9008 | 0 | 0.11 µs | 0.10 µs | 0.20 µs | 0.20 µs | 5717552.9 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 3 | 10000 | 992 | 9008 | 0 | 0.13 µs | 0.10 µs | 0.20 µs | 0.30 µs | 5059960.5 | 0 KB |
| SLIDING_WINDOW_LOG | Run 1 | 10000 | 1000 | 9000 | 0 | 0.29 µs | 0.10 µs | 0.50 µs | 1.10 µs | 3014954.2 | 0 KB |
| SLIDING_WINDOW_LOG | Run 2 | 10000 | 1000 | 9000 | 0 | 0.14 µs | 0.10 µs | 0.20 µs | 0.30 µs | 4691091.6 | 0 KB |
| SLIDING_WINDOW_LOG | Run 3 | 10000 | 1000 | 9000 | 0 | 0.14 µs | 0.10 µs | 0.20 µs | 0.40 µs | 4860031.1 | 0 KB |
| TOKEN_BUCKET | Run 1 | 10000 | 1099 | 8901 | 0 | 0.13 µs | 0.10 µs | 0.20 µs | 0.30 µs | 5363080.6 | 0 KB |
| TOKEN_BUCKET | Run 2 | 10000 | 1099 | 8901 | 0 | 0.13 µs | 0.10 µs | 0.20 µs | 0.30 µs | 5176519.3 | 0 KB |
| TOKEN_BUCKET | Run 3 | 10000 | 1099 | 8901 | 0 | 0.13 µs | 0.10 µs | 0.20 µs | 0.30 µs | 5089835.6 | 0 KB |
| LEAKY_BUCKET | Run 1 | 10000 | 1099 | 8901 | 0 | 0.14 µs | 0.10 µs | 0.20 µs | 0.20 µs | 4694174.5 | 0 KB |
| LEAKY_BUCKET | Run 2 | 10000 | 1099 | 8901 | 0 | 0.19 µs | 0.20 µs | 0.30 µs | 0.60 µs | 3500052.5 | 0 KB |
| LEAKY_BUCKET | Run 3 | 10000 | 1099 | 8901 | 0 | 0.15 µs | 0.10 µs | 0.30 µs | 0.30 µs | 4622567.4 | 0 KB |

### Raw Data: 4. Burst Level 1 (50 reqs, < Cap)

| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FIXED_WINDOW | Run 1 | 50 | 50 | 0 | 0 | 1.14 µs | 0.20 µs | 0.70 µs | 46.90 µs | 801282.1 | 0 KB |
| FIXED_WINDOW | Run 2 | 50 | 50 | 0 | 0 | 1.92 µs | 0.20 µs | 0.90 µs | 84.80 µs | 484027.1 | 0 KB |
| FIXED_WINDOW | Run 3 | 50 | 50 | 0 | 0 | 1.36 µs | 0.20 µs | 0.50 µs | 59.60 µs | 709219.9 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 1 | 50 | 50 | 0 | 0 | 0.73 µs | 0.10 µs | 0.30 µs | 28.70 µs | 365497.1 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 2 | 50 | 50 | 0 | 0 | 0.93 µs | 0.20 µs | 0.30 µs | 38.50 µs | 1002004.0 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 3 | 50 | 50 | 0 | 0 | 0.91 µs | 0.10 µs | 0.40 µs | 39.80 µs | 1037344.4 | 0 KB |
| SLIDING_WINDOW_LOG | Run 1 | 50 | 50 | 0 | 0 | 3.70 µs | 0.20 µs | 4.00 µs | 119.40 µs | 261506.3 | 0 KB |
| SLIDING_WINDOW_LOG | Run 2 | 50 | 50 | 0 | 0 | 1.90 µs | 0.10 µs | 2.40 µs | 58.30 µs | 510204.1 | 0 KB |
| SLIDING_WINDOW_LOG | Run 3 | 50 | 50 | 0 | 0 | 2.33 µs | 0.20 µs | 4.10 µs | 75.80 µs | 408163.3 | 0 KB |
| TOKEN_BUCKET | Run 1 | 50 | 50 | 0 | 0 | 2.44 µs | 0.20 µs | 0.40 µs | 111.20 µs | 388500.4 | 0 KB |
| TOKEN_BUCKET | Run 2 | 50 | 50 | 0 | 0 | 1.19 µs | 0.10 µs | 0.60 µs | 51.10 µs | 787401.6 | 0 KB |
| TOKEN_BUCKET | Run 3 | 50 | 50 | 0 | 0 | 1.28 µs | 0.10 µs | 0.90 µs | 54.30 µs | 651041.7 | 0 KB |
| LEAKY_BUCKET | Run 1 | 50 | 50 | 0 | 0 | 1.23 µs | 0.10 µs | 0.30 µs | 54.00 µs | 316455.7 | 0 KB |
| LEAKY_BUCKET | Run 2 | 50 | 50 | 0 | 0 | 2.03 µs | 0.20 µs | 1.00 µs | 86.10 µs | 465549.3 | 0 KB |
| LEAKY_BUCKET | Run 3 | 50 | 50 | 0 | 0 | 4.51 µs | 0.20 µs | 1.70 µs | 205.50 µs | 214224.5 | 0 KB |

### Raw Data: 5. Burst Level 2 (100 reqs, = Cap)

| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FIXED_WINDOW | Run 1 | 100 | 100 | 0 | 0 | 0.38 µs | 0.10 µs | 0.20 µs | 0.40 µs | 2304147.5 | 0 KB |
| FIXED_WINDOW | Run 2 | 100 | 100 | 0 | 0 | 0.78 µs | 0.20 µs | 0.30 µs | 1.20 µs | 1123595.5 | 0 KB |
| FIXED_WINDOW | Run 3 | 100 | 100 | 0 | 0 | 0.51 µs | 0.10 µs | 0.30 µs | 0.80 µs | 1782531.2 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 1 | 100 | 100 | 0 | 0 | 0.50 µs | 0.10 µs | 0.30 µs | 3.80 µs | 1814882.0 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 2 | 100 | 100 | 0 | 0 | 1.12 µs | 0.20 µs | 0.40 µs | 0.90 µs | 819000.8 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 3 | 100 | 100 | 0 | 0 | 1.10 µs | 0.20 µs | 0.30 µs | 1.00 µs | 836120.4 | 0 KB |
| SLIDING_WINDOW_LOG | Run 1 | 100 | 100 | 0 | 0 | 0.92 µs | 0.20 µs | 0.50 µs | 23.40 µs | 1002004.0 | 0 KB |
| SLIDING_WINDOW_LOG | Run 2 | 100 | 100 | 0 | 0 | 1.32 µs | 0.20 µs | 0.80 µs | 49.80 µs | 707714.1 | 0 KB |
| SLIDING_WINDOW_LOG | Run 3 | 100 | 100 | 0 | 0 | 1.02 µs | 0.10 µs | 0.60 µs | 25.10 µs | 934579.4 | 0 KB |
| TOKEN_BUCKET | Run 1 | 100 | 100 | 0 | 0 | 0.44 µs | 0.10 µs | 0.20 µs | 1.20 µs | 2049180.3 | 0 KB |
| TOKEN_BUCKET | Run 2 | 100 | 100 | 0 | 0 | 1.24 µs | 0.20 µs | 0.50 µs | 0.70 µs | 749625.2 | 0 KB |
| TOKEN_BUCKET | Run 3 | 100 | 100 | 0 | 0 | 0.59 µs | 0.10 µs | 0.30 µs | 1.00 µs | 1538461.5 | 0 KB |
| LEAKY_BUCKET | Run 1 | 100 | 100 | 0 | 0 | 1.21 µs | 0.20 µs | 0.30 µs | 1.60 µs | 759301.4 | 0 KB |
| LEAKY_BUCKET | Run 2 | 100 | 100 | 0 | 0 | 0.78 µs | 0.20 µs | 0.30 µs | 0.70 µs | 1164144.4 | 0 KB |
| LEAKY_BUCKET | Run 3 | 100 | 100 | 0 | 0 | 0.75 µs | 0.20 µs | 0.30 µs | 0.80 µs | 1215066.8 | 0 KB |

### Raw Data: 6. Burst Level 3 (150 reqs, > Cap)

| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FIXED_WINDOW | Run 1 | 150 | 100 | 50 | 0 | 0.51 µs | 0.20 µs | 0.30 µs | 1.20 µs | 1732101.6 | 0 KB |
| FIXED_WINDOW | Run 2 | 150 | 100 | 50 | 0 | 0.29 µs | 0.10 µs | 0.10 µs | 0.30 µs | 3006012.0 | 0 KB |
| FIXED_WINDOW | Run 3 | 150 | 100 | 50 | 0 | 0.41 µs | 0.10 µs | 0.20 µs | 0.50 µs | 2005347.6 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 1 | 150 | 100 | 50 | 0 | 0.60 µs | 0.20 µs | 0.30 µs | 0.70 µs | 1431297.7 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 2 | 150 | 100 | 50 | 0 | 0.53 µs | 0.20 µs | 0.30 µs | 1.00 µs | 1594048.9 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 3 | 150 | 100 | 50 | 0 | 0.71 µs | 0.20 µs | 0.20 µs | 1.00 µs | 1239669.4 | 0 KB |
| SLIDING_WINDOW_LOG | Run 1 | 150 | 100 | 50 | 0 | 0.63 µs | 0.10 µs | 0.70 µs | 22.10 µs | 1494023.9 | 0 KB |
| SLIDING_WINDOW_LOG | Run 2 | 150 | 100 | 50 | 0 | 1.87 µs | 0.20 µs | 0.80 µs | 79.70 µs | 505731.6 | 0 KB |
| SLIDING_WINDOW_LOG | Run 3 | 150 | 100 | 50 | 0 | 0.72 µs | 0.20 µs | 0.50 µs | 13.00 µs | 1211631.7 | 0 KB |
| TOKEN_BUCKET | Run 1 | 150 | 100 | 50 | 0 | 0.67 µs | 0.20 µs | 0.30 µs | 0.90 µs | 1320422.5 | 0 KB |
| TOKEN_BUCKET | Run 2 | 150 | 100 | 50 | 0 | 0.42 µs | 0.10 µs | 0.20 µs | 0.50 µs | 2063273.7 | 0 KB |
| TOKEN_BUCKET | Run 3 | 150 | 100 | 50 | 0 | 0.31 µs | 0.10 µs | 0.10 µs | 0.60 µs | 2851711.0 | 0 KB |
| LEAKY_BUCKET | Run 1 | 150 | 100 | 50 | 0 | 0.58 µs | 0.20 µs | 0.30 µs | 0.60 µs | 1463414.6 | 0 KB |
| LEAKY_BUCKET | Run 2 | 150 | 100 | 50 | 0 | 0.48 µs | 0.10 µs | 0.20 µs | 0.70 µs | 1867995.0 | 0 KB |
| LEAKY_BUCKET | Run 3 | 150 | 100 | 50 | 0 | 0.63 µs | 0.20 µs | 0.40 µs | 1.20 µs | 1411100.7 | 0 KB |

### Raw Data: 7. Burst Level 4 (500 reqs, >> Cap)

| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FIXED_WINDOW | Run 1 | 500 | 100 | 400 | 0 | 0.33 µs | 0.20 µs | 0.20 µs | 0.60 µs | 2426006.8 | 0 KB |
| FIXED_WINDOW | Run 2 | 500 | 100 | 400 | 0 | 0.15 µs | 0.10 µs | 0.10 µs | 0.20 µs | 5181347.2 | 0 KB |
| FIXED_WINDOW | Run 3 | 500 | 100 | 400 | 0 | 0.19 µs | 0.10 µs | 0.20 µs | 0.20 µs | 4336513.4 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 1 | 500 | 100 | 400 | 0 | 0.14 µs | 0.10 µs | 0.10 µs | 0.20 µs | 5530973.5 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 2 | 500 | 100 | 400 | 0 | 0.19 µs | 0.10 µs | 0.10 µs | 0.70 µs | 4488330.3 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 3 | 500 | 100 | 400 | 0 | 0.29 µs | 0.10 µs | 0.20 µs | 0.60 µs | 2875215.6 | 0 KB |
| SLIDING_WINDOW_LOG | Run 1 | 500 | 100 | 400 | 0 | 0.21 µs | 0.10 µs | 0.20 µs | 0.60 µs | 3736920.8 | 0 KB |
| SLIDING_WINDOW_LOG | Run 2 | 500 | 100 | 400 | 0 | 0.32 µs | 0.20 µs | 0.20 µs | 1.10 µs | 2495010.0 | 0 KB |
| SLIDING_WINDOW_LOG | Run 3 | 500 | 100 | 400 | 0 | 0.25 µs | 0.10 µs | 0.20 µs | 0.40 µs | 3154574.1 | 0 KB |
| TOKEN_BUCKET | Run 1 | 500 | 100 | 400 | 0 | 0.35 µs | 0.20 µs | 0.20 µs | 0.60 µs | 2355157.8 | 0 KB |
| TOKEN_BUCKET | Run 2 | 500 | 100 | 400 | 0 | 0.18 µs | 0.10 µs | 0.10 µs | 0.40 µs | 4508566.3 | 0 KB |
| TOKEN_BUCKET | Run 3 | 500 | 100 | 400 | 0 | 0.20 µs | 0.10 µs | 0.10 µs | 0.40 µs | 4084967.3 | 0 KB |
| LEAKY_BUCKET | Run 1 | 500 | 100 | 400 | 0 | 0.34 µs | 0.20 µs | 0.20 µs | 0.40 µs | 2420135.5 | 0 KB |
| LEAKY_BUCKET | Run 2 | 500 | 100 | 400 | 0 | 0.40 µs | 0.20 µs | 0.30 µs | 0.60 µs | 2035830.6 | 0 KB |
| LEAKY_BUCKET | Run 3 | 500 | 100 | 400 | 0 | 0.17 µs | 0.10 µs | 0.10 µs | 0.30 µs | 4873294.3 | 0 KB |

### Raw Data: 8. Recovery Workload (150 burst -> +5s -> 50 burst)

| Algorithm | Run | Total Reqs | Allowed | Rejected | Errors | Avg Latency | P50 | P95 | P99 | RPS | Heap Delta |
|---|---|---|---|---|---|---|---|---|---|---|---|
| FIXED_WINDOW | Run 1 | 200 | 100 | 100 | 0 | 0.22 µs | 0.10 µs | 0.20 µs | 0.40 µs | 1231527.1 | 0 KB |
| FIXED_WINDOW | Run 2 | 200 | 100 | 100 | 0 | 0.33 µs | 0.10 µs | 0.20 µs | 0.40 µs | 1811594.2 | 0 KB |
| FIXED_WINDOW | Run 3 | 200 | 100 | 100 | 0 | 0.64 µs | 0.20 µs | 0.20 µs | 0.30 µs | 1042209.5 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 1 | 200 | 100 | 100 | 0 | 1.08 µs | 0.20 µs | 0.30 µs | 1.10 µs | 679578.7 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 2 | 200 | 100 | 100 | 0 | 0.23 µs | 0.10 µs | 0.20 µs | 0.40 µs | 2262443.4 | 0 KB |
| SLIDING_WINDOW_COUNTER | Run 3 | 200 | 100 | 100 | 0 | 0.37 µs | 0.10 µs | 0.20 µs | 0.60 µs | 1800180.0 | 0 KB |
| SLIDING_WINDOW_LOG | Run 1 | 200 | 100 | 100 | 0 | 0.59 µs | 0.20 µs | 0.40 µs | 1.90 µs | 1179941.0 | 0 KB |
| SLIDING_WINDOW_LOG | Run 2 | 200 | 100 | 100 | 0 | 0.62 µs | 0.20 µs | 0.30 µs | 1.10 µs | 1028277.6 | 0 KB |
| SLIDING_WINDOW_LOG | Run 3 | 200 | 100 | 100 | 0 | 0.36 µs | 0.10 µs | 0.20 µs | 0.60 µs | 1881467.5 | 0 KB |
| TOKEN_BUCKET | Run 1 | 200 | 150 | 50 | 0 | 0.44 µs | 0.10 µs | 0.20 µs | 0.60 µs | 907852.9 | 0 KB |
| TOKEN_BUCKET | Run 2 | 200 | 150 | 50 | 0 | 0.50 µs | 0.20 µs | 0.30 µs | 0.60 µs | 1369863.0 | 0 KB |
| TOKEN_BUCKET | Run 3 | 200 | 150 | 50 | 0 | 0.52 µs | 0.10 µs | 0.20 µs | 0.30 µs | 1532567.0 | 0 KB |
| LEAKY_BUCKET | Run 1 | 200 | 150 | 50 | 0 | 0.79 µs | 0.20 µs | 0.30 µs | 0.60 µs | 557413.6 | 0 KB |
| LEAKY_BUCKET | Run 2 | 200 | 150 | 50 | 0 | 0.82 µs | 0.20 µs | 0.40 µs | 0.90 µs | 897263.3 | 0 KB |
| LEAKY_BUCKET | Run 3 | 200 | 150 | 50 | 0 | 0.34 µs | 0.10 µs | 0.20 µs | 1.00 µs | 1361470.4 | 0 KB |

## 6. Visual Performance Comparison Charts

### Throughput Comparison (RPS under Large Workload - 10,000 reqs)
```text
FIXED_WINDOW              | ███████████████████████████████ | 6214296.1 RPS
SLIDING_WINDOW_COUNTER    | ████████████████████████████   | 5638624.0 RPS
SLIDING_WINDOW_LOG        | ████████████████████           | 4188692.3 RPS
TOKEN_BUCKET              | ██████████████████████████     | 5209811.8 RPS
LEAKY_BUCKET              | █████████████████████          | 4272264.8 RPS
```

### Tail Latency Comparison (P99 Latency under Micro Workload - 100 reqs)
```text
FIXED_WINDOW              | ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒ | 30.30 µs P99
SLIDING_WINDOW_COUNTER    | ▒▒▒▒▒▒▒                        | 6.70 µs P99
SLIDING_WINDOW_LOG        | ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒     | 25.70 µs P99
TOKEN_BUCKET              | ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒          | 21.30 µs P99
LEAKY_BUCKET              | ▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒▒     | 25.60 µs P99
```

## 7. Accuracy & Semantic Behavioral Analysis

### Sliding Window Counter Approximation Accuracy (vs. Log Oracle)
- **Reference Oracle**: `SlidingWindowLogRateLimiter` (exact timestamp rolling window)
- **Target Algorithm**: `SlidingWindowCounterRateLimiter` (weighted window estimation)
- **Workload Scenario**: Front-loaded requests in Window 1 (100 reqs at $t=0.1s$) followed by early Window 2 requests (20 reqs at $t=11.0s$)
- **Total Requests Evaluated**: `120`
- **Counter Allowed / Rejected**: `110 / 10`
- **Log Oracle Allowed / Rejected**: `120 / 0`
- **Decision Mismatch Count**: `10`
- **Approximation Error %**: `8.33%`
- **Analysis**: Discrepancy observed because Sliding Window Counter applies a 90% weighting to Window 1's count at $t=11.0s$, estimating 90 active requests and rejecting requests 11..20, whereas Sliding Window Log evicts all $t=0.1s$ timestamps from the rolling $[1.0s, 11.0s]$ window and permits all 20 requests.

### Fixed Window Boundary Spike Analysis
- **Configured Capacity**: `100` requests per 10-second window
- **Workload Pattern**: 80 requests near end of Window 1 ($t = 9.95s$) + 80 requests near start of Window 2 ($t = 10.05s$)
- **Accepted in Window 1**: `80` requests
- **Accepted in Window 2**: `80` requests
- **Total Accepted Across 100ms Boundary Interval**: `160` requests
- **Observed Capacity Ratio**: `1.60x` configured capacity limit
- **Analysis**: Demonstrates classic Fixed Window boundary burst behavior where a client can consume $2\times$ configured rate limit across a window transition interval without triggering rate limit rejections.

## 8. Space Complexity & Memory Behavior

| Algorithm | Theoretical Space Complexity | Empirical Memory Observations |
|---|---|---|
| **Fixed Window** | $\mathcal{O}(N)$ | Low footprint; fixed entry per active client key |
| **Sliding Window Counter** | $\mathcal{O}(N)$ | Low footprint; stores current + previous window count |
| **Sliding Window Log** | $\mathcal{O}(N \times K)$ | High footprint; memory scales linearly with request logs retained in window |
| **Token Bucket** | $\mathcal{O}(N)$ | Low footprint; stores token count + last refill timestamp |
| **Leaky Bucket** | $\mathcal{O}(N)$ | Low footprint; stores water level + last leak timestamp |

## 9. Benchmark Limitations

1. **Custom Benchmark Harness**: Uses a custom timing runner rather than JMH; subject to resolution limits on sub-microsecond operations.
2. **Sub-Microsecond Operations**: Execution times approach timer resolution noise; small latency variations are not statistically significant.
3. **Single-Threaded Execution**: Measures single-thread decision throughput; multi-threaded lock contention is evaluated in Phase 5.
4. **In-Memory Storage**: Measures local map data structures; does not reflect network or Redis round-trip latency (Phase 3).
5. **Empirical Heap Noise**: `System.gc()` heap measurements contain JVM background memory noise; Big-O complexity serves as authoritative baseline.

## 10. Step-by-Step Reproducibility Guide

To reproduce these benchmark measurements:
```bash
# 1. Compile test and benchmark classes
.\mvnw.cmd test-compile

# 2. Execute standalone benchmark runner
.\mvnw.cmd exec:java "-Dexec.mainClass=com.gauransh.gateway.ratelimiter.benchmark.AlgorithmBenchmarkRunner" "-Dexec.classpathScope=test"

# 3. Verify normal correctness unit tests remain green
.\mvnw.cmd test
```
