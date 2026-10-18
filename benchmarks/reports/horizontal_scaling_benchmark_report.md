# Horizontal Scaling Benchmark Report — Clustered Gateway Deployments

**Sprint:** 17 — Horizontal Scaling & Load Testing<br>
**Document Version:** 1.0<br>
**Status:** Complete (Empirical Campaign Findings)<br>
**Related Documents:**
- Cluster Deployment Guide: [../../docs/CLUSTER_DEPLOYMENT_GUIDE.md](../../docs/CLUSTER_DEPLOYMENT_GUIDE.md)
- Benchmarks Overview: [../README.md](../README.md)
- Development Playbook (Phase 6): [../../docs/ENGINEERING/Development_Playbook.md](../../docs/ENGINEERING/Development_Playbook.md)

---

## 1. Executive Summary & Engineering Findings

This report documents the empirical evaluation of the Distributed API Gateway when deployed as a multi-node cluster backed by a shared Redis instance. The campaign evaluated 1-node, 3-node, and 5-node gateway topologies under controlled concurrent workloads using a dedicated virtual-thread benchmark harness (`ClusterScalingBenchmarkRunner`).

### Key Verified Findings

1. **Functional Multi-Gateway Load Distribution**:
   Traffic directed round-robin to cluster endpoints was evenly distributed across nodes (100% on 1 node, 33.33% per node on 3 nodes, and 20.0% per node on 5 nodes). Zero request drops or connection resets occurred during normal operation.
2. **Atomic Shared-Quota Precision Across Nodes**:
   Under a canonical low-capacity policy (`capacity: 5`, single shared client identity, zero warm-up), requests rotating round-robin across nodes yielded exactly 5 admissions followed by 15 consecutive HTTP 429 rejections on both 3-node and 5-node clusters. Redis Lua script execution prevented any quota over-allocation or cross-node state leakage.
3. **Stateless Node Isolation & Recovery**:
   Terminating an individual gateway instance (`gateway-3`) caused immediate transport connection failures on its direct host port (`18083`), while surviving nodes (`gateway-1`, `gateway-2`) continued serving traffic and updating shared rate-limit counters without interruption. Upon container restart, the instance reached a healthy status in an observed measurement of 1.07 seconds.
4. **Resilience & `FAIL_CLOSED` Outage Policy**:
   Simulating a complete shared Redis outage caused all gateway instances to reject incoming requests with clean `HTTP 503 Service Unavailable` (`RATE_LIMIT_BACKEND_UNAVAILABLE`). No rate-limit headers were leaked, no application crashes occurred, and no HTTP 500 errors were returned, complying strictly with the `FAIL_CLOSED` policy defined in ADR-0016. Upon Redis restoration, gateways reconnected automatically within an observed measurement of 1.08 seconds.

> [!WARNING]
> ### Explicit Single-Host Limitation & Scope Boundaries
> All gateway containers (`gateway-1` through `gateway-5`), the shared Redis instance, the mock backend origin, and the client benchmark harness executed concurrently on a **single physical host** sharing common CPU, memory, OS kernel scheduling, and Docker bridge network resources.
>
> - **Observed Throughput Trend**: Average observed throughput declined as requests rotated across more nodes (704.3 RPS on 1 node -> 475.1 RPS on 3 nodes -> 358.9 RPS on 5 nodes), accompanied by higher latency percentiles.
> - **Causal Attribution Boundary**: Host-level resource contention, virtualization overhead, and context switching across multiple co-located containers may have contributed to this decline. However, container-level CPU, memory, and network utilization were not independently instrumented during the campaign; therefore, no definitive causal attribution (e.g. CPU scheduling, thread contention, cache thrashing, or Docker bridge multiplexing) is asserted.
> - **Scalability Conclusion**: This benchmark confirms **functional multi-gateway coordination and shared state consistency** under single-host conditions. It **does NOT demonstrate multi-host horizontal throughput scalability**. True horizontal scaling validation requires physically isolated multi-host infrastructure.

---

## 2. Test Environment & Clustered Topology

### Host Specifications

| Parameter | Specification |
|---|---|
| Host Operating System | Windows 11 Home (x86_64) |
| Java Runtime Environment | OpenJDK 64-Bit Server VM (build 21.0.8+12-LTS-250) |
| Container Engine | Docker Desktop / Docker Engine 28.0.1 |
| Gateway Image | `distributed-api-gateway:latest` (Alpine-based Eclipse Temurin JRE 21) |
| Redis Image | `redis:7-alpine` |
| Mock Backend Image | `kennethreitz/httpbin` |

