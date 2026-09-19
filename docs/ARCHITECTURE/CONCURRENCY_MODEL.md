# Concurrency Model

Version: 1.2

Status: Implemented (Sprint 13 — Thread Safety; Sprint 14 — Synchronization & Locking).
Sprint 15 — Lock-Free Improvements: measurement stage complete, optimization decision pending review.

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
| `PolicySnapshotConsistencyTest` | All five algorithms resolve the policy exactly once per request, evaluate against one snapshot, and hold that one-to-one ratio under 40 concurrent requests |

Per-algorithm same-key contention tests from Sprints 3–7 remain in place and unchanged.

---

## 10. Synchronization & Locking Decision (Sprint 14)

**Version 1 introduces no application-level locks.** This is an architectural position recorded in
`REDIS_DESIGN.md`, not a shortcut:

> *Application-Level Locks — Version 1 Does Not Use `synchronized`, `ReentrantLock`,
> `ReadWriteLock`. Reason: Application locks work only inside one JVM. They cannot coordinate
> multiple Gateway instances.*

> *Distributed Locks — Version 1 Does Not Use Redis Distributed Locks… Lua scripts already provide
> the required atomicity. Distributed locks would increase latency, complexity, operational
> overhead without additional benefit.*

`Engineering_contracts.md` states the same for the rate limiter — *"Distributed synchronization
relies on Redis, not JVM locks"* — and for observability — *"Lock-free whenever practical"*.
The reserved key prefix `gateway:locks` appears in `REDIS_DESIGN.md` only under **Future
Evolution**, confirming distributed locking is out of scope for Version 1.

The reasons, stated plainly:

- **JVM locks are process-local.** A lock is an object header or an AQS state word in one process's
  heap. Two Gateway instances have two heaps; instance A's lock constrains nothing in instance B.
- **They cannot coordinate multiple instances.** The moment the Gateway scales past one replica,
  JVM locking gives the *illusion* of coordination while shared state is corrupted exactly as before.
- **`ConcurrentHashMap.compute` already provides the local atomicity a lock would supply.** It holds
  the bin lock for the key across the whole read-calculate-write sequence.
- **Immutable state provides safe publication.** A swapped-in record is either fully visible or not
  visible at all; no `volatile` and no lock is needed for visibility.
- **Redis and Lua provide distributed atomicity.** That boundary is established in Sprints 11–12 and
  is not re-implemented with JVM constructs.

Verified at the time of writing: `src/main` contains zero locking constructs. The only occurrence of
the word `synchronized` anywhere in production code is the sentence *"This class is not internally
synchronized"* in the `SlidingWindowLog` Javadoc.

---

## 11. Lock Granularity Analysis

Evaluated for completeness. None is implemented, because each is either redundant with mechanisms
already in place or protects a mutation that does not occur.

| Option | Contention | Memory | Scalability | Isolation | Failure modes | Verdict |
|---|---|---|---|---|---|---|
| Global lock | Severe — every client serialized | O(1) | Does not scale | None | Convoy effect; one slow client blocks all | Rejected |
| Per-key lock | Low | O(active keys), **unbounded** | Good | Full | Lock-map eviction is itself a concurrency problem; leaks without it | Rejected |
| Striped lock | Low, bounded | O(stripes) | Good | Approximate — hash collisions share a stripe | False sharing between unrelated keys | Rejected |
| `ReadWriteLock` | Low for reads | O(1) | Good read scaling | N/A | Writer starvation; pointless where no writer exists | Rejected |

The decisive point: **`ConcurrentHashMap` already is a striped lock.** Its bin-level locking is
precisely the per-key/striped design above, implemented in the JDK, with the lock-lifecycle problem
already solved. A `LockManager` layered on top would re-implement the JDK's own mechanism — less
tested, with a lock-eviction bug waiting to be written — and would nest a second lock inside the
first.

Three specific candidates and why each was rejected:

