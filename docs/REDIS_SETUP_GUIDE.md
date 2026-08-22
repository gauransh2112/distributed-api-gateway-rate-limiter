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

## 8. Scope Disclaimer

**Redis-backed storage and rate limiting are not implemented in Sprint 9. They begin in Sprint 10.**
