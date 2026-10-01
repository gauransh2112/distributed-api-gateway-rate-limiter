# Distributed Gateway Cluster — Deployment Guide

**Sprint:** 16 — Distributed Gateway Cluster
**Version:** 1.0
**Scope:** Clustered deployment of the Gateway. Operational documentation only.

---

## 1. Purpose

Sprint 16 turns the Gateway from a single-node application into a horizontally scalable cluster:
three Gateway instances, one shared Redis, container-level health checks, load distribution through
Docker's embedded DNS, and automatic failover.

Implements **Development Playbook Phase 6 — Distributed Gateway Cluster**.

What makes this possible was built earlier: the Gateway keeps **no** rate limiting state in its own
heap. Every instance reads and writes the same Redis, which is why any instance can serve any
request and why instances are interchangeable.

---

## 2. What this sprint deliberately does NOT include

Three components the Playbook lists under Phase 6 are **deferred to Phase 7 / Sprint 18**:

```
HealthAggregator      NodeHealthChecker      NodeRegistry
```

They were deferred because the repository's own documentation already places cluster health
aggregation in Phase 7, not Phase 6:

| Evidence | Location |
|---|---|
| `GET /health/cluster` endpoint defined | `Development_Playbook.md` Phase 7 |
| "Health Aggregation" section | `Development_Playbook.md` Phase 7 |
| "Cluster health aggregation works" — Definition of Done | `Development_Playbook.md` Phase 7 |

Pulling them into Sprint 16 would have required inventing either an undocumented API endpoint or an
undocumented peer-topology configuration (and `API_specification.md` documents no cluster or node
endpoint at all). Neither was acceptable, so the cluster uses **container-level** health checks for
failover instead.

**The distinction matters:**

```
Container health   — "is this instance serving?"        -> Sprint 16, what an orchestrator needs
Cluster health     — "what is the state of all nodes?"  -> Phase 7 / Sprint 18
```

---

## 3. Topology

```
                      ┌─────────────┐
                      │  gateway-1  │──┐
                      ├─────────────┤  │
    DNS: "gateway" ───│  gateway-2  │──┼──▶  redis  (shared state)
                      ├─────────────┤  │
                      │  gateway-3  │──┘
                      └─────────────┘
                                         backend  (placeholder origin)
```

All services sit on one user-defined bridge network, `gateway-net`, and address each other by
service name. No host networking, no hardcoded addresses.

---

## 4. Components

| Artifact | Role |
|---|---|
| `Dockerfile` | Runtime image for the Gateway |
| `docker/docker-compose.cluster.yml` | Three-node cluster, shared Redis, backend, network |
| `docker/docker-compose.yml` | **Unchanged** — Redis alone, for local development |

The two compose files are separate on purpose. `docker-compose.yml` is the Redis-only development
environment documented in the Redis Setup Guide; that workflow is untouched.

### The image