- **`LuaScriptLoader.scriptCache`** — read-mostly with rare writes, the textbook `ReadWriteLock`
  case. `ConcurrentHashMap` already gives lock-free reads *and* atomic writes, and `reload()` is
  idempotent (same body ⇒ same SHA), so concurrent writers converge. A lock would add blocking to
  the hot read path to protect a write that cannot conflict.
- **`RateLimiterProperties`** — the classic config-hot-reload `ReadWriteLock` case. There is no
  hot-reload: nothing calls a setter at runtime. A lock would protect a mutation that does not exist.
- **`SlidingWindowLog`'s mutable deque** — a mutable object in a shared map, the classic "needs a
  lock" shape. It is already inside one: the CHM bin lock held by `compute`.

---

## 12. Deadlock Analysis

**No explicit lock infrastructure exists, so there is no application-level deadlock surface.**

- **Lock ordering:** a lock-ordering problem requires at least two locks. There are zero.
- **Nested locks:** none. The only implicit lock is the CHM bin lock held inside `compute`, and no
  remapping function acquires anything else — none calls another `compute`, another limiter, or
  Redis. Verified by reading all five remapping functions.
- **Lock cycles:** none.
- **Lock retention / leakage:** none — there is no `lock()` without `unlock()` because there is no
  `lock()`.

One genuine blocking point does exist on the request path and is worth naming, because it is where a
request thread can actually stall: the Lettuce network call to Redis. It is bounded by
`application.yml` (`connect-timeout: 2000ms`, `timeout: 2000ms`), not by a lock, and no JVM lock
would improve it.

This analysis holds only while the zero-lock property holds. Introducing a single lock reintroduces
the entire deadlock surface and invalidates this section.

---

## 13. Policy Snapshot Consistency (Sprint 14)

**Each request evaluation must resolve its policy exactly once and evaluate against that one
snapshot.**

Every algorithm previously called `RateLimitPolicyResolver.resolvePolicy(context)` **twice** per
request — once for capacity, once for the window or rate — then used both results as though they
were one policy:

```
allowRequest(context)
   |
   +-- resolvePolicy(context) -> capacity        (policy version A)
   +-- resolvePolicy(context) -> window / rate   (policy version B)
```

A resolver backed by live-reloading configuration could return a different policy version between
the two calls, producing a decision matching **no configured policy**: capacity from one version
combined with a window from another. The corrected flow resolves once:

```
allowRequest(context)
   |
   v
resolvePolicy(context)
   |
   v
RateLimitPolicy snapshot
   |
   +---- capacity
   +---- window / duration
   +---- refill or leak rate
```

**This was fixed by restructuring, not by locking.** The resolver is an externally injected
strategy; a limiter cannot lock state it does not own, and the defect is a compound *read*, not a
data race. Reading once is both the simpler and the correct answer.

Exposure was latent: `RateLimitPolicyResolver` has no implementation in `src/main` and production
wiring passes `null`, so both branches were unreachable in practice. It is fixed because the
guarantee is what a future policy store will rely on.

`PolicySnapshotConsistencyTest` pins it down across all five algorithms — exactly one resolution per
request, one snapshot per decision, and one-to-one resolution under 40 concurrent requests. All 15
of its cases fail against the previous two-call implementation.

---

## 14. Contention Measurement (Sprint 15 — measurement stage)

Sprints 13 and 14 established that the concurrency design is *correct*. Neither measured how it
*behaves* under contention: the Sprint 8 harness is single-threaded by construction and says so in
its own report — *"multi-threaded lock contention is evaluated in Phase 5"*. Sprint 15 closes that
gap before considering any lock-free change, because the repository rule is measure first,
optimize second.

### Methodology

`ContentionBenchmarkRunner` (test scope, measurement infrastructure, asserts nothing) releases N
workers simultaneously against one limiter instance and records per-operation latency.

