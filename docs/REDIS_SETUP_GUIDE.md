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
