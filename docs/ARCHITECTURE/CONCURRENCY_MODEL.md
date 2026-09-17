# Concurrency Model

Version: 1.0

Status: Implemented (Sprint 13 — Thread Safety)

Related Documents:
`Development_Playbook.md` (Phase 5 — Concurrency & Thread Safety),
`Engineering_contracts.md` (Rate Limiter, Redis),
`package_structure.md` (Shared, Configuration),
`REDIS_DESIGN.md`, ADR-0009

---

## 1. Purpose

This document defines what thread safety means for the Distributed API Gateway, records the
shared mutable state that exists in the JVM, and explains which concurrency mechanism guards each
piece of it and why that mechanism was chosen over the alternatives.

It documents the state of the code, not an aspiration. Every claim here is backed by a test.

---

## 2. What Thread Safety Means In This Project

A servlet container serves each request on its own thread. Every component in the request path is
therefore entered concurrently, and the Gateway must produce the same decision regardless of the
order in which threads interleave.

Two different problems are deliberately kept separate:

```
Thread safety            ->  correctness inside ONE JVM
                             (many request threads, one process)

Distributed correctness  ->  correctness across MANY JVMs
                             (many Gateway instances, one Redis)
```

**JVM synchronization never substitutes for the second.** As `Engineering_contracts.md` states for
the rate limiter, *"Distributed synchronization relies on Redis, not JVM locks"*, and for the Redis
module, *"The module must never rely on JVM synchronization for distributed correctness."*
Redis atomic commands and Lua scripts (Sprints 11–12) own that boundary. Nothing in this document
changes it.

---

## 3. Governing Rules

From `Development_Playbook.md` (Thread Safety Rules):

1. Prefer immutable objects.
2. Avoid shared mutable state.
3. Use Atomic classes instead of synchronized counters where possible.
4. Use Concurrent Collections instead of synchronized collections.
5. Minimize lock scope.
6. Never block request threads unnecessarily.

Preferred progression:

```
Immutable Objects
        |
Atomic Classes
        |
Concurrent Collections
        |
ReadWriteLock
        |
synchronized
```

The implementation stops at **Concurrent Collections**. No lock of any kind exists in
`src/main`, and none is needed for the current design.

---

## 4. Shared Mutable State Inventory

| State | Owner | Structure | Mechanism | Status |
|---|---|---|---|---|
| `windows` | `FixedWindowRateLimiter` | `ConcurrentHashMap<String, FixedWindow>` | `compute` + immutable record | Safe |
| `counters` | `SlidingWindowCounterRateLimiter` | `ConcurrentHashMap<String, SlidingWindowCounter>` | `compute` + immutable record | Safe |
| `buckets` | `TokenBucketRateLimiter` | `ConcurrentHashMap<String, TokenBucket>` | `compute` + immutable record | Safe |
| `buckets` | `LeakyBucketRateLimiter` | `ConcurrentHashMap<String, LeakyBucket>` | `compute` + immutable record | Safe |
| `logs` | `SlidingWindowLogRateLimiter` | `ConcurrentHashMap<String, SlidingWindowLog>` | `compute` + **confined mutable** deque | Safe by confinement |
| `scriptCache` | `LuaScriptLoader` | `ConcurrentHashMap<String, LuaScript>` | atomic map ops + immutable `LuaScript` | Safe |
| configuration | `RateLimiterProperties` | mutable POJO, singleton bean | safe publication at startup | Safe in practice |
| request metadata | `RateLimitContext` | record | deep immutability | Safe (fixed in Sprint 13) |

Everything else in the request path — the filter, context factory, key resolver, header writer,
metrics publisher, `DefaultRedisService`, `DefaultLuaExecutor` — holds no mutable state at all.
Its fields are `final` and its locals are request-scoped.

---

## 5. Why `ConcurrentHashMap.compute`

Every rate limiting algorithm performs the same shape of work:

```
read state  ->  calculate  ->  write new state  ->  return decision
```