| Parameter | Value |
|---|---|
| Subject | `ConcurrentHashMap.compute()` — the per-key atomic state transition |
| Thread counts | 8, 32, 64 |
| Operations per thread | 10,000 |
| Warm-up | 2,000 ops per thread, discarded, on a separate limiter instance |
| Runs per configuration | 3 (mean and standard deviation reported) |
| Key distributions | same-key (one shared key) and multi-key (one key per thread) |
| Clock | fixed — no window rolls, no refill, no leak during a run |
| Capacity | 1,000,000,000 — every request admitted, so every thread takes the state-writing path |
| Coordination | ready latch + start latch; the timer starts only after every worker has parked |
| Environment | OpenJDK 21.0.10, Linux x86-64, **4 available processors** |

Contexts are built before the measured region, so context construction (which performs a deep
header copy) is excluded. Full data: `benchmarks/reports/contention_benchmark_report.md`.

### Results

Aggregate throughput, mean of 3 runs:

| Algorithm | Same-key @64 | Multi-key @64 | Multi-key advantage | Same-key 8→64 threads | Avg latency 8→64 threads |
|---|---:|---:|---:|---|---|
| Fixed Window | 3,432,501 ops/s | 13,832,671 ops/s | **4.0×** | +15% | 1.94 → 11.27 µs |
| Sliding Window Counter | 2,433,360 | 10,905,182 | **4.5×** | +26% | 3.07 → 18.19 µs |
| Sliding Window Log | 1,760,076 | 7,711,305 | **4.4×** | +15% | 4.30 → 26.65 µs |
| Token Bucket | 1,880,582 | 10,378,879 | **5.5×** | +1% | 3.62 → 25.42 µs |
| Leaky Bucket | 1,852,299 | 9,744,605 | **5.3×** | +20% | 4.44 → 25.72 µs |

Across all 90 configurations and 31.2 million operations: **zero errors**.

### Interpretation

**1. Fine-grained striping works.** Independent keys sustain 3.6–5.7× the throughput of a single
shared key, in every algorithm at every thread count. `ConcurrentHashMap`'s bin-level locking
delivers the per-key parallelism the design depends on — the property a hand-written lock manager
would have had to reproduce.

**2. Same-key contention saturates but does not collapse.** Multiplying threads by eight changes
same-key throughput by between +1% and +26% — flat, not degraded. There is no convoy effect, no
livelock, no throughput cliff. This is the expected signature of a saturated critical section.

**3. Latency growth is queueing, not pathology.** Under same-key load, average latency rises
roughly linearly with thread count while throughput stays flat — exactly what Little's Law predicts
for a saturated serialization point. Median latency barely moves (Fixed Window: 0.50 → 0.53 µs),
so the growth is waiting time, not slower work.

**4. The same-key ceiling is a correctness requirement, not an implementation artifact.** One key's
state transition must be atomic, so it is serialized by definition. CAS would not remove that
serialization — it would convert waiting into retrying, and for these time-dependent algorithms a
retry re-reads the clock and recomputes a different result (section 11).

**5. The absolute numbers leave ample headroom.** The slowest algorithm sustains roughly 1.5 million
decisions per second against a single hot key. A gateway serving 10,000 requests per second to one
client would use well under 1% of that.

### Was critical-section narrowing demonstrated?

**No.** The hypothesis was that Token Bucket, Leaky Bucket and Sliding Window Log allocate
`Instant`/`Duration` objects inside the remapping function while Fixed Window does not, lengthening
their critical sections. Fixed Window is indeed the fastest algorithm under same-key contention
(3.4M vs 1.9M ops/s), but **that comparison cannot attribute the difference to allocation
placement**: Fixed Window also does far less arithmetic, inside and outside the lock, and it leads
by a similar margin in the *multi-key* case where contention is not the limiter. The two variables
are confounded.

Isolating the effect would require building a narrowed variant of one algorithm and measuring it
against the current one — a production change. Since the measurement does not demonstrate a
benefit, the code stays unchanged.

