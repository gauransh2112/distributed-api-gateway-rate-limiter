# Contention Benchmark Report — In-Memory Rate Limiters

Subject under measurement: `ConcurrentHashMap.compute()`

Generated: 2026-09-19T10:42:39.032638365Z

> **Scope of these numbers.** They describe this workload on this machine only. They are an engineering measurement, not a general performance claim, and they do not establish that any mechanism is universally faster or slower.

---

## Environment

| Property | Value |
|---|---|
| Java version | 21.0.10 |
| JVM | OpenJDK 64-Bit Server VM 21.0.10+7-Ubuntu-124.04 |
| OS | Linux 6.18.44-fc-v37 (amd64) |
| Available processors | 4 |
| Max heap | 3422 MB |
| Redis | not used — all measured algorithms are in-memory |

## Method

| Parameter | Value |
|---|---|
| Thread counts | [8, 32, 64] |
| Operations per thread | 10,000 |
| Warm-up operations per thread | 2,000 (discarded, separate limiter instance) |
| Runs per configuration | 3 |
| Key distributions | same-key (1 shared key); multi-key (1 key per thread) |
| Clock strategy | fixed clock — time does not advance during a run |
| Capacity | 1,000,000,000 (every request admitted, so every thread takes the state-writing path) |
| Start coordination | ready latch + start latch; timer starts after all workers park |
| Latency measurement | `System.nanoTime()` around each `allowRequest` call |

## Results

### FIXED_WINDOW

| Scenario | Threads | Total ops | Throughput (ops/s) | StdDev (ops/s) | Avg latency (us) | P50 (us) | P95 (us) | P99 (us) | Allowed | Rejected | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Same-key (1 key) | 8 | 80,000 | 2,984,353 | 661,888 | 1.94 | 0.50 | 2.02 | 3.75 | 80,000 | 0 | 0 |
| Same-key (1 key) | 32 | 320,000 | 3,627,498 | 168,724 | 5.33 | 0.49 | 1.34 | 2.65 | 320,000 | 0 | 0 |
| Same-key (1 key) | 64 | 640,000 | 3,432,501 | 88,082 | 11.27 | 0.53 | 1.25 | 2.46 | 640,000 | 0 | 0 |
| Multi-key (1 key per thread) | 8 | 80,000 | 14,534,110 | 753,049 | 0.19 | 0.08 | 0.18 | 1.60 | 80,000 | 0 | 0 |
| Multi-key (1 key per thread) | 32 | 320,000 | 16,090,914 | 5,400,604 | 0.30 | 0.08 | 0.34 | 1.65 | 320,000 | 0 | 0 |
| Multi-key (1 key per thread) | 64 | 640,000 | 13,832,671 | 2,637,874 | 1.34 | 0.09 | 0.44 | 1.75 | 640,000 | 0 | 0 |

### SLIDING_WINDOW_COUNTER

| Scenario | Threads | Total ops | Throughput (ops/s) | StdDev (ops/s) | Avg latency (us) | P50 (us) | P95 (us) | P99 (us) | Allowed | Rejected | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Same-key (1 key) | 8 | 80,000 | 1,935,170 | 429,988 | 3.07 | 0.69 | 2.39 | 5.86 | 80,000 | 0 | 0 |
| Same-key (1 key) | 32 | 320,000 | 2,332,201 | 446,619 | 9.62 | 0.68 | 2.06 | 4.27 | 320,000 | 0 | 0 |
| Same-key (1 key) | 64 | 640,000 | 2,433,360 | 403,343 | 18.19 | 0.71 | 1.92 | 3.81 | 640,000 | 0 | 0 |
| Multi-key (1 key per thread) | 8 | 80,000 | 10,177,957 | 1,002,250 | 0.40 | 0.10 | 0.29 | 1.69 | 80,000 | 0 | 0 |
| Multi-key (1 key per thread) | 32 | 320,000 | 10,948,455 | 2,425,546 | 0.89 | 0.10 | 0.47 | 2.36 | 320,000 | 0 | 0 |
| Multi-key (1 key per thread) | 64 | 640,000 | 10,905,182 | 233,416 | 1.19 | 0.10 | 0.65 | 2.56 | 640,000 | 0 | 0 |

### SLIDING_WINDOW_LOG