That is a compound read-modify-write. Making the *map* concurrent is not enough: two threads could
each read the same counter, each compute `n + 1`, and each store it, losing an update. This is the
identical hazard that Lua solves on the Redis side, and it needs an equivalent guarantee inside
the JVM.

`ConcurrentHashMap.compute(key, fn)` holds the bin lock for that key while the remapping function
runs, so the whole read-calculate-write sequence is atomic **per key**, and successive `compute`
calls on a key have a happens-before relationship. Two clients hashing to different bins never
block each other, so quota evaluation for unrelated clients proceeds in parallel — which satisfies
Rules 4, 5 and 6 simultaneously.

The state objects are records (`FixedWindow`, `SlidingWindowCounter`, `TokenBucket`,
`LeakyBucket`), so each `compute` swaps one immutable value for another and no thread can observe
a half-updated state (Rule 1).

### Rejected alternatives

| Alternative | Why rejected |
|---|---|
| `HashMap` + `synchronized` method | One global lock serializes every client. Violates Rules 4–6 for no correctness gain. |
| `Collections.synchronizedMap` | Makes individual operations atomic but *not* the read-modify-write sequence — the lost-update bug survives. |
| Per-key `ReentrantLock` / striped locks | Duplicates what `compute` already provides, adds lock lifecycle and eviction problems. Belongs to Sprint 14 if ever justified. |
| `AtomicLong` counters | Fits a bare counter, but not window rollover, token refill or timestamp eviction, which must be atomic *together* with the count. |
| CAS retry loops / `LongAdder` | Sprint 15 concern. Premature without measurement; the repository rule is measure first, optimize second. |

---

## 6. The One Mutable Object: `SlidingWindowLog`

Sliding Window Log stores a deque of request timestamps per client. Copying that deque on every
request to keep it immutable would add allocation proportional to the window size on the hot path,
so the deque stays mutable and is instead **confined**: it is only ever read or written inside the
`compute` call that owns its key.

This is the only place where a mutable object is published into a shared map, and the compiler
cannot enforce the rule. A concurrently mutated `ArrayDeque` does not fail fast — it silently loses,
duplicates or reorders entries. The invariant is therefore stated explicitly in the class Javadoc
and pinned down by `SlidingWindowLogStateIntegrityTest`, which asserts on behaviour that depends on
the deque's exact contents (saturation, full eviction, partial eviction) rather than only on the
allowed/rejected split.

**Rule for future work:** never add an accessor that hands a `SlidingWindowLog` to a caller. Derive
the value inside `compute` and return the value.

---

## 7. Defect Found And Fixed In Sprint 13

`RateLimitContext` documented its header map as unmodifiable and copied it with `Map.copyOf`.
That is a **shallow** copy: the map becomes unmodifiable, but the `List<String>` values remain the
caller's mutable `ArrayList` instances.

```java
Map<String, List<String>> copy = Map.copyOf(source);
copy.put("k", List.of());          // UnsupportedOperationException  (as documented)
copy.get("X-Api-Key").add("x");    // succeeded                      (not as documented)
```

An object that claims immutability but is not immutable is unsafe to publish across threads and
contradicts the Shared package rule *"Mutable shared state is prohibited"*. The compact constructor
now copies each value list as well, making the record deeply immutable.

Exposure was low — contexts are request-scoped and are not currently shared between threads — so
this was a latent defect rather than an active race. It is fixed because the guarantee the type
advertises is what future code will rely on.

Each value list is copied into a private `ArrayList` and wrapped with
`Collections.unmodifiableList`, **not** copied with `List.copyOf`. Both yield a list that rejects
mutation, but `List.copyOf` also rejects null elements, which the previous implementation accepted.
Using it would have turned a defensive copy into an input-validation change: header values this
type used to accept would suddenly throw. Hardening immutability must not alter what the type
accepts. The backing list is created inside the constructor and never escapes, so the wrapper is
effectively immutable while staying null-tolerant.

