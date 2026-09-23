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
  Sprint 17 (section 13), Sliding Window Log in Sprint 18 (section 14) and Token Bucket in
  Sprint 19 (section 15) and Leaky Bucket in Sprint 20 (section 16). Every algorithm is now
  distributed.

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
- Sliding Window Log followed in Sprint 18 (section 14), Token Bucket in Sprint 19
  (section 15) and Leaky Bucket in Sprint 20 (section 16). Every algorithm is now distributed.


---

## 14. Sprint 18 — Distributed Sliding Window Log

The third Phase 6 deliverable, and the most precise of the window algorithms: instead of counting
requests, it records each admitted request's timestamp and decides from the timestamps themselves.

### Why this algorithm needs a script

Sliding Window Counter approximates the previous window with a weight. The log does not approximate
at all — the window is exactly `[now - window, now]`, so the decision is a sequence of three
dependent steps:

```
1. evict entries older than now - window
2. count what remains
3. admit and record only if count < capacity
```

Issued as separate commands, two Gateway instances could each read the same count, each conclude
there is room, and each record — admitting more than capacity. `sliding_log.lua` performs evict,
count, conditional insert and TTL refresh as one uninterrupted step, so the quota holds across
every instance.

**Evict before counting, and insert only if the count permits.** The Redis design sketches
insert-then-count-then-remove-if-over. That order admits a request into the log before deciding on
it, and would have to undo the insert on rejection. The in-memory `SlidingWindowLogRateLimiter`
establishes the opposite semantics — a rejected request never enters the log and never consumes
quota — and the in-memory algorithm is the behavioural reference the distributed version must
match, so the script evaluates first and writes second.

### Components

| Artifact | Role |
|---|---|
| `sliding_log.lua` | Atomic evict / count / conditional-insert over the timestamp log |
| `RedisSlidingWindowLogRateLimiter` | Algorithm with the request log stored in Redis |

### Keys

```
dev:ratelimiter:log:user-101
```

Built through `RedisKeyBuilder`, following the resolution applied since Sprint 16: the Engineering
Contract schema `environment:module:resource:identifier` outranks the longer example in the Redis
design document.

**No window component in the key**, unlike Fixed Window and Sliding Window Counter. Those two
partition time into discrete windows and give each window its own counter key. The log has no
discrete windows at all — it holds one continuously rolling sequence per client, and the window is
applied at read time as a score range. One client therefore has exactly one key, for its whole
lifetime. A consequence worth stating: this algorithm is **not** limited to windows of one second
or more, because no part of the key is expressed in epoch seconds.

The value is a Redis **sorted set**, score = the request's epoch-millisecond timestamp:

```
ZREMRANGEBYSCORE key -inf (cutoff     <- exclusive: an entry exactly at the boundary survives
ZCARD key                             <- entries currently inside the window
ZADD key <nowMillis> <member>         <- only when the count permits
```