| Scenario | Threads | Total ops | Throughput (ops/s) | StdDev (ops/s) | Avg latency (us) | P50 (us) | P95 (us) | P99 (us) | Allowed | Rejected | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Same-key (1 key) | 8 | 80,000 | 1,530,349 | 254,061 | 4.30 | 0.90 | 2.65 | 6.53 | 80,000 | 0 | 0 |
| Same-key (1 key) | 32 | 320,000 | 1,883,025 | 70,030 | 12.93 | 0.87 | 2.48 | 5.62 | 320,000 | 0 | 0 |
| Same-key (1 key) | 64 | 640,000 | 1,760,076 | 127,912 | 26.65 | 0.88 | 2.46 | 5.37 | 640,000 | 0 | 0 |
| Multi-key (1 key per thread) | 8 | 80,000 | 5,999,269 | 202,853 | 0.85 | 0.10 | 0.47 | 0.85 | 80,000 | 0 | 0 |
| Multi-key (1 key per thread) | 32 | 320,000 | 8,414,849 | 762,615 | 1.51 | 0.11 | 0.59 | 1.64 | 320,000 | 0 | 0 |
| Multi-key (1 key per thread) | 64 | 640,000 | 7,711,305 | 758,299 | 1.48 | 0.11 | 0.64 | 2.51 | 640,000 | 0 | 0 |

### TOKEN_BUCKET

| Scenario | Threads | Total ops | Throughput (ops/s) | StdDev (ops/s) | Avg latency (us) | P50 (us) | P95 (us) | P99 (us) | Allowed | Rejected | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Same-key (1 key) | 8 | 80,000 | 1,857,415 | 225,408 | 3.62 | 0.77 | 2.22 | 4.46 | 80,000 | 0 | 0 |
| Same-key (1 key) | 32 | 320,000 | 1,874,038 | 51,921 | 12.48 | 0.79 | 2.25 | 4.99 | 320,000 | 0 | 0 |
| Same-key (1 key) | 64 | 640,000 | 1,880,582 | 69,277 | 25.42 | 0.78 | 2.28 | 5.29 | 640,000 | 0 | 0 |
| Multi-key (1 key per thread) | 8 | 80,000 | 6,743,181 | 639,640 | 0.52 | 0.10 | 0.42 | 2.10 | 80,000 | 0 | 0 |
| Multi-key (1 key per thread) | 32 | 320,000 | 9,062,209 | 2,124,616 | 1.72 | 0.11 | 0.79 | 2.91 | 320,000 | 0 | 0 |
| Multi-key (1 key per thread) | 64 | 640,000 | 10,378,879 | 1,101,682 | 0.99 | 0.10 | 0.72 | 2.24 | 640,000 | 0 | 0 |

### LEAKY_BUCKET

| Scenario | Threads | Total ops | Throughput (ops/s) | StdDev (ops/s) | Avg latency (us) | P50 (us) | P95 (us) | P99 (us) | Allowed | Rejected | Errors |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| Same-key (1 key) | 8 | 80,000 | 1,548,965 | 203,317 | 4.44 | 0.85 | 2.49 | 5.89 | 80,000 | 0 | 0 |
| Same-key (1 key) | 32 | 320,000 | 1,735,806 | 152,131 | 13.66 | 0.77 | 2.53 | 6.07 | 320,000 | 0 | 0 |
| Same-key (1 key) | 64 | 640,000 | 1,852,299 | 70,488 | 25.72 | 0.80 | 2.28 | 5.34 | 640,000 | 0 | 0 |
| Multi-key (1 key per thread) | 8 | 80,000 | 7,648,776 | 999,655 | 0.48 | 0.10 | 0.42 | 1.80 | 80,000 | 0 | 0 |
| Multi-key (1 key per thread) | 32 | 320,000 | 9,868,713 | 1,279,594 | 1.14 | 0.10 | 0.62 | 2.56 | 320,000 | 0 | 0 |
| Multi-key (1 key per thread) | 64 | 640,000 | 9,744,605 | 1,672,141 | 1.20 | 0.10 | 0.85 | 2.60 | 640,000 | 0 | 0 |

## Limitations

1. **Per-operation timing overhead.** Two `System.nanoTime()` calls wrap each operation. At these latencies that overhead is a material fraction of the measurement. It is constant across configurations, so comparisons remain meaningful while absolute values are inflated.
2. **Fixed clock.** No window rolls, no refill and no leak occurs during a run. This isolates the concurrency mechanism but does not represent production time progression.
3. **Sliding Window Log accumulates state.** With a fixed clock nothing expires, so its timestamp deque grows for the whole run. Its numbers therefore include growth and allocation costs the other algorithms do not incur.
4. **Shared machine.** Measurements were taken in a containerised environment without CPU pinning or isolation. Run-to-run variance is reported as standard deviation.
5. **Not JMH.** No fork isolation, no dead-code-elimination guards, no blackholes. This is a focused engineering experiment, not a microbenchmark-grade harness.
6. **One implementation only.** No competing implementation was built, so these numbers describe current behaviour and do not compare mechanisms against each other.