### Clustered Deployment Topology

The clustered environment was instantiated using Docker Compose overlays:
- Base cluster configuration: `docker/docker-compose.cluster.yml` (3 gateway nodes, Redis, mock backend, network)
- 5-node topology overlay: `docker/docker-compose.5nodes.yml` (adds `gateway-4` and `gateway-5`)
- Capacity overlay: `docker/docker-compose.scaling-capacity.yml` (configures `capacity: 10000` for high-throughput testing)

```text
                       ┌─────────────┐ :18081
                       │  gateway-1  │──┐
                       ├─────────────┤ :18082
                       │  gateway-2  │──┤
                       ├─────────────┤ :18083
     Client Runner ───▶│  gateway-3  │──┼──▶  gateway-cluster-redis (:6379 internal)
                       ├─────────────┤ :18084
                       │  gateway-4  │──┤
                       ├─────────────┤ :18085
                       │  gateway-5  │──┘
                                         gateway-cluster-backend (:8080 internal, inert)
```

All containers were attached to a single user-defined bridge network (`gateway-net`). Gateway ports were mapped to host ports `18081` through `18085`.

---

## 3. Benchmark Methodology & Execution Parameters

### Benchmark Execution Harness

The experiment executed `ClusterScalingBenchmarkRunner`, a specialized Java 21 test harness utilizing virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) to maintain bounded concurrency without thread starvation.

### Experiment A: Horizontal Scaling Performance Workload
- **Workload Parameters**:
  - Target Path: `/api/v1/gateway/anything`
  - Total Measured Requests per Topology: `300`
  - Bounded Concurrency: `30` simultaneous in-flight workers
  - Warm-up Requests: `30` requests per topology using an isolated client ID suffix (`-warmup`), discarded from metrics
  - Repeated Runs: `3` independent runs per topology to capture variance
  - Rate-Limit Capacity: `10,000` requests/window (overlay active)

### Response Classification (`ADMITTED_ROUTING_PENDING`)

In all performance runs, the benchmark runner logged:
`Admitted (2xx/404+H): 0/300`

- **Meaning of `0`**: Exactly zero HTTP 2xx application responses were returned.
- **Meaning of `300`**: Exactly 300 requests passed through `RateLimitFilter`, evaluated Redis atomic counters, and received `HTTP 404 Not Found` with code `ROUTE-001` and valid rate-limiting headers (`X-RateLimit-Limit: 10000`, `X-RateLimit-Remaining`).
- **Semantic Classification**: These responses are formally classified as **`ADMITTED_ROUTING_PENDING`**. They confirm rate-limiter admission across the cluster, but do **not** represent successful application routing or backend forwarding (reverse-proxy routing to the mock backend is scheduled for Sprint 19/20).

### Status of Mean Latency

`BenchmarkMetricsCollector.RunMetrics` calculates mean latency internally, but `ClusterScalingBenchmarkRunner.toSummaryString()` omits it from formatted summary output. Because individual raw per-request latencies were not dumped to stdout, mean latency is formally recorded as **Unavailable in campaign logs**. It is not estimated or derived from percentile values.

---

## 4. Quantitative Results Across Topologies (Experiment A)

### Raw Per-Run Measurements

#### Run 1
- **1-Node Baseline** (`localhost:18081`):
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **553.4 RPS**
  - Latency: P50: 47.54 ms | P95: 87.90 ms | P99: 107.41 ms
- **3-Node Cluster** (`18081`–`18083`):
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **420.3 RPS**
  - Latency: P50: 64.28 ms | P95: 124.07 ms | P99: 148.83 ms
- **5-Node Cluster** (`18081`–`18085`):
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **335.5 RPS**
  - Latency: P50: 73.59 ms | P95: 175.96 ms | P99: 233.86 ms

#### Run 2
- **1-Node Baseline**:
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **682.4 RPS**
  - Latency: P50: 39.42 ms | P95: 71.26 ms | P99: 82.05 ms
- **3-Node Cluster**:
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **426.9 RPS**
  - Latency: P50: 61.05 ms | P95: 130.06 ms | P99: 157.17 ms
- **5-Node Cluster**:
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **400.6 RPS**
  - Latency: P50: 67.50 ms | P95: 138.11 ms | P99: 211.69 ms