`RateLimitContextImmutabilityTest` guards the fix. Four of its cases fail against the previous
shallow-copy implementation, and its two null-handling cases pass against both implementations —
which is what proves the fix hardened immutability without changing accepted input.

---

## 8. Components Deliberately Left Unchanged

- **The five algorithms.** Already correct. Rewriting correct concurrent code to look busier would
  risk the algorithm semantics for no benefit.
- **`RateLimiterProperties`.** A mutable POJO, but the Spring container fully initializes it before
  any request thread exists, and no production code calls a setter at runtime. Converting it to
  immutable constructor binding would change the configuration architecture, not fix a defect.
- **The `AtomicReference` result holders** inside four algorithms. They carry the evaluation result
  out of the `compute` lambda and are per-call locals, never shared. Correct as written.
- **Redis and Lua.** Sprint 12 architecture is approved and untouched. Redis-side atomicity is not
  re-implemented with JVM constructs.

---

## 9. Testing Strategy

Thread safety cannot be demonstrated by inspecting data structure choices, so every claim above is
backed by an executable test that creates real contention:

1. Create all workers.
2. Block them on a start latch (`readyLatch` confirms every worker has arrived).
3. Release them simultaneously.
4. Await completion through `Future.get`, which also rethrows any worker exception.
5. Assert **exact** expected state.

`Thread.sleep` is not used as a synchronization mechanism. The clock is fixed for the duration of
each burst (`MutableTestClock`), so no window rolls, no token refills and no water leaks while the
workers run — every algorithm then has exactly one correct answer. Assertions are exact equality,
never bounds, so a test cannot pass merely because the scheduler happened to serialize the workers.

| Test | Proves |
|---|---|
| `MultiKeyConcurrencyIsolationTest` | 120 threads across 6 clients, all five algorithms: each client is admitted exactly its own quota — no lost updates, no cross-key leakage |
| `SlidingWindowLogStateIntegrityTest` | The confined mutable deque survives 80-thread contention intact, across repeated runs, including full and partial eviction |
| `RateLimitContextImmutabilityTest` | Deep immutability at both levels, plus consistent reads when one context is published to 40 threads |
| `LuaScriptLoaderConcurrencyTest` | The script cache stays consistent under concurrent reads, concurrent reloads, and reads overlapping reloads |

Per-algorithm same-key contention tests from Sprints 3–7 remain in place and unchanged.

---

## 10. Remaining Work

Deferred by roadmap boundary, not by oversight:

- **Sprint 14 — Synchronization & Locking:** `LockManager`, `ReentrantLock`, `ReadWriteLock`,
  striped per-key locks.
- **Sprint 15 — Lock-Free Improvements:** CAS retry designs, `LongAdder`, concurrency and
  synchronization-strategy benchmarks.
- **Phase 5 packages** `concurrency/`, `executor/`, `stress/`, `benchmark/` and their components
  (`GatewayExecutor`, `ThreadPoolConfiguration`, `StressTestRunner`, `ConcurrentRequestGenerator`,
  `ConcurrencyBenchmark`) span Sprints 13–15 and are not created in Sprint 13.
- **Concurrent metrics collection** and a **thread-safe configuration cache** are Phase 5
  deliverables with no subject in the current codebase: the metrics publisher is a No-Op and no
  configuration cache exists. Building them is new functionality, not thread-safety hardening.
- **Minor, non-thread-safety observation:** `Map.copyOf` iteration order is unspecified and varies
  between JVM runs, so `getFirstHeader` picks arbitrarily between header names that differ only in
  case. Pre-existing, unrelated to concurrency, and out of Sprint 13 scope.

---

## 11. Success Criteria

Sprint 13 is complete when:

- Every piece of shared mutable state is inventoried and has a documented mechanism.
- No unnecessary synchronization is introduced.
- No distributed correctness is delegated to JVM locks.
- Existing algorithm semantics are unchanged.
- Concurrent tests demonstrate correctness with exact assertions.
- The full Maven suite passes.

---

## End of Document