One image runs every instance. It carries no identity of its own — identity arrives through the
environment, which is what lets the cluster scale by adding containers rather than by changing code
(*"No node-specific code"*, per the Playbook's Configuration Strategy).

The jar is **built on the host and copied in**, rather than compiled inside the image. The
repository's build already produces an executable Spring Boot jar, and a build stage would
re-resolve every Maven dependency over the network on each image build — slower, and it fails
outright in restricted environments. The image installs **no packages**: its health check uses the
BusyBox `wget` already present in `eclipse-temurin:21-jre-alpine`, so the build needs no network
access at all. The container runs as an unprivileged `gateway` user.

---

## 5. Gateway instance identity

Identity comes from the environment, per the **Gateway Identity** category the Configuration
Reference assigns to the Gateway package:

```yaml
environment:
  GATEWAY_INSTANCE_ID: gateway-1
```

It appears in every log line, so output from a cluster can be attributed to the instance that
produced it:

```
2026-10-01T06:04:16.065Z  INFO [gateway-1] [main] c.g.g.bootstrap.GatewayApplication : Started ...
2026-10-01T06:04:15.192Z  INFO [gateway-2] [main] c.g.g.bootstrap.GatewayApplication : Started ...
2026-10-01T06:04:15.330Z  INFO [gateway-3] [main] c.g.g.bootstrap.GatewayApplication : Started ...
```

**No Java code reads it, and no API contract changed.** It is a logging-pattern placeholder in
`application.yml` with a default (`gateway-local`), so single-instance runs are unaffected. Exposing
identity through an API response would have meant altering a documented contract for no operational
gain.

---

## 6. Running the cluster

```bash
# 1. Build the jar (the image copies it in)
mvn package -DskipTests

# 2. Validate and build
docker compose -f docker/docker-compose.cluster.yml config
docker compose -f docker/docker-compose.cluster.yml build

# 3. Start
docker compose -f docker/docker-compose.cluster.yml up -d

# 4. Confirm every service is healthy
docker compose -f docker/docker-compose.cluster.yml ps
```

Expected:

```
NAME                      STATUS
gateway-1                 Up (healthy)
gateway-2                 Up (healthy)
gateway-3                 Up (healthy)
gateway-cluster-backend   Up (healthy)
gateway-cluster-redis     Up (healthy)
```

Each instance is published on the host for inspection:

```
gateway-1 -> http://localhost:18081
gateway-2 -> http://localhost:18082
gateway-3 -> http://localhost:18083
```

Tear down with `docker compose -f docker/docker-compose.cluster.yml down`.

---

## 7. Load distribution

All three Gateways share the network alias **`gateway`**, so Docker's embedded DNS returns every
instance's address and rotates between them:

```bash
docker run --rm --network gateway-cluster_gateway-net busybox nslookup gateway
#  Address: 172.18.0.4
#  Address: 172.18.0.5
#  Address: 172.18.0.6
```

**No reverse proxy is introduced.** `ADR-0008` states that because every Gateway behaves
identically the load balancer may freely distribute requests — round robin, least connections,
weighted or random — and that **no sticky sessions are required**. The architecture is deliberately
load-balancer-agnostic, so adding nginx, HAProxy or Traefik would have introduced an undocumented
infrastructure dependency for no architectural requirement. Docker DNS satisfies the documented
design; a production deployment may place any balancer in front without application changes.

---

## 8. Health checks and failover

Each Gateway container health-checks itself against the existing `/health` endpoint:

```
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=3
```

`/health` is reused exactly as documented — **its API contract is unchanged**.

When an instance stops, the remaining instances keep serving, and the failed instance is dropped
from the `gateway` DNS rotation:

```
gateway-1  ✗ stopped        ->  unreachable
gateway-2  ✓ healthy        ->  HTTP 200
gateway-3  ✓ healthy        ->  HTTP 200
```

Restarting it returns it to service without any coordination step:

```bash
docker start gateway-1
# gateway-1 health: healthy      serving: HTTP 200
```

Failover and recovery are **infrastructure-level**. No application-level failover logic exists,
because the documented architecture does not require any: the instances are stateless and
interchangeable, so there is nothing to hand over.

---

## 9. Shared Redis

One Redis serves the whole cluster, declared once in the compose file. Its port is **not** published
to the host — it is reachable only on the internal network, so nothing outside the cluster can read
or write rate limiting state.

The Gateways reuse the existing Redis integration unchanged: `RedisService`, the key schema, all five
Lua scripts, and the failure policy from Sprint 21's work. **No new Redis keys, data structures, TTL
semantics, Lua scripts or coordination mechanisms were introduced.** In particular there is **no
Redis-backed node registry** — node registration would have been new, undocumented distributed state.

Verification that the scripts load against the shared instance:

```
c.g.g.redis.script.LuaScriptLoader : Lua script loader initialized with 5 script(s)
```

### Shared quota enforcement — the acceptance criterion

A cluster is only correct if the instances enforce **one** quota rather than one each. Verified on the
running cluster with `capacity = 5`, `FIXED_WINDOW`, `redis.enabled=true`, requests round-robined
across all three instances:

```
req 1 -> gateway-1 -> 200     req 6 -> gateway-3 -> 429
req 2 -> gateway-2 -> 200     req 7 -> gateway-1 -> 429
req 3 -> gateway-3 -> 200     req 8 -> gateway-2 -> 429
req 4 -> gateway-1 -> 200     req 9 -> gateway-3 -> 429
req 5 -> gateway-2 -> 200

allowed = 5   rejected = 4
```

Five admitted in total — **not five per instance**. The rejections are issued by different instances
from the ones that consumed the quota, which is the property that distinguishes shared state from
per-instance state.

The counter exists in Redis under the existing contract schema, with the documented TTL:

```
dev:ratelimiter:fixed:<clientId>:<windowStart> = 9   ttl = 69s
```

Documented headers appear on real responses:

```
200 ->  X-RateLimit-Limit: 5   X-RateLimit-Remaining: 4   X-RateLimit-Reset: 1790838120
429 ->  X-RateLimit-Limit: 5   X-RateLimit-Remaining: 0   Retry-After: 31
```

With one instance stopped, the survivors still enforce the same total of five. A restarted instance
rejects traffic against a quota **it never served**, because quota lives in Redis rather than in any
instance's heap.

> **Note on wiring.** Reaching this required a wiring fix found by deploying the application: the
> five in-memory strategies had been registered as Spring beans and `NoOpRateLimiter` carried
> `@Primary`, so `RateLimitFilter` received the no-op limiter and the packaged Gateway enforced
> nothing regardless of configuration. ADR-0007 assigns algorithm selection to a factory; the
> strategies are no longer beans, so the factory owns selection as documented. A Spring wiring test
> and an end-to-end HTTP test now guard against the regression returning.

---

### Redis failure

Stopping Redis does not crash any Gateway — matching `ADR-0008`'s *"Redis failures should never
crash the application"*:

```
redis stopped -> gateway-1/2/3 still running, restart count 0, /health still HTTP 200
redis started -> cluster resumes with no restart and no reconfiguration
```

The rate limiter's own failure policy (`FAIL_OPEN` / `FAIL_CLOSED`, ADR-0016) is unchanged by this
sprint.

---

## 10. The `backend` service

The Playbook's compose topology lists a `backend` service, so one is present. It is a minimal static
HTTP origin and is **deliberately inert**: the Gateway does not route to it, because the `routing`
package — part of the approved package structure — is not yet implemented, so the Gateway currently
terminates requests itself.

It exists so the documented topology is complete and so routing has a target once that package
lands. Wiring it up now would have meant inventing undocumented proxy behaviour.

---

## 11. Architecture compliance

| Constraint | Status |
|---|---|
| No new top-level Java packages | ✅ `bootstrap`, `observability`, `ratelimiter`, `redis`, `shared` unchanged |
| No `cluster/`, `deployment/`, new `health/` packages | ✅ none created |
| No new API endpoints | ✅ `/health` and `/ready` reused as documented |
| No new configuration contracts | ✅ identity uses the documented Gateway Identity category |
| No new Redis keys / structures / scripts | ✅ Redis integration untouched |
| No new distributed coordination | ✅ none |
| No external load balancer | ✅ Docker DNS only |
| Health logic ownership | ✅ remains under `observability` |

**No Java source file was added or modified by this sprint.** Sprint 16 is deployment
infrastructure, configuration and documentation — which is the correct outcome once the three
cluster-health components are deferred, because the application was already stateless and
already proven correct across instances.

---

## 12. Known limitations

- **Cluster health aggregation is not implemented** — deferred to Phase 7 / Sprint 18 with its
  documented `GET /health/cluster` endpoint. There is currently no single place to ask "what is the
  state of every node?"; each instance answers only for itself.
- **No application-level failover logic.** Failover relies on the orchestrator and DNS. Sufficient
  for stateless instances, and the documented architecture asks for nothing more.
- **The `backend` service is not routed to**, as described in section 10.
- **Load and scaling behaviour is not measured here.** Throughput, latency, failover timing and
  3-vs-5-node comparisons belong to Sprint 17 — Horizontal Scaling & Load Testing.
- **Scaling beyond three instances requires adding services** to the compose file. The application
  needs no change, which is the property being demonstrated, but this compose file enumerates
  instances explicitly rather than using replicas, because the Playbook's documented topology names
  `gateway-1`, `gateway-2` and `gateway-3`.