#### Run 3
- **1-Node Baseline**:
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **877.1 RPS**
  - Latency: P50: 30.78 ms | P95: 51.18 ms | P99: 60.22 ms
- **3-Node Cluster**:
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **578.2 RPS**
  - Latency: P50: 46.64 ms | P95: 84.41 ms | P99: 124.50 ms
- **5-Node Cluster**:
  - Total Requests: 300 | Admitted (`ADMITTED_ROUTING_PENDING`): 300 | 429s: 0 | 503s: 0 | Errors: 0
  - Peak In-Flight: 30 | Throughput: **340.7 RPS**
  - Latency: P50: 71.50 ms | P95: 203.28 ms | P99: 311.58 ms

---

### Synthesis of Performance Metrics (Averaged across Runs 1–3)

| Topology | Target Endpoints | Throughput Range | Mean Throughput | Mean Latency | P50 Latency Range | P95 Latency Range | P99 Latency Range | Response Classification | Error Count |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1-Node** | `localhost:18081` | 553.4 – 877.1 RPS | **704.3 RPS** | *Unavailable* | 30.8 – 47.5 ms | 51.2 – 87.9 ms | 60.2 – 107.4 ms | 100% `ADMITTED_ROUTING_PENDING` | 0 |
| **3-Node** | `localhost:18081`..`18083` | 420.3 – 578.2 RPS | **475.1 RPS** | *Unavailable* | 46.6 – 64.3 ms | 84.4 – 130.1 ms | 124.5 – 157.2 ms | 100% `ADMITTED_ROUTING_PENDING` | 0 |
| **5-Node** | `localhost:18081`..`18085` | 335.5 – 400.6 RPS | **358.9 RPS** | *Unavailable* | 67.5 – 73.6 ms | 138.1 – 203.3 ms | 211.7 – 311.6 ms | 100% `ADMITTED_ROUTING_PENDING` | 0 |

### Load Distribution Verification
- **1-Node Topology**: `gateway-1` received 300 / 300 requests (**100.0%**).
- **3-Node Topology**: `gateway-1`, `gateway-2`, and `gateway-3` each received exactly 100 / 300 requests (**33.33%**).
- **5-Node Topology**: `gateway-1` through `gateway-5` each received exactly 60 / 300 requests (**20.0%**).

---

## 5. Shared-Quota Correctness (Experiment B)

Shared-quota precision was evaluated against the canonical low-capacity configuration without the scaling overlay (`capacity: 5`, `--warmup=0`, 20 sequential requests issued by a single client ID):

### 3-Node Cluster Execution
- **Workload**: 20 requests rotating round-robin across `gateway-1`, `gateway-2`, `gateway-3`.
- **Observed Result**: Exactly **5 admissions** (`ADMITTED_ROUTING_PENDING`), followed by exactly **15 consecutive HTTP 429 rejections** with `X-RateLimit-Remaining: 0`. Errors: 0.

### 5-Node Cluster Execution
- **Workload**: 20 requests rotating round-robin across `gateway-1` through `gateway-5`.
- **Observed Result**: Exactly **5 admissions** (`ADMITTED_ROUTING_PENDING`), followed by exactly **15 consecutive HTTP 429 rejections** with `X-RateLimit-Remaining: 0`. Errors: 0.

**Invariant Confirmed**: Regardless of node count or request distribution, the shared Redis counter enforces exact capacity bounds with zero quota leakage.

---

## 6. Resilience, Node Failure & Redis Outage Semantics

### Gateway Node Failure & Isolation (`gateway-3`)
1. **Pre-condition**: Cluster healthy; direct probe to `gateway-3` admitted (`HTTP 404`, `X-RateLimit-Remaining: 4`).
2. **Failure Injection**: `docker stop gateway-3` executed (completed in 551.0 ms).
3. **Transport Behavior**:
   - Direct requests to port `18083` failed immediately with TCP transport connection errors.
   - **No Transparent Failover**: The test client does not implement client-side retries or automatic failover redirection.
   - **Stateless Isolation**: Requests directed to surviving nodes (`gateway-1`, `gateway-2`) succeeded immediately. Shared rate-limit state decremented continuously from `remaining: 4` on node 1 to `remaining: 3` on node 2 without deadlock or state corruption.