The exclusive cutoff (`(cutoff`, Redis's exclusive-range syntax) is what makes an entry at exactly
`now - window` still count as inside the window, matching the in-memory implementation's boundary
rule.

### The member token

A sorted set is a *set*: two members with the same value are one member, and a second `ZADD` of it
overwrites the score rather than appending. If the member were the bare timestamp, two requests
admitted in the same millisecond would collapse into one entry and the client would silently get
extra quota — and under load, requests landing in the same millisecond is the normal case, not an
edge case.

The member is therefore a unique token generated per request by the Java side
(`UUID.randomUUID()`) and passed to the script as an argument, never generated inside the script.
Scripts must be deterministic; generating randomness inside one is exactly what Redis's scripting
contract forbids. The integration suite pins this: 40 concurrent requests within one millisecond
must leave the log holding exactly as many entries as were admitted.

### TTL

```
TTL = window duration + 10 second safety buffer
```

The same buffer rule as the other two algorithms, and set with `EXPIRE` on every admitted request —
**refreshed**, not set-if-absent. Fixed Window sets its TTL once because the key must die when its
window closes; the log's key has no window of its own and must outlive its newest entry, so its
lifetime is pushed forward with each admission. An idle client's key expires on its own, which is
what keeps abandoned logs from accumulating.

### Enabling it

The existing Redis configuration block, unchanged:

```yaml
gateway:
  rate-limit:
    algorithm: SLIDING_WINDOW_LOG
    redis:
      enabled: true      # false (default) keeps the in-memory limiter
```

### Concurrency

No mutable JVM state — no map, no lock, no atomic. Correctness rests on Redis executing
`sliding_log.lua` to completion without interruption.

**Backward clock drift is deliberately not handled the way the in-memory version handles it.** The
in-memory limiter clears its log when it observes the clock moving backwards, because its log is
private to one JVM. Here the log is shared: one instance with a skewed clock clearing it would
destroy quota state for every other instance, turning a local clock problem into a cluster-wide
one. A sorted set orders by score regardless of insertion order, so an entry written with an
earlier timestamp simply sorts earlier and ages out earlier — the log stays consistent without
intervention.

### Memory characteristic

This is the one algorithm whose storage grows with traffic: a counter is one integer per window,
whereas the log holds one sorted-set entry per admitted request. Storage per client is therefore
bounded by capacity, not by a constant — the price paid for exact boundaries. Eviction is not
deferred to the TTL: every evaluation removes what has aged out, so a client's log never exceeds
its capacity plus the request being evaluated.

### Verification

```bash
.\mvnw.cmd test -Dtest=RedisSlidingWindowLogRateLimiterTest
.\mvnw.cmd test -Dtest=RedisSlidingWindowLogRateLimiterIntegrationTest
```

The integration suite proves the boundary precision that distinguishes this algorithm: an entry at
exactly `now - window` is still active, one millisecond past it releases exactly one slot, and a
quota spent at the very end of a window cannot be respent immediately after the boundary — the
2x burst that Fixed Window permits. It also runs two independent limiter instances against one
Redis, including 40 concurrent threads split across both, admitting exactly the configured quota
and storing exactly that many entries.

### Known limitations

- **Failure policy is still not implemented.** Redis failures propagate as the Redis module's
  exceptions; fail-open versus fail-closed remains a separate Phase 6 deliverable.
- **Storage scales with admitted traffic**, as described above — unlike the counter-based
  algorithms, whose footprint is constant per client.
- Token Bucket followed in Sprint 19 (section 15) and Leaky Bucket in Sprint 20
  (section 16). Every algorithm is now distributed.

---

## 15. Sprint 19 — Distributed Token Bucket

The fourth Phase 6 deliverable, and the first whose state is neither a counter nor a list of
timestamps. Token Bucket is also the algorithm the Redis design uses to explain why this project
adopted Lua at all.

### Why this algorithm needs a script

The window algorithms increment something. Token Bucket computes a value:

```
elapsed = now - lastRefill
tokens  = min(capacity, tokens + elapsed x refillRate)
admit when tokens >= 1, then tokens = tokens - 1
```

That new value depends on elapsed time and is capped at capacity, so it **cannot be expressed as an
INCRBY** — the arithmetic has to happen where the state lives. `REDIS_DESIGN.md` walks through the
failure directly: two Gateways `GET` 5 tokens, both compute 4, both `SET` 4, and two requests have
consumed one token. `token_bucket.lua` performs read, refill, consume, write and TTL refresh as one
uninterrupted step.

### Components

| Artifact | Role |
|---|---|
| `token_bucket.lua` | Atomic read / refill / conditional-consume / write over the bucket hash |
| `RedisTokenBucketRateLimiter` | Algorithm with the bucket stored in Redis |

### Keys and state

```
dev:ratelimiter:tokenbucket:user-101     ->  Redis HASH
                                             tokens     = "4.5"
                                             lastRefill = "1790173361000"
```

Built through `RedisKeyBuilder`, following the resolution applied since Sprint 16: the Engineering
Contract schema `environment:module:resource:identifier` outranks the longer forms in the Redis
design document and the Development Playbook, which for this algorithm disagree with each other in
four different places.

A **hash**, not a string or a sorted set — the Redis design specifies it (`Hash | Token Bucket
state`) and explicitly rejects splitting the fields across separate keys. As with the Sliding
Window Log there is **no window component**: a bucket is continuous, not partitioned into windows.
Because no part of the key is expressed in epoch seconds, this algorithm is not restricted to
windows of one second or more.

**Deviation — capacity is not stored.** The Redis design lists three fields: `tokens`, `capacity`
and `lastRefill`. Capacity is **configuration**, not bucket state: it is resolved per request from
the policy snapshot, nothing ever reads it back, and persisting it would let a stale value outlive
a configuration change or let two differently-configured instances fight over the field. Only
`tokens` and `lastRefill` are written. Recorded here rather than applied silently.

### Fractional tokens, and why they are returned as a string

Tokens are a `double`, matching the in-memory implementation — a bucket refilling at 2 tokens/sec
holds 0.5 tokens after 250ms, and that fraction is real quota.

**Redis truncates a Lua number to an integer on the way out**, including inside a returned table:

```
EVAL "return 3.7"           ->  (integer) 3        <- 0.7 tokens destroyed
EVAL "return {1, 2.9}"      ->  1) 1   2) 2        <- truncation applies inside tables
EVAL "return tostring(3.7)" ->  "3.7"              <- preserved as a bulk string
```

Returning the count as a number would silently discard up to a whole token on **every** call, and
because the truncated value is written back, the loss compounds. The script therefore returns the
exact count as a **string**, which Java parses with `Double.parseDouble`. The whole-number fields
(`allowed`, `remaining`, `retryAfterMillis`) return as ordinary integers. This is the Token Bucket
analogue of Sprint 18's member-collision defect: a representation mismatch that sequential tests
would not reveal.

`remaining` is reported to clients as whole tokens, but the **reset time is computed from the exact
fractional count**, so the advertised refill time is not rounded.

### State is written on rejection

Unlike the Sliding Window Log — where a rejected request must not enter the log — a rejected Token
Bucket request **does** write state: it materialises the tokens accrued up to now and advances
`lastRefill`. This matches the in-memory implementation. Withholding the write would re-accrue the
same interval on the following request, effectively refilling twice.

### TTL

```
TTL = 1 hour, refreshed on every evaluation
```

The Redis design specifies one hour with refresh-on-access, a different rule from the window
algorithms' `window + buffer`: a bucket has no window to expire alongside, so its lifetime is
measured from the last request. An active client's bucket persists; an abandoned one disappears on
its own.

**Deviation — refreshed on every evaluation, not only on successful ones.** The design says "every
successful request refreshes the TTL". Since a rejected request also writes state, a bucket that
expired *while its client was being throttled* would be recreated full on the next request, handing
back the quota just spent. The TTL is refreshed whenever state is written, which is every
evaluation.

### Time source

Timestamps come from the injected Java `Clock`, consistent with the three distributed limiters
already shipped and with the in-memory Token Bucket.

**This algorithm is more exposed to clock skew than the window algorithms, and that is worth stating
plainly.** For Fixed Window and Sliding Window Counter a skewed clock selects a *different key* —
bounded, self-correcting damage. Here the timestamp feeds the refill arithmetic directly: an
instance whose clock runs fast writes a `lastRefill` in the future, and the other instances then
compute no elapsed time until the real clock catches up. The script clamps negative elapsed time to
zero, so a skewed instance can never *drain* a shared bucket, but no client-side clock can give the
cluster a single shared notion of time. Deployments are expected to keep instances NTP-synchronised.

Redis's own `TIME` command would remove the skew entirely and remains available as a future change.
It was not adopted here because it would forfeit the deterministic clock the whole test suite is
built on, and because clock-skew hardening is properly its own deliverable.

### Enabling it

The existing Redis configuration block, unchanged:

```yaml
gateway:
  rate-limit:
    algorithm: TOKEN_BUCKET
    redis:
      enabled: true      # false (default) keeps the in-memory limiter
```

No new configuration was added. Capacity comes from `defaultCapacity` or the policy; the refill rate
from `refillRate` (tokens per second) or, when a policy carries a window, from
`capacity / windowSeconds`. The one-hour TTL is a constant rather than a property, because no
configuration key for it is documented.

### Concurrency

No mutable JVM state — no map, no lock, no atomic. Correctness rests on Redis executing
`token_bucket.lua` to completion without interruption.

### Verification

```bash
.\mvnw.cmd test -Dtest=RedisTokenBucketRateLimiterTest
.\mvnw.cmd test -Dtest=RedisTokenBucketRateLimiterIntegrationTest
```

The integration suite proves the properties specific to this algorithm: a new bucket starts full so
an unseen client may burst to capacity; tokens refill at exactly the configured rate; a half-token
refill is **stored as 0.5 rather than truncated to 0**; a rejected request still advances the
bucket; tokens cap at capacity after an hour of idling; and an expired bucket behaves like an unseen
client. It also runs two independent limiter instances against one Redis, including 40 concurrent
threads split across both, admitting exactly the configured quota — and then asserts the **stored
token balance** independently, so a write lost under contention cannot hide behind the counters.

### Known limitations

- **Failure policy is still not implemented.** Redis failures propagate as the Redis module's
  exceptions; fail-open versus fail-closed remains a separate Phase 6 deliverable.
- **Clock skew between Gateway instances affects refill accuracy**, as described above. Mitigated,
  not eliminated.
- **An invalid configuration throws rather than rejects.** A non-positive capacity or refill rate
  raises `IllegalArgumentException` before Redis is contacted, matching the in-memory Token Bucket
  and differing deliberately from Fixed Window, which rejects such requests.
- **Leaky Bucket followed in Sprint 20** (section 16), completing the algorithms.

---

## 16. Sprint 20 — Distributed Leaky Bucket

The fifth Phase 6 deliverable and the **last rate limiting algorithm**. After this sprint every
algorithm the Gateway offers runs distributed; only the Failure Policy remains.

### ⚠️ Documented deviation — the design specifies a queue, the Gateway implements a meter

This is the largest design-versus-implementation gap encountered in Phase 6, and it is recorded
here rather than resolved by editing the design document.

**What `REDIS_DESIGN.md` specifies:**

| Aspect | Section | Specifies |
|---|---|---|
| Data structure | Data Structures matrix | `List \| Leaky Bucket queue` |
| Rationale | "Why Lists?" | *"Leaky Bucket behaves like Queue → FIFO. Redis Lists naturally support FIFO operations."* |
| Operations | Lists | `LPUSH`, `RPUSH`, `LPOP`, `RPOP`, `LLEN` |
| Stores | Leaky Bucket Keys | *"Queue State"* and *"Last Leak Timestamp"* |

**What the Gateway implements.** `LeakyBucketRateLimiter` is not a queue. Its entire state is:

```java
public record LeakyBucket(double waterLevel, long lastLeakTimestampMillis) {}
```

A **meter** — one fractional water level that rises by one unit per admitted request and drains
continuously. No request is ever stored.

**Why the meter was kept, and the List model not implemented:**

1. **A List cannot hold the state the same design requires.** The design says the key stores queue
   state *and* a last-leak timestamp. A Redis List is homogeneous — there is nowhere to put the
   timestamp. The specification contradicts itself within one section. Token Bucket needed a hash
   for exactly this reason.
2. **It would change observable behaviour.** A queue stores discrete entries and drains in whole
   units; the meter drains fractionally. Boundary decisions would differ from the in-memory
   limiter, and since Sprint 18 the established rule has been that **the in-memory implementation
   is the behavioural reference the distributed version must match**. Two limiters behind one
   configuration flag must not disagree.

**The state is therefore a hash of `level` and `lastLeak`.** `REDIS_DESIGN.md` has deliberately
**not** been modified: it remains an authoritative document describing an intended design, and this
section records where the implementation departs from it and why. Reconciling the two is a
documentation decision for the project owner, not something to be resolved inside an
implementation sprint.

### Why this algorithm needs a script

Like Token Bucket, the new level depends on elapsed time and is clamped, so it cannot be expressed
as an `INCRBY` — the arithmetic has to happen where the state lives:

```
elapsed = now - lastLeak
level   = max(0, level - elapsed x leakRate)
admit when level + 1 <= capacity, then level = level + 1
```

Issued as separate commands, two instances would each read the same level, each find room for one
more unit, and each write — admitting past capacity. `leaky_bucket.lua` performs drain, admission
test, write and TTL refresh as one uninterrupted step.

### Components

| Artifact | Role |
|---|---|
| `leaky_bucket.lua` | Atomic drain / admission-test / write over the bucket hash |
| `RedisLeakyBucketRateLimiter` | Algorithm with the bucket stored in Redis |

The script name is **not documented anywhere** — ADR-0009's script listing stops at
`sliding_log.lua` and never names one for this algorithm. `leaky_bucket.lua` was chosen by direct
analogy with `token_bucket.lua`. ADR-0009 has not been amended to add it; that remains open.

### Keys and state

```
dev:ratelimiter:leaky:user-101     ->  Redis HASH
                                        level    = "4.5"
                                        lastLeak = "1790173361000"
```

Built through `RedisKeyBuilder`: the Engineering Contract schema outranks the design document's
`gateway:ratelimit:leaky:<clientId>`, as in every sprint since 16. No window component. Capacity is
not stored, for the same reason as Token Bucket — it is configuration, not bucket state.

### Leaky Bucket versus Token Bucket

The two share a state shape and almost nothing else. These differences are all preserved from the
in-memory implementations:

| | Leaky Bucket | Token Bucket |
|---|---|---|
| Initial state | **Empty** — no credit | **Full** — full burst available |
| Direction | Level **rises** on admit, drains over time | Tokens **fall** on admit, refill over time |
| Admission test | `level + 1 <= capacity` | `tokens >= 1` |
| **Burst** | **None** — constant outflow | Up to capacity |
| Idle client | Gains nothing beyond an empty bucket | Accumulates tokens up to capacity |
| `remaining` | `capacity - ceil(level)` — **rounds up** | `(long) tokens` — **truncates** |
| `retryAfter` | **Floored at 1ms** | No floor |
| **Clock regression** | `lastLeak` **preserved** — explicit invariant | `lastRefill` follows the clock |

The rounding differences are not cosmetic. Leaky Bucket rounds the level **up** when reporting
remaining capacity, so a partially used unit still occupies its slot — the conservative choice for
an algorithm whose purpose is a smooth outflow.

### Clock regression

The in-memory implementation states the invariant directly: *"lastLeakTimestampMillis must NEVER
move backwards."* The script preserves the stored timestamp when the clock runs backwards, rather
than following it as Token Bucket does. The reason is asymmetry: rewinding `lastLeak` would cause
the same interval to be drained **twice** on a later request, handing back capacity that was
legitimately used. Preserving it means a skewed instance can neither drain the shared bucket early
nor corrupt it for the others.

Because the stored timestamp may be ahead of the observed clock, the reset time reported to clients
is measured from **the timestamp actually stored**, not from `now`.

### Fractional levels are returned as a string

Identical constraint to Sprint 19: Redis truncates a Lua number to an integer on the way out,
including inside a returned table. The level is returned as a string and parsed with
`Double.parseDouble`; returning it as a number would silently discard up to a whole unit of water
on every call, and the loss would compound as the truncated value is written back.

Everything the client is told — `remaining`, `retryAfter`, `resetTime` — is derived on the Java
side from the exact level, so the in-memory rounding rules (`ceil` for remaining, a 1ms floor on
retryAfter) are mirrored where they can be read and tested.

### TTL

```
TTL = 1 hour, refreshed on every evaluation
```

As documented, and with the same deviation as Token Bucket: refreshed on every evaluation rather
than only on admitted requests, because a bucket that expired while its client was being throttled
would be recreated **empty** and hand back the capacity just consumed.

### Enabling it

```yaml
gateway:
  rate-limit:
    algorithm: LEAKY_BUCKET
    redis:
      enabled: true      # false (default) keeps the in-memory limiter
```

No new configuration. Note that there is **no separate leak-rate property**: `refillRate` serves
both bucket algorithms, and a policy window overrides it with `capacity / windowSeconds`.

### Concurrency

No mutable JVM state — no map, no lock, no atomic. Correctness rests on Redis executing
`leaky_bucket.lua` to completion without interruption.

### Verification

```bash
.\mvnw.cmd test -Dtest=RedisLeakyBucketRateLimiterTest
.\mvnw.cmd test -Dtest=RedisLeakyBucketRateLimiterIntegrationTest
```

The integration suite proves the properties specific to this algorithm: the bucket **starts empty**;
water drains at exactly the configured rate; a half-unit drain is **stored as 4.5 rather than
truncated**; a rejected request still materialises the drained level; **an hour of idling grants no
burst** — the property that most sharply separates this algorithm from Token Bucket; and a backward
clock **does not rewind** the stored timestamp. It also runs two independent limiter instances
against one Redis, including 40 concurrent threads split across both, admitting exactly the
configured capacity — then asserts the **stored water level** independently, so a write lost under
contention cannot hide behind the counters.

### Known limitations

- **Failure policy is still not implemented.** Redis failures propagate as the Redis module's
  exceptions; fail-open versus fail-closed remains the final Phase 6 deliverable.
- **The state model deviates from `REDIS_DESIGN.md`**, as detailed at the top of this section. The
  design document has deliberately not been edited.
- **`leaky_bucket.lua` is not named in ADR-0009's script listing.** The name was inferred; amending
  the ADR remains open.
- **Clock skew between Gateway instances affects drain accuracy**, as with Token Bucket. The
  never-move-backwards invariant bounds the damage but does not eliminate it.
- **An invalid configuration throws rather than rejects**, matching the in-memory Leaky Bucket.

### Phase 6 status after this sprint

```
Fixed Window            done (section 12)      Token Bucket     done (section 15)
Sliding Window Counter  done (section 13)      Leaky Bucket     done (section 16)
Sliding Window Log      done (section 14)      Failure Policy   remaining
```

Every rate limiting algorithm now runs distributed.
