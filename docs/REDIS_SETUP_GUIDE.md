# Redis Development Environment & Infrastructure Setup Guide

**Sprint:** 9 — Redis Setup  
**Version:** 1.0  
**Scope:** Development Infrastructure Setup Only  

---

## 1. Overview & Purpose

Sprint 9 establishes a reproducible Redis development environment and Spring Boot infrastructure connection layer for the Distributed API Gateway repository. 

> **Important**: Redis-backed storage and rate limiting are **not** implemented in Sprint 9. They begin in Sprint 10.

---

## 2. Prerequisites

- **Docker & Docker Compose**: Installed and running locally.
- **Java**: JDK 21.
- **Maven**: Bundled `mvnw` wrapper.

---

## 3. Redis Version & Infrastructure Decisions

- **Selected Docker Image**: `redis:7.2-alpine`
- **Version Decision Rationale**: Selected as the Sprint 9 local development Redis image after reviewing project requirements; the repository documentation does not prescribe an exact Redis image tag. The `7.2-alpine` image provides low-latency key-value performance, minimal security footprint (~30MB), and fast container boot times.
- **ADR Reference**: Governed under [`ADR-0011 — Docker Compose for Local Distributed Deployment`](file:///c:/Users/ASUS/OneDrive/Desktop/PROJECTS/Distributed_api_gateway/docs/ARCHITECTURE/architecture_design_record.md#L5683).

---

## 4. Docker Container Topology

The Redis container definition is located in [`docker/docker-compose.yml`](file:///c:/Users/ASUS/OneDrive/Desktop/PROJECTS/Distributed_api_gateway/docker/docker-compose.yml):

- **Service Name**: `redis`
- **Container Name**: `gateway-redis`
- **Port Mapping**: `6379:6379`
- **Command**: `redis-server --save "" --appendonly no` (in-memory mode for development)
- **Health Check**: Native command `redis-cli ping` (interval 5s, timeout 3s, retries 5)

---

## 5. Application Configuration Ownership

Redis connection properties are owned exclusively by Spring Boot's standard infrastructure namespace `spring.data.redis.*` in [`application.yml`](file:///c:/Users/ASUS/OneDrive/Desktop/PROJECTS/Distributed_api_gateway/src/main/resources/application.yml):

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      connect-timeout: 2000ms
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

> Application property stub `RateLimiterProperties.RedisProperties` (`gateway.rate-limit.redis.*`) is left untouched for future algorithm-level settings (`enabled`, `keyPrefix`, `timeout`) to prevent duplicate sources of truth.

---

## 6. How to Run & Verify Redis Infrastructure

### A. Start Redis Container
```bash
docker compose -f docker/docker-compose.yml up -d
```

### B. Verify Container Health
```bash
docker compose -f docker/docker-compose.yml ps
```
*Output should indicate container status `healthy`.*

### C. Run Infrastructure Connectivity & Actuator Verification Test
```bash
.\mvnw.cmd test -Dtest=RedisInfrastructureTest
```

### D. Verify Spring Boot Actuator Health Endpoint
When the application is running, inspect:
```
http://localhost:8080/actuator/health
```
*Expected Redis status in health payload:*
```json
"redis": {
  "status": "UP",
  "details": {
    "version": "7.2.X"
  }
}
```

### E. Stop Container & Clean Environment
```bash
docker compose -f docker/docker-compose.yml down
```

---

## 7. Documented Failure Semantics Ambiguity Disclosure

There is an acknowledged conflict in the repository documentation regarding Redis failure semantics:

1. **`Sequence_diagram.md` (SEQ-014)**: Specifies fail-fast startup behavior (`Redis Connection Failed -> Retry Connection -> Failure Policy -> Startup Failed`), requiring that the Gateway must NOT enter `READY` state if Redis is unreachable on boot.
2. **`ADR-0008`**: Specifies graceful degradation (`Redis unavailable -> Gateway reports degraded health -> Requests fail gracefully`), stating *"Redis failures should never crash the application."*

**Resolution**: This conflict is documented as an architectural ambiguity without altering documentation or inventing unapproved failure semantics. For Sprint 9, production startup sequence behavior is preserved while ordinary unit tests remain 100% independent of Redis.

---

## 9. Sprint 10 — Redis Storage Abstraction Layer

Sprint 10 introduces the low-level Redis Storage Abstraction Layer (`RedisService`, `RedisKeyBuilder`, `RedisSerializer`, `RedisStorageException`).

### Core Components
- **`RedisKeyBuilder`**: Standardized key builder enforcing the repository schema `<project>:<module>:<resource>:<identifier>` (e.g. `gateway:ratelimit:user:123`).
- **`RedisService` & `DefaultRedisService`**: Low-level storage contract exposing string operations (`set`, `setWithTtl`, `get`), counters (`increment`, `incrementBy`, `decrement`), TTL management (`expire`), existence (`exists`, `delete`), and hash field operations (`hashSet`, `hashGet`, `hashDelete`).
- **`JacksonRedisSerializer`**: JSON serialization and deserialization utility using Jackson `ObjectMapper`.
- **`RedisStorageException`**: Domain exception extending `GatewayException` for Spring Data Redis driver exception translation.

---

## 10. Sprint 11 — Redis Atomic Operations & TTL Capabilities

Sprint 11 extends `RedisService` with single-command atomic primitives and TTL capabilities required by distributed rate limiting.

### Core Capabilities Introduced
- **Atomic Set Primitives**: `setIfAbsent` (`SETNX`), `setIfAbsentWithTtl` (`SETNX EX`), `getAndSet` (`GETSET`).
- **Atomic Counter Primitives**: `incrementBy` (`INCRBY`), `decrementBy` (`DECRBY`).
- **Atomic Hash Primitives**: `hashSetIfAbsent` (`HSETNX`), `hashIncrement` (`HINCRBY`).
- **TTL Management**: `getTtl` (`TTL`), `expireAt` (`EXPIREAT`), `persist` (`PERSIST`).

### Integration & Concurrency Testing
- **`RedisStorageIntegrationTest`**: Verifies atomic primitives and TTL inspection against live Redis.
- **`RedisConcurrencyIntegrationTest`**: Multi-threaded test verifying zero lost updates under 30-thread contention.

```bash
.\mvnw.cmd test -Dtest=RedisStorageIntegrationTest,RedisConcurrencyIntegrationTest
```

> **Important**: Lua script execution engines (`EVAL`/`EVALSHA`) are **not** implemented in Sprint 11. They begin in Sprint 12.

---

## 11. Sprint 12 — Redis Lua Scripts & Execution Engine

Sprint 12 introduces the mechanism for **atomic multi-step Redis workflows**.

Sprint 11 delivered primitives that are each atomic on their own. A workflow such as
`read state -> calculate -> update state -> refresh TTL -> return result` is **not** atomic merely
because every individual command is, so concurrent Gateway instances can still interleave and
corrupt shared state. Redis executes an entire Lua script without interruption, which closes that
gap without distributed locks and without JVM synchronization.

### Core Components

- **`LuaScript`**: Immutable value holding a script's name, body, and SHA1 digest.
- **`LuaScriptLoader`** *(internal)*: Reads `.lua` files from the classpath, computes their SHA1
  digest, registers them with Redis via `SCRIPT LOAD`, and caches the resulting SHA.
- **`LuaExecutor` / `DefaultLuaExecutor`** *(public API)*: Executes registered scripts through
  `EVALSHA`, recovers automatically from a Redis-side script cache miss, and translates Lua
  failures into the Redis exception hierarchy.
- **`RedisService.executeLua(...)`**: Storage contract entry point, delegating to `LuaExecutor`.

### Script Lifecycle

```
Application Startup
        |
Read Script From Classpath      (redis/scripts/*.lua)
        |
Compute SHA1
        |
SCRIPT LOAD
        |
Cache SHA  ->  EVALSHA on every subsequent execution
```

`EVALSHA` is used instead of `EVAL` so the script body is not transmitted on every request.

### Script Cache Miss Recovery

Redis may evict its script cache (for example after `SCRIPT FLUSH` or a restart). The engine
recovers without operator intervention:

```
EVALSHA  ->  NOSCRIPT  ->  reload script  ->  update SHA  ->  retry once  ->  result
```

Recovery is bounded to a single retry, so a persistently failing script cannot cause an
unbounded retry loop.

### Shipped Scripts

| Script | Purpose |
|--------|---------|
| `increment.lua` | Atomic counter increment plus expiration establishment |

`increment.lua` applies the TTL only when the key has no expiration, so repeated increments never
extend an existing window. Algorithm scripts (`token_bucket.lua`, sliding window, leaky bucket)
belong to the distributed rate limiter work and are **not** part of Sprint 12.

### Error Semantics

| Code | Exception | Raised When |
|------|-----------|-------------|
| `REDIS-005` | `LuaExecutionException` | The script fails during execution (Lua runtime error, invalid arguments) |
| `REDIS-006` | `LuaScriptNotLoadedException` | The script is unknown, or Redis rejects its registration |

The Redis module never decides HTTP behaviour or fail-open / fail-closed semantics. The calling
module owns failure policy.

### Startup Behaviour When Redis Is Unavailable

Script bodies are read from the classpath eagerly, and a missing script resource fails fast
because it is a packaging defect. Registering a script with Redis is a network operation: failure
at startup is logged as a warning and tolerated, because the execution engine registers the script
automatically on the first `NOSCRIPT` response. Redis unavailability therefore never prevents the
Gateway from starting, and unit tests remain 100% independent of Redis.

### Verification

```bash
.\mvnw.cmd test -Dtest=LuaScriptLoaderTest,DefaultLuaExecutorTest
.\mvnw.cmd test -Dtest=LuaExecutionIntegrationTest,LuaConcurrencyIntegrationTest
```

The integration tests exercise real `SCRIPT LOAD` / `EVALSHA`, force a genuine `NOSCRIPT` by
flushing the Redis script cache mid-test, and verify zero lost updates across 30 concurrent
threads performing 600 Lua increments.

---

## 12. Sprint 16 — Distributed Fixed Window Rate Limiter

Sprint 16 is the first increment of the Distributed Rate Limiter phase, and the first time Redis
actually makes a rate limiting decision.

Sprints 9–12 built the distributed infrastructure; Sprints 13–15 proved the concurrency model
correct. Until now every algorithm kept its counters in the JVM heap, which means a client hitting
two Gateway instances received **two independent quotas**. `RedisFixedWindowRateLimiter` moves the
counter into Redis so all instances share one.

### Component

| Class | Role |
|---|---|
| `RedisFixedWindowRateLimiter` | Fixed Window algorithm with the counter stored in Redis |

It implements the existing `RateLimiter` contract, so nothing upstream changes.

### Why no new Lua script

ADR-0009 states that *"Simple Fixed Window may continue using native Redis commands"*, and Sprint 12's
`increment.lua` already performs exactly the Fixed Window transition:

```
INCRBY counter
   |
set TTL only if the key has no expiration
   |
return the new count
```

Applying the TTL only when absent is what keeps the window **fixed** rather than sliding: later
requests in the same window increment the counter without extending its lifetime. The remaining
step — comparing the returned count against capacity — is a pure comparison and needs no atomicity.

### Key schema

Built through `RedisKeyBuilder`, so keys obey the Engineering Contract schema
`environment:module:resource:identifier`:

```
dev:ratelimiter:fixed:user-101:1722587600
                                ^ window start, epoch seconds
```

Each window owns its own key, so an expired window's counter is simply abandoned to its TTL and
never needs resetting.

> **Note.** The Redis design document shows this key as `gateway:ratelimit:fixed:<clientId>:<window>`,
> which has five segments and no environment prefix, and conflicts with the four-segment schema the
> Engineering Contract mandates and `RedisKeyBuilder` enforces. The Engineering Contract is the
> higher authority, so the builder schema is used and `RedisKeyBuilder` was left unchanged.

### TTL

```
TTL = window duration + 10 second safety buffer
```

The buffer, documented in the Redis design, prevents a counter expiring fractionally before its
window closes because of clock differences between instances or processing delay.

### Enabling it

Uses the existing Redis configuration block — no new property was introduced:

```yaml
gateway:
  rate-limit:
    algorithm: FIXED_WINDOW
    redis:
      enabled: true      # false (default) keeps the in-memory limiter
```

The algorithm is the same Fixed Window counter either way; only the store differs, which is why
this is a storage flag rather than a new algorithm constant.

### Concurrency

The limiter holds **no mutable JVM state** — no map, no lock, no atomic. Correctness rests entirely
on Redis executing `increment.lua` to completion without interruption, which serializes the counter
transition across every Gateway instance. A JVM lock could not provide this, because it coordinates
only threads inside one process.

### Verification

```bash
.\mvnw.cmd test -Dtest=RedisFixedWindowRateLimiterTest
.\mvnw.cmd test -Dtest=RedisFixedWindowRateLimiterIntegrationTest
```

The integration suite runs two independent limiter instances against one Redis and asserts that a
client cannot obtain a separate quota per instance — including under 40 concurrent threads split
across both instances, where exactly the configured quota is admitted.

### Known limitations

- **Failure policy is not implemented.** Redis failures propagate as the Redis module's exceptions.
  Choosing between fail-open and fail-closed is a separate documented deliverable of this phase and
  was deliberately not decided here.
- **Window durations below one second are not supported.** The key's window component is expressed
  in epoch seconds, so sub-second windows would map adjacent windows onto the same key. The
  documented configuration surface expresses windows in seconds or minutes.
- **Only Fixed Window is distributed** as of this sprint. Sliding Window Counter followed in
  Sprint 17 (section 13); Sliding Window Log, Token Bucket and Leaky Bucket remain in-memory and
  per-instance.

---

## 13. Sprint 17 — Distributed Sliding Window Counter

The second Phase 6 deliverable. Where Sprint 16 moved the Fixed Window counter into Redis, this
moves the Sliding Window Counter — and unlike Fixed Window, it needs a Lua script of its own.

### Why this algorithm needs a script

Fixed Window's transition is "increment, set TTL if absent" — a single-key write that
`increment.lua` already performed. Sliding Window Counter reads **two** keys, computes a weighted
estimate, and writes **only if** the result permits it:

```
estimated = previousCount x previousWeight + currentCount
previousWeight = (windowDuration - elapsedInCurrentWindow) / windowDuration
admit when estimated + 1 <= capacity
```

Run as separate commands, two Gateway instances could each read the same counts, each conclude
there is room for one more request, and each increment — admitting more than capacity. That is the
exact hazard ADR-0009 reserves Lua for, so `sliding_counter.lua` performs the whole
read-calculate-decide-write sequence as one atomic step.

Only admitted requests increment the counter: the increment sits inside the branch, so a rejected
request never consumes quota.

### Components

| Artifact | Role |
|---|---|
| `sliding_counter.lua` | Atomic weighted evaluation over the current and previous window counters |
| `RedisSlidingWindowCounterRateLimiter` | Algorithm with both counters stored in Redis |

The script name follows ADR-0009's script organization (`sliding_counter.lua`). The Development
Playbook calls it `sliding_window.lua`; ADRs are the higher authority.

### Keys

```
dev:ratelimiter:sliding:user-101:1789992000   <- current window
dev:ratelimiter:sliding:user-101:1789991940   <- previous window (one window earlier)
```

Built through `RedisKeyBuilder`, following the resolution already applied in Sprint 16: the
Engineering Contract schema `environment:module:resource:identifier` outranks the five-segment
example in the Redis design document.

**Keying by window start replaces key rotation.** The Redis design describes a `current:` /
`previous:` key pair per client, which would require renaming or resetting keys as windows roll —
itself a multi-step operation needing coordination. Because each window owns a timestamped key, the
previous window's counter is simply the key one window earlier, and an expired window is abandoned
to its TTL. Same two-counter model, no rotation.

### TTL

```
TTL = (2 x window duration) + 10 second safety buffer
```

Twice the window because a counter is read as the *previous* window throughout the window that
follows its own — the Redis design states the previous window's lifetime as "current window TTL plus
one additional window". Since each key is first current and then previous, one TTL covers both
roles. As with Fixed Window, the expiry is set only when absent, so repeated requests never extend it.

### Enabling it

The existing Redis configuration block, unchanged:

```yaml
gateway:
  rate-limit:
    algorithm: SLIDING_WINDOW_COUNTER
    redis:
      enabled: true      # false (default) keeps the in-memory limiter
```

### Concurrency

No mutable JVM state — no map, no lock, no atomic. Correctness rests on Redis executing
`sliding_counter.lua` to completion without interruption.

Backward clock drift needs no special handling here: because each window owns a timestamped key,
drift selects an earlier key rather than corrupting a shared one.

### Verification

```bash
.\mvnw.cmd test -Dtest=RedisSlidingWindowCounterRateLimiterTest
.\mvnw.cmd test -Dtest=RedisSlidingWindowCounterRateLimiterIntegrationTest
```

The integration suite proves the property that distinguishes this algorithm from Fixed Window:
after a window is fully consumed, stepping halfway into the next window admits only 5 of 10
requests, because half the previous window still counts. A Fixed Window limiter would have granted
a full fresh quota at that boundary. It also runs two independent limiter instances against one
Redis, including 40 concurrent threads split across both, admitting exactly the configured quota.

### Known limitations

- **Failure policy is still not implemented.** Redis failures propagate as the Redis module's
  exceptions; fail-open versus fail-closed remains a separate Phase 6 deliverable.
- **Window durations below one second are not supported**, for the same reason as Fixed Window: the
  key's window component is expressed in epoch seconds.
- **Sliding Window Log, Token Bucket and Leaky Bucket remain in-memory** and per-instance.