4. **Recovery**: `docker start gateway-3` executed (took 323.9 ms). Container reached `healthy` in an observed measurement of **1.07 seconds** and resumed normal request processing.

### Shared Redis Outage & Failure Policy (`gateway-cluster-redis`)
1. **Pre-condition**: Actuator health reporting `UP` across all nodes.
2. **Failure Injection**: `docker stop gateway-cluster-redis` executed (completed in 377.9 ms).
3. **Outage Semantics (`FAIL_CLOSED`)**:
   - Actuator health immediately reported `DOWN` (`RedisConnectionFailureException: Unable to connect to Redis`).
   - Incoming requests received clean `HTTP/1.1 503 Service Unavailable`:
     ```json
     {"success":false,"data":null,"error":{"code":"RATE_LIMIT_BACKEND_UNAVAILABLE","message":"Rate limiting is temporarily unavailable. Please retry.","details":null}}
     ```
   - Zero application crashes, zero HTTP 500 errors, and zero rate-limit headers leaked, strictly adhering to ADR-0016.
4. **Recovery**: `docker start gateway-cluster-redis` executed (took 292.2 ms). Redis reached `healthy` in an observed measurement of **1.08 seconds**. Gateway instances automatically re-established Lettuce connections, `/actuator/health` returned `UP`, and normal request processing resumed.

---

## 7. Roadmap & Documentation Alignment

### Numbering & Phase Divergence
Documentation across the repository reflects differing numbering schemes:
- **Phase 6 vs. Sprint 17**: [Development_Playbook.md](../../docs/ENGINEERING/Development_Playbook.md) designates the clustered gateway deployment as **Phase 6**, whereas [CLUSTER_DEPLOYMENT_GUIDE.md](../../docs/CLUSTER_DEPLOYMENT_GUIDE.md) and active development track it under **Sprint 16** (cluster deployment) and **Sprint 17** (horizontal scaling & load testing).
- **Redis Setup Guide Divergence**: [REDIS_SETUP_GUIDE.md](../../docs/REDIS_SETUP_GUIDE.md) labels Sprint 17 as "Distributed Sliding Window Counter", whereas actual sprint progression assigned Sprint 17 to clustered horizontal scaling benchmarks.
- **Cluster Health Aggregation**: Deferred to **Phase 7 / Sprint 18** per [CLUSTER_DEPLOYMENT_GUIDE.md](../../docs/CLUSTER_DEPLOYMENT_GUIDE.md) Section 2.
- **Reverse Proxy Routing**: Scheduled for **Sprint 19/20** (Gateway Core & Request Pipeline).

---

## 8. Acceptance Criteria Verification Matrix

| Acceptance Criterion | Audited Verdict | Empirical Ground Truth & Evidence Citations |
| :--- | :--- | :--- |
| **AC-1: Single-Node Benchmark Baseline** | **PASSED** | 3 runs completed; average 704.3 RPS, P50 30.8–47.5 ms, 0 errors, 100% admitted on pending route. |
| **AC-2: Three-Node Multi-Gateway Coordination** | **PASSED (Functional)** | Exact 33.3% round-robin load distribution across 3 nodes; 0 errors; shared Redis state coordinated correctly under single-host conditions. |
| **AC-3: Five-Node Multi-Gateway Coordination** | **PASSED (Functional)** | Exact 20.0% round-robin load distribution across 5 nodes; 0 errors; shared Redis state coordinated correctly under single-host conditions. |
| **AC-4: Shared-Quota Precision (3 Nodes)** | **PASSED** | Exactly 5 admissions across 3 nodes followed by 15 consecutive HTTP 429 rejections; zero over-admission. |
| **AC-5: Shared-Quota Precision (5 Nodes)** | **PASSED** | Exactly 5 admissions across 5 nodes followed by 15 consecutive HTTP 429 rejections; zero over-admission. |
| **AC-6: Gateway Node Isolation & Recovery** | **PASSED** | `gateway-3` stopped; direct port failed; surviving nodes operated normally; node recovered in 1.07s test measurement. |
| **AC-7: Redis Failure Policy & Recovery** | **PASSED** | Redis stopped; gateways returned clean HTTP 503 (`FAIL_CLOSED`); Redis restored in 1.08s test measurement with automatic reconnection. |
| **AC-8: Zero Production Code Changes in Campaign** | **PASSED** | All campaign executions confined to test and Docker overlay scope; pre-existing files preserved without alteration. |