### Limitations

These numbers describe this workload on this machine. They are an engineering measurement, not a
general performance claim.

1. **Only 4 available processors.** At 32 and 64 threads the machine is heavily oversubscribed, so
   OS scheduling contributes alongside lock contention. The average-versus-P99 inversion under
   same-key load (avg 11.27 µs, P99 2.46 µs for Fixed Window at 64 threads) shows a small number of
   descheduled operations dominating the mean.
2. **Timing overhead.** Two `System.nanoTime()` calls wrap each operation, which is material at
   these latencies. It is constant across configurations, so comparisons hold while absolute values
   are inflated.
3. **Run-to-run variance.** Same-key measurements are stable (coefficient of variation 2.6–4.7% at
   64 threads); multi-key measurements are noisier (up to 34%). The 4–5× same-key/multi-key
   separation is far larger than the noise, but smaller multi-key differences should not be read as
   significant.
4. **Fixed clock.** Isolates the concurrency mechanism; does not represent production time
   progression.
5. **Sliding Window Log accumulates state.** With a fixed clock nothing expires, so its deque grows
   through the run and its numbers include costs the others do not incur.
6. **Not JMH.** No fork isolation, dead-code-elimination guards, or blackholes.
7. **One implementation measured.** No competing implementation was built, deliberately — a
   benchmark against an architecturally rejected design would not be useful evidence.

### Conclusion of the measurement stage

The data supports **Case A**: the current implementation behaves acceptably under every contention
pattern measured. No bottleneck was found, so **no production optimization is justified on this
evidence**, and `ConcurrentHashMap.compute()` remains the production mechanism. Sprint 15 is not
closed by this section; the optimization decision rests with architecture review.

---

## 15. Remaining Work

Deferred by roadmap boundary, not by oversight:

- **Sprint 14 — Synchronization & Locking:** completed. The outcome was that no application-level
  locking is warranted; see section 10. `LockManager`, `ReentrantLock`, `ReadWriteLock` and striped
  per-key locks were evaluated and rejected, not deferred.
- **Sprint 15 — Lock-Free Improvements:** measurement stage complete (section 14). CAS retry
  designs and `LongAdder` were analysed and found unjustified; the contention benchmark found no
  bottleneck. Any production optimization awaits architecture review.
- **Phase 5 packages** `concurrency/`, `executor/`, `stress/`, `benchmark/` and their components
  (`GatewayExecutor`, `ThreadPoolConfiguration`, `StressTestRunner`, `ConcurrentRequestGenerator`,
  `ConcurrencyBenchmark`) span Sprints 13–15 and are not created in Sprints 13 or 14.
- **Concurrent metrics collection** and a **thread-safe configuration cache** are Phase 5
  deliverables with no subject in the current codebase: the metrics publisher is a No-Op and no
  configuration cache exists. Building them is new functionality, not thread-safety hardening.
- **Minor, non-thread-safety observation:** `Map.copyOf` iteration order is unspecified and varies
  between JVM runs, so `getFirstHeader` picks arbitrarily between header names that differ only in
  case. Pre-existing, unrelated to concurrency, and outside both Sprint 13 and Sprint 14 scope.

---

## 16. Success Criteria

Sprint 13 is complete when:

- Every piece of shared mutable state is inventoried and has a documented mechanism.
- No unnecessary synchronization is introduced.
- No distributed correctness is delegated to JVM locks.
- Existing algorithm semantics are unchanged.
- Concurrent tests demonstrate correctness with exact assertions.
- The full Maven suite passes.

Sprint 14 is complete when:

- The locking question is answered from repository evidence rather than by adding locks.
- No application-level lock is introduced.
- No distributed correctness is delegated to JVM synchronization.
- Each request evaluates against exactly one policy snapshot.
- The full Maven suite passes.

---

## End of Document
