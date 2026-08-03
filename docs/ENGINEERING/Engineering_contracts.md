# Engineering Contracts

# Distributed API Gateway + Rate Limiter

Version: 1.0

---

# Purpose

Engineering Contracts define the ownership boundaries of every major module in the project.

While

- Product Requirements define **what** to build,
- Architecture defines **how the system is organized**,
- ADRs explain **why decisions were made**,

Engineering Contracts define

**who owns what.**

They are the authoritative source for module responsibilities.

---

# Why Engineering Contracts?

As projects grow, one of the biggest causes of technical debt is unclear ownership.

Questions like

- Should Authentication access Redis?
- Should Routing know JWT?
- Can Dashboard call Redis?
- Where should metrics be generated?

should never require discussion.

The answer should already exist.

Engineering Contracts eliminate ambiguity.

---

# Objectives

Every contract should clearly define

- Module Purpose
- Responsibilities
- Non-Responsibilities
- Public Interfaces
- Internal Components
- Dependencies
- Forbidden Dependencies
- Configuration
- Events
- Testing Requirements
- Performance Expectations
- Security Requirements
- Future Evolution

---

# Engineering Philosophy

Each module should own

**one business capability.**

Every capability should have

- one owner
- one boundary
- one public interface

No capability should have multiple owners.

---

# Ownership Principle

Every class belongs to exactly one module.

Example

```
JwtService

↓

Authentication
```

---

```
TokenBucketStrategy

↓

Rate Limiter
```

---

```
RouteResolver

↓

Routing
```

Ownership must always be obvious.

---

# Dependency Philosophy

Modules communicate only through clearly defined interfaces.

Dependencies should always move

```text
Controller

↓

Application

↓

Domain

↓

Infrastructure
```

Feature ownership remains independent of dependency direction.

---

# Contract Hierarchy

Contracts have the following authority.

```
Engineering Contract

↓

Implementation

↓

Tests

↓

Documentation
```

If implementation violates a contract,

the implementation is incorrect.

---

# Contract Structure

Every Engineering Contract follows the same template.

---

## 1. Purpose

Why does this module exist?

---

## 2. Responsibilities

What this module owns.

---

## 3. Non-Responsibilities

What this module must never own.

---

## 4. Public API

Classes and interfaces exposed to other modules.

---

## 5. Internal Components

Private implementation details.

---

## 6. Dependencies

Which modules this module may depend on.

---

## 7. Forbidden Dependencies

Modules this module must never know about.

---

## 8. Configuration

Configuration owned by the module.

---

## 9. Events

Events produced or consumed.

---

## 10. Testing Requirements

Required test coverage.

---

## 11. Performance Requirements

Latency

Memory

Concurrency

Scalability

---

## 12. Security Requirements

Authentication

Authorization

Secrets

Validation

---

## 13. Definition of Done

Completion criteria.

---

## 14. Future Scope

Planned evolution.

---

# Repository Contracts

Version 1 contains the following contracts.

| Contract | Responsibility |
|------------|----------------|
| AUTHENTICATION | User Authentication & JWT |
| ROUTING | Route Resolution & Request Forwarding |
| RATE_LIMITER | Request Rate Limiting |
| REDIS | Distributed State Management |
| GATEWAY | Request Processing Pipeline |
| DASHBOARD | Monitoring UI |
| OBSERVABILITY | Logging, Metrics & Health |
| CONFIGURATION | Application Configuration |

Every major module in the repository should have exactly one contract.

---

# Rules for AI

When implementing a feature

The AI must

✓ Read the relevant Engineering Contract

↓

✓ Follow ownership boundaries

↓

✓ Respect forbidden dependencies

↓

✓ Update documentation

↓

✓ Write required tests

↓

✓ Stop

The AI must never infer ownership from existing code.

The Engineering Contract is the source of truth.

---

# Rules for Developers

Before creating a new class ask

> Which Engineering Contract owns this?

If the answer is unclear,

the architecture should be reviewed before implementation begins.

---

# Contract Review Checklist

Every contract should answer

- Why does this module exist?
- What business capability does it own?
- What classes belong here?
- What classes do not belong here?
- Which modules can depend on it?
- Which modules must never depend on it?
- What tests are required?
- What performance guarantees exist?
- What security rules apply?
- What future changes are expected?

If any question cannot be answered,

the contract is incomplete.

---

# Contract Evolution

Engineering Contracts are living documents.

They must be updated whenever

- ownership changes
- architecture changes
- public APIs change
- responsibilities change
- security boundaries change

Implementation should never evolve faster than its contract.

---

# Relationship with Other Documentation

```text
Vision

↓

Product Requirements

↓

Engineering Specification

↓

Architecture

↓

ADRs

↓

Engineering Contracts

↓

Development Playbook

↓

Implementation

↓

Tests
```

Engineering Contracts bridge architecture and implementation.

---

# Success Criteria

The Engineering Contract section is successful if

- Every module has a clearly defined owner.
- No responsibility overlaps exist.
- AI can implement modules without guessing ownership.
- New contributors understand the repository quickly.
- Architectural drift is minimized over time.

---

# End of README


# Engineering Contract — Authentication Module

Module: Authentication

Version: 1.0

Status: Active

Owner: Security Domain

---

# Purpose

The Authentication module is responsible for verifying user identity before a request enters the Gateway pipeline.

Its responsibility ends once the user's identity has been established and stored inside the Security Context.

Authentication answers one question:

> **Who is making this request?**

It never answers

> **What is this user allowed to do?**

Authorization is a separate responsibility.

---

# Business Goal

Provide secure, stateless authentication for every request entering the Gateway while supporting horizontal scaling and high throughput.

---

# Core Responsibilities

The Authentication module owns:

✓ User Login

✓ User Registration (if implemented)

✓ JWT Generation

✓ JWT Validation

✓ Token Parsing

✓ Password Hashing

✓ Security Context Population

✓ Authentication Failure Handling

✓ Authentication Configuration

✓ Authentication Filters

---

# Explicit Non-Responsibilities

Authentication never owns

✗ Authorization

✗ Role Evaluation

✗ Permission Checking

✗ Route Resolution

✗ Rate Limiting

✗ Redis State

✗ Dashboard

✗ Metrics Aggregation

✗ Request Forwarding

✗ Business Logic

---

# Module Boundaries

```text
Client

↓

Authentication

↓

Security Context

↓

Authorization

↓

Gateway

↓

Backend
```

Authentication ends immediately after identity verification.

---

# Owned Packages

```
authentication/

controller/

service/

jwt/

filter/

dto/

model/

config/

exception/
```

Everything related to authentication belongs here.

---

# Public API

The module exposes

```
AuthenticationService

JwtService

AuthenticationFacade
```

Other modules must never bypass these public entry points.

---

# Internal Components

Internal implementation classes include

```
JwtProvider

JwtValidator

JwtParser

PasswordEncoder

AuthenticationFilter

AuthenticationExceptionHandler
```

These classes are implementation details and should not be used directly outside the module.

---

# Primary Classes

Expected Version 1 classes

```
AuthenticationController

AuthenticationService

JwtService

JwtTokenProvider

JwtValidator

JwtAuthenticationFilter

AuthenticationRequest

AuthenticationResponse

RegisterRequest

RegisterResponse

AuthenticationException

AuthenticationConfiguration
```

Future classes

```
RefreshTokenService

KeyRotationService

OAuthProvider

OpenIdProvider
```

---

# Interfaces

Example

```
AuthenticationService

↓

AuthenticationServiceImpl
```

Interfaces should exist only where abstraction provides value.

Avoid unnecessary interfaces.

---

# Entry Points

HTTP

```
POST /login

POST /register
```

Internal

```
validateToken()

generateToken()

authenticate()

extractClaims()
```

---

# Exit Points

Successful authentication produces

```
Security Context

↓

Authenticated Principal

↓

JWT Claims

↓

Continue Gateway Pipeline
```

Failed authentication produces

```
401 Unauthorized
```

---

# Dependencies

Authentication may depend on

✓ Spring Security

✓ BCrypt

✓ Configuration

✓ Validation

✓ Logging

✓ Exception Module

---

# Forbidden Dependencies

Authentication must never depend on

✗ Routing

✗ Rate Limiter

✗ Dashboard

✗ Metrics

✗ Redis Algorithms

✗ Gateway Routing

✗ WebClient

---

# Data Ownership

Authentication owns

```
JWT

Claims

Credentials

Authentication Requests

Authentication Responses
```

Authentication does NOT own

```
Business Users

Orders

Gateway Metrics

Route Definitions

Rate Limit Counters
```

---

# JWT Responsibilities

JWT generation

↓

JWT validation

↓

Claim extraction

↓

Expiration validation

↓

Signature verification

JWT must remain completely encapsulated.

Other modules should never manually parse JWTs.

---

# Password Policy

Passwords

Must

✓ Use BCrypt

✓ Be hashed immediately

✓ Never be logged

✓ Never be returned

✓ Never be stored in plaintext

---

# Security Context

Authentication owns creation of

```
SecurityContext

AuthenticationPrincipal
```

Authorization consumes this information.

Authentication never performs permission checks.

---

# Configuration Ownership

Authentication owns

```
JWT Secret

Expiration Time

BCrypt Strength

Authentication Filter

Security Configuration
```

These values must come from external configuration.

---

# Exception Ownership

Authentication owns

```
InvalidTokenException

ExpiredTokenException

AuthenticationFailedException

InvalidCredentialsException
```

Exceptions remain module-specific.

---

# Logging Responsibilities

Log

✓ Login Success

✓ Login Failure

✓ Invalid JWT

✓ Expired JWT

✓ Invalid Signature

Never log

✗ Password

✗ JWT

✗ Secret

✗ Full Claims

---

# Performance Requirements

JWT Validation

Target

< 5 ms

Authentication should never become the primary Gateway bottleneck.

---

# Concurrency Requirements

Authentication components must remain

✓ Stateless

✓ Thread-safe

✓ Immutable where practical

No authentication state may exist inside application memory.

---

# Testing Requirements

Mandatory Unit Tests

- JWT Generation
- JWT Validation
- Password Hashing
- Expiration Validation
- Invalid Signature
- Invalid Claims

---

Mandatory Integration Tests

- Login Flow
- Protected Endpoint
- Invalid Token
- Missing Token
- Expired Token

---

Mandatory Security Tests

- JWT Tampering
- Invalid Algorithm
- Empty Authorization Header
- Malformed JWT

---

# Error Handling

Authentication failures should produce

```
401 Unauthorized
```

Authentication should never return

```
500 Internal Server Error
```

for invalid credentials.

---

# Invariants

The following must always remain true.

✓ JWT is validated before Gateway processing.

✓ Passwords remain hashed.

✓ Authentication remains stateless.

✓ Authentication never accesses Redis.

✓ Authentication never performs authorization.

✓ JWT parsing remains encapsulated.

---

# Metrics Produced

Authentication publishes

- Login Attempts
- Login Successes
- Login Failures
- JWT Validation Failures
- Authentication Latency

It does not aggregate metrics.

---

# Future Scope

Possible additions

- Refresh Tokens
- OAuth2
- OpenID Connect
- Multi-Factor Authentication
- API Keys
- JWKS
- Key Rotation

The current architecture should support these without redesign.

---

# Definition of Done

The Authentication module is complete when

- Login works.
- JWT generation works.
- JWT validation works.
- Password hashing works.
- Security Context is populated.
- Unit tests pass.
- Integration tests pass.
- Security tests pass.
- Documentation is updated.
- Engineering Review Checklist passes.

---

# Acceptance Checklist

- [ ] Stateless
- [ ] Thread-safe
- [ ] JWT encapsulated
- [ ] BCrypt implemented
- [ ] Security Context populated
- [ ] No Redis dependency
- [ ] No Routing dependency
- [ ] Tests complete
- [ ] Documentation complete

---

# Related Contracts

- GATEWAY.md
- CONFIGURATION.md
- OBSERVABILITY.md

---

# Related ADRs

- ADR-0004 — Constructor Injection
- ADR-0005 — JWT Authentication
- ADR-0010 — Stateless Gateway

---

# Final Contract

The Authentication module owns **identity verification and nothing else**.

It is the exclusive authority for JWT lifecycle management, credential validation, and Security Context creation. It must remain stateless, thread-safe, isolated from business logic, and completely independent of routing, rate limiting, and distributed state.

# Engineering Contract — Routing Module

Module: Routing

Version: 1.0

Status: Active

Owner: Gateway Domain

---

# Purpose

The Routing module is responsible for determining where an incoming request should be forwarded.

It owns everything related to request forwarding after authentication, authorization, and rate limiting have successfully completed.

Routing answers one question:

> **Where should this request go?**

It never answers

> **Who is the user?**

or

> **Should this request be allowed?**

---

# Business Goal

Provide fast, deterministic, and configurable request routing while remaining completely independent of authentication and rate limiting logic.

---

# Core Responsibilities

The Routing module owns

✓ Route Resolution

✓ Route Registry

✓ Backend Service Discovery (Version 1)

✓ Request Forwarding

✓ Header Forwarding

✓ Path Rewriting

✓ Query Parameter Forwarding

✓ HTTP Method Preservation

✓ Response Forwarding

✓ Route Configuration

---

# Explicit Non-Responsibilities

Routing never owns

✗ Authentication

✗ Authorization

✗ JWT

✗ Rate Limiting

✗ Redis

✗ Dashboard

✗ Metrics Aggregation

✗ Logging Infrastructure

✗ Security Context

---

# Module Position

```text
Client

↓

Authentication

↓

Authorization

↓

Rate Limiter

↓

Routing

↓

Backend Service
```

Routing executes only after all security checks succeed.

---

# Owned Packages

```
routing/

resolver/

forwarder/

registry/

config/

dto/

exception/

model/
```

---

# Public API

The Routing module exposes

```
RouteResolver

RouteForwarder

RouteRegistry

RoutingFacade
```

Other modules should interact only through these abstractions.

---

# Internal Components

Internal implementation classes

```
DefaultRouteResolver

WebClientRouteForwarder

InMemoryRouteRegistry

RouteMatcher

PathRewriter

HeaderMapper
```

These classes remain implementation details.

---

# Primary Classes

Expected Version 1 classes

```
Route

RouteDefinition

RouteRegistry

RouteResolver

RouteForwarder

RoutingConfiguration

RouteNotFoundException

ForwardingException

PathMatcher

HeaderMapper
```

Future

```
LoadBalancer

ServiceDiscovery

WeightedRouteResolver

CircuitBreakerForwarder
```

---

# Entry Points

Internal

```
resolve()

forward()

registerRoute()

findRoute()
```

No public HTTP endpoints belong to this module.

Configuration APIs are handled separately.

---

# Exit Points

Successful routing produces

```
Backend Response

↓

Gateway

↓

Client
```

Failure produces

```
404 Route Not Found

or

502 Bad Gateway

or

504 Gateway Timeout
```

---

# Dependencies

Routing may depend on

✓ Configuration

✓ WebClient

✓ DTO

✓ Logging API

✓ Validation

---

# Forbidden Dependencies

Routing must never depend on

✗ Authentication

✗ JWT

✗ Rate Limiter

✗ Redis

✗ Dashboard

✗ SecurityContext

---

# Data Ownership

Routing owns

```
Route Definitions

Backend URLs

Forwarding Metadata

Request Mapping

Route Configuration
```

Routing does NOT own

```
Users

JWT

Permissions

Rate Limit State

Metrics Storage
```

---

# Route Resolution Rules

Every request should resolve exactly one route.

Decision inputs

- HTTP Method
- URI
- Path Pattern
- Route Configuration

Outputs

- Backend Service
- Backend URI

Ambiguous routing is prohibited.

---

# Route Registry

Version 1

```
In-memory Registry
```

Future

```
Database

Redis

Configuration Server
```

The routing module owns the registry regardless of storage implementation.

---

# Request Forwarding Rules

The following must be preserved

✓ HTTP Method

✓ Path

✓ Query Parameters

✓ Headers (except filtered)

✓ Body

Routing must never modify request semantics unless explicitly configured.

---

# Header Policy

Forward

✓ Authorization

✓ Content-Type

✓ Accept

✓ Correlation ID

Remove

✗ Internal Gateway Headers

✗ Hop-by-Hop Headers

Header filtering remains configurable.

---

# Response Policy

Forward

✓ Status Code

✓ Response Body

✓ Response Headers

Routing should avoid altering backend responses.

Gateway-generated responses remain distinguishable.

---

# Timeout Policy

Version 1

Global timeout.

Future

Per-route timeout.

Routing owns timeout configuration.

---

# Error Handling

Unknown Route

↓

404 Not Found

---

Backend Unavailable

↓

502 Bad Gateway

---

Backend Timeout

↓

504 Gateway Timeout

Routing errors should be deterministic and well documented.

---

# Logging Responsibilities

Routing logs

✓ Route Selected

✓ Backend Target

✓ Forwarding Duration

✓ Forwarding Failure

Never log

✗ JWT

✗ Sensitive Request Bodies

✗ Secrets

---

# Performance Requirements

Target Route Resolution

< 1 ms

Target Forwarding Overhead

Minimal compared to backend latency.

Routing should not become the performance bottleneck.

---

# Concurrency Requirements

Routing components must remain

✓ Stateless

✓ Thread-safe

✓ Immutable where possible

Route resolution must not depend on mutable shared memory.

---

# Testing Requirements

Mandatory Unit Tests

- Route Matching
- Path Resolution
- Header Mapping
- Path Rewriting
- Route Registry

---

Mandatory Integration Tests

- Request Forwarding
- Unknown Route
- Backend Timeout
- Invalid Backend
- Header Propagation

---

Mandatory Failure Tests

- Backend Down
- Malformed Route
- Missing Route Configuration

---

# Invariants

The following must always remain true

✓ Routing never authenticates users.

✓ Routing never rate limits requests.

✓ Routing never accesses Redis.

✓ Every request resolves at most one route.

✓ Route forwarding preserves request semantics.

---

# Metrics Produced

Routing publishes

- Total Routed Requests
- Route Distribution
- Average Forwarding Latency
- Backend Errors
- Timeout Count

It does not aggregate metrics.

---

# Future Scope

Possible enhancements

- Service Discovery
- Dynamic Routes
- Weighted Routing
- Blue-Green Routing
- Canary Releases
- Circuit Breakers
- Retry Policies

The architecture should support these without redesign.

---

# Definition of Done

The Routing module is complete when

- Route resolution works.
- Request forwarding works.
- Unknown routes return 404.
- Backend failures are handled.
- Unit tests pass.
- Integration tests pass.
- Documentation is updated.
- Engineering Review Checklist passes.

---

# Acceptance Checklist

- [ ] Stateless
- [ ] Thread-safe
- [ ] Deterministic route resolution
- [ ] Request semantics preserved
- [ ] No Authentication dependency
- [ ] No Redis dependency
- [ ] No Rate Limiter dependency
- [ ] Tests complete
- [ ] Documentation complete

---

# Related Contracts

- GATEWAY.md
- CONFIGURATION.md
- OBSERVABILITY.md

---

# Related ADRs

- ADR-0006 — WebClient over RestTemplate
- ADR-0010 — Stateless Gateway
- ADR-0014 — Package-by-Feature

---

# Final Contract

The Routing module owns **request destination resolution and forwarding exclusively**.

It is the sole authority for route resolution, backend selection, and request forwarding. It must remain deterministic, stateless, independent of authentication and rate limiting, and capable of evolving toward advanced routing features such as service discovery and canary deployments without architectural changes.

# Engineering Contract — Rate Limiter Module

Module: Rate Limiter

Version: 1.0

Status: Active

Owner: Gateway Domain

---

# Purpose

The Rate Limiter module is responsible for determining whether an incoming request should be allowed or rejected based on configured traffic policies.

It protects backend services from abuse, traffic spikes, and unfair resource consumption.

The Rate Limiter answers one question:

> **Can this request proceed right now?**

It never answers

> **Who is the user?**

or

> **Where should this request go?**

---

# Business Goal

Provide accurate, configurable, distributed, and high-performance rate limiting while remaining completely independent of authentication, routing, and transport concerns.

---

# Core Responsibilities

The Rate Limiter owns

✓ Rate Limiting Algorithms

✓ Policy Evaluation

✓ Rate Limit Decision

✓ Client Identification Strategy

✓ Distributed Counter Management

✓ Algorithm Selection

✓ Rate Limit Configuration

✓ Retry-After Calculation

✓ Rate Limit Metadata

---

# Explicit Non-Responsibilities

The Rate Limiter never owns

✗ Authentication

✗ Authorization

✗ JWT

✗ Request Routing

✗ Request Forwarding

✗ Dashboard

✗ HTTP Controllers

✗ WebClient

✗ Business Logic

---

# Module Position

```text
Client

↓

Authentication

↓

Authorization

↓

Rate Limiter

↓

Routing

↓

Backend
```

The module executes only after identity has been established.

---

# Owned Packages

```
ratelimiter/

strategy/

algorithm/

policy/

resolver/

factory/

redis/

metrics/

dto/

exception/
```

---

# Public API

The module exposes

```
RateLimiter

RateLimitingStrategy

PolicyResolver

ClientIdentifierResolver

RateLimiterFacade
```

Other modules should communicate only through these abstractions.

---

# Internal Components

Internal implementation classes

```
TokenBucketStrategy

LeakyBucketStrategy

SlidingWindowCounterStrategy

SlidingWindowLogStrategy

FixedWindowStrategy

RateLimiterFactory

DefaultPolicyResolver

RedisRateLimitStore
```

These remain internal implementation details.

---

# Primary Classes

Expected Version 1 classes

```
RateLimiter

RateLimitingStrategy

RateLimitDecision

RateLimitPolicy

RateLimiterFactory

ClientIdentifierResolver

FixedWindowStrategy

SlidingWindowCounterStrategy

SlidingWindowLogStrategy

TokenBucketStrategy

LeakyBucketStrategy

RateLimitExceededException
```

Future

```
AdaptiveRateLimiter

GeoAwareStrategy

DynamicPolicyManager

HybridRateLimiter
```

---

# Entry Points

Internal

```
allowRequest()

resolvePolicy()

resolveClient()

selectStrategy()
```

There are no HTTP endpoints in this module.

---

# Exit Points

Successful decision

```
ALLOW

↓

Routing
```

Rejected decision

```
BLOCK

↓

429 Too Many Requests
```

---

# Dependencies

The Rate Limiter may depend on

✓ Redis Service

✓ Configuration

✓ Clock / Time Provider

✓ Logging API

✓ Metrics API

---

# Forbidden Dependencies

The Rate Limiter must never depend on

✗ Authentication

✗ JWT

✗ Routing

✗ Dashboard

✗ Controllers

✗ WebClient

✗ SecurityContext

---

# Data Ownership

The Rate Limiter owns

```
Rate Limit Policies

Algorithm Selection

Distributed Counters

Bucket State

Window State

Retry Information
```

It does NOT own

```
User Accounts

JWT Claims

Routes

Business Data

Dashboard State
```

---

# Supported Algorithms

Version 1

✓ Fixed Window

✓ Sliding Window Counter

✓ Sliding Window Log

✓ Token Bucket

✓ Leaky Bucket

Each algorithm must implement the common strategy interface.

---

# Strategy Rules

Every algorithm

Must

✓ Implement `RateLimitingStrategy`

✓ Be stateless

✓ Be independently testable

✓ Be replaceable

Must Never

✗ Access Controllers

✗ Parse HTTP

✗ Know Routing

✗ Know Authentication

---

# Policy Resolution

The module determines

- Requests Per Window
- Window Duration
- Burst Capacity
- Refill Rate

Policies are configuration-driven.

Algorithms never hardcode limits.

---

# Client Identification

Clients may be identified by

Version 1

- User ID
- API Key (future)
- IP Address (optional)

The client identification strategy remains configurable.

---

# Redis Responsibilities

The Rate Limiter delegates storage to Redis.

Flow

```text
RateLimiter

↓

RedisService

↓

Redis
```

Algorithms never access Redis directly.

---

# Lua Script Responsibilities

Algorithms requiring multiple Redis operations

↓

Delegate to Lua Executor

↓

Receive Atomic Decision

The algorithm owns business logic.

Redis owns atomic execution.

---

# Error Handling

If Redis is unavailable

↓

Controlled failure

↓

Gateway logs event

↓

Response determined by configured failure policy

Failure behavior must be configurable.

---

# Logging Responsibilities

Log

✓ Rate Limit Allowed

✓ Rate Limit Blocked

✓ Algorithm Selected

✓ Redis Failure

✓ Policy Loaded

Never log

✗ JWT

✗ Passwords

✗ Secrets

---

# Performance Requirements

Decision latency target

< 2 ms

Algorithm execution

O(1) whenever practical

Redis round trips should be minimized.

---

# Concurrency Requirements

Every algorithm must be

✓ Stateless

✓ Thread-safe

✓ Deterministic

Distributed synchronization relies on Redis, not JVM locks.

---

# Testing Requirements

Mandatory Unit Tests

- Each Algorithm
- Policy Resolution
- Strategy Factory
- Retry Calculation
- Client Resolution

---

Mandatory Integration Tests

- Redis Integration
- Lua Scripts
- Policy Loading
- Multiple Algorithms

---

Mandatory Stress Tests

- High Concurrency
- Burst Traffic
- Sustained Load
- Multiple Gateway Instances

---

Mandatory Benchmark Tests

Compare

- Fixed Window
- Sliding Counter
- Sliding Log
- Token Bucket
- Leaky Bucket

Measure

- Latency
- Throughput
- Memory Usage
- Accuracy

---

# Invariants

The following must always remain true

✓ Exactly one algorithm processes each request.

✓ Policies remain externalized.

✓ Algorithms remain independent.

✓ Rate Limiter never authenticates users.

✓ Rate Limiter never resolves routes.

✓ Distributed state lives in Redis.

---

# Metrics Produced

The Rate Limiter publishes

- Allowed Requests
- Blocked Requests
- Algorithm Usage
- Average Decision Latency
- Redis Operation Count
- Retry-After Distribution

It does not aggregate metrics.

---

# Future Scope

Possible enhancements

- Dynamic Policies
- Per-Tenant Limits
- Machine Learning Policies
- Geo-based Limits
- User Tier Limits
- Adaptive Rate Limiting

The architecture should support these without major refactoring.

---

# Definition of Done

The Rate Limiter module is complete when

- All five algorithms work.
- Policy resolution works.
- Strategy selection works.
- Redis integration works.
- Lua scripts work.
- Unit tests pass.
- Integration tests pass.
- Stress tests pass.
- Benchmark report published.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Acceptance Checklist

- [ ] Stateless
- [ ] Thread-safe
- [ ] Strategy Pattern implemented
- [ ] Policies externalized
- [ ] Redis abstraction respected
- [ ] No Authentication dependency
- [ ] No Routing dependency
- [ ] All algorithms tested
- [ ] Benchmark completed
- [ ] Documentation complete

---

# Related Contracts

- REDIS.md
- GATEWAY.md
- CONFIGURATION.md
- OBSERVABILITY.md

---

# Related ADRs

- ADR-0007 — Strategy Pattern
- ADR-0008 — Redis as Distributed State
- ADR-0009 — Lua Scripts

---

# Final Contract

The Rate Limiter module owns **traffic control and request admission decisions exclusively**.

It is the sole authority for policy evaluation, algorithm execution, and rate limit decisions. It must remain stateless, deterministic, algorithm-independent, and completely isolated from authentication, routing, and transport concerns while supporting distributed execution through Redis.

# Engineering Contract — Redis Module

Module: Redis

Version: 1.0

Status: Active

Owner: Infrastructure Layer

---

# Purpose

The Redis module provides distributed, low-latency, temporary data storage for the Gateway.

It acts as the shared state layer for all Gateway instances.

Redis answers one question:

> **Where should distributed temporary state be stored?**

It never answers

> **How should business logic behave?**

Business decisions belong to the calling modules.

---

# Business Goal

Provide a reliable, atomic, distributed storage layer that enables horizontal scaling while remaining completely independent of business logic.

---

# Core Responsibilities

The Redis module owns

✓ Redis Connection Management

✓ Connection Pooling

✓ Key Management

✓ Key Naming Strategy

✓ TTL Management

✓ Atomic Operations

✓ Lua Script Execution

✓ Redis Health Checks

✓ Serialization

✓ Deserialization

✓ Distributed State Access

---

# Explicit Non-Responsibilities

The Redis module never owns

✗ Authentication

✗ Authorization

✗ Routing

✗ Rate Limiting Decisions

✗ Dashboard

✗ Business Rules

✗ Policy Evaluation

✗ HTTP

✗ Request Processing

Redis stores state.

It never decides application behavior.

---

# Module Position

```text
Gateway

↓

Redis Service

↓

Redis Client

↓

Redis Server
```

Business modules never communicate directly with Redis.

---

# Owned Packages

```
redis/

client/

connection/

key/

script/

serializer/

health/

config/

exception/
```

---

# Public API

The module exposes

```
RedisService

LuaExecutor

RedisHealthService

KeyBuilder
```

Every other module interacts with Redis exclusively through these APIs.

---

# Internal Components

Internal implementation classes

```
LettuceConfiguration

RedisConnectionManager

RedisSerializer

RedisTemplateAdapter

LuaScriptLoader

RedisHealthIndicator
```

These classes remain implementation details.

---

# Primary Classes

Expected Version 1 classes

```
RedisService

LuaExecutor

RedisConfiguration

RedisHealthIndicator

RedisKeyBuilder

RedisSerializer

RedisConnectionManager

RedisException
```

Future

```
RedisClusterManager

RedisSentinelManager

PubSubService

RedisCacheManager
```

---

# Entry Points

Internal

```
get()

set()

increment()

decrement()

expire()

executeLua()

exists()

delete()
```

No HTTP endpoints belong to this module.

---

# Exit Points

Operations return

- Stored Value
- Updated Counter
- Boolean Result
- Lua Result

Redis never returns HTTP responses.

---

# Dependencies

The Redis module may depend on

✓ Spring Data Redis

✓ Lettuce

✓ Configuration

✓ Logging API

✓ Serialization

---

# Forbidden Dependencies

Redis must never depend on

✗ Authentication

✗ Routing

✗ Rate Limiter

✗ Dashboard

✗ Controllers

✗ Security Context

✗ Business Models

---

# Data Ownership

Redis owns

```
Distributed Counters

Token Buckets

Sliding Windows

Gateway Metrics

Temporary Cache

Health State
```

Redis does NOT own

```
Users

JWT

Routes

Business Entities

Application Configuration
```

---

# Key Naming Strategy

Every key must follow

```
environment:module:resource:identifier
```

Examples

```
prod:ratelimiter:user:123

dev:gateway:metrics

prod:bucket:user:42
```

Keys must be

✓ Predictable

✓ Human-readable

✓ Environment aware

---

# TTL Policy

Temporary state must expire automatically.

Examples

```
Sliding Window

↓

Window Duration
```

```
Fixed Window

↓

Window Duration
```

```
Temporary Metrics

↓

Configured TTL
```

No temporary key should exist indefinitely.

---

# Lua Script Responsibilities

Lua execution belongs exclusively to this module.

Business modules

↓

RedisService

↓

LuaExecutor

↓

Redis

Algorithms never execute scripts directly.

---

# Serialization Policy

Redis owns serialization.

Calling modules work only with domain objects.

Redis implementation details remain hidden.

---

# Connection Management

Connection lifecycle belongs exclusively to Redis.

Requirements

✓ Shared Connection Pool

✓ Configurable Timeouts

✓ Automatic Reconnection

✓ Health Verification

No module should manually create Redis connections.

---

# Error Handling

Redis failures produce

```
RedisException
```

The module never determines user-facing HTTP responses.

Calling modules decide failure behavior.

---

# Logging Responsibilities

Log

✓ Connection Established

✓ Connection Lost

✓ Reconnection

✓ Lua Failure

✓ Timeout

✓ Health Status

Never log

✗ Secrets

✗ Credentials

✗ Full Payloads

---

# Performance Requirements

Target latency

< 1 ms

Connection reuse is mandatory.

Network round trips should be minimized.

Lua preferred for multi-operation workflows.

---

# Concurrency Requirements

Redis operations must support

✓ Multiple Gateway Instances

✓ Atomic Updates

✓ Thread Safety

The module must never rely on JVM synchronization for distributed correctness.

---

# Health Requirements

Expose

- Redis Availability
- Connection Pool Status
- Latency
- Ping Response

Health should integrate with Spring Boot Actuator.

---

# Testing Requirements

Mandatory Unit Tests

- Key Builder
- Serialization
- TTL Calculation
- Lua Loading

---

Mandatory Integration Tests

- Redis Connection
- Atomic Operations
- Lua Execution
- TTL Expiration
- Connection Recovery

---

Mandatory Failure Tests

- Redis Down
- Timeout
- Authentication Failure
- Connection Exhaustion

---

Mandatory Performance Tests

Measure

- GET Latency
- SET Latency
- INCR Latency
- Lua Execution
- Connection Pool Throughput

---

# Invariants

The following must always remain true

✓ Redis stores distributed state only.

✓ Business logic never exists in Redis.

✓ Redis connections remain pooled.

✓ Temporary state expires.

✓ Lua executes atomically.

✓ Business modules never access Redis clients directly.

---

# Metrics Produced

Redis publishes

- Command Count
- Average Latency
- Connection Count
- Active Connections
- Failed Commands
- Lua Execution Count

It does not aggregate metrics.

---

# Future Scope

Possible enhancements

- Redis Cluster
- Redis Sentinel
- Redis Streams
- Pub/Sub
- Read Replicas
- Multi-region Deployment

The architecture should support these without changing business modules.

---

# Definition of Done

The Redis module is complete when

- Connection management works.
- Atomic operations work.
- Lua execution works.
- TTL works.
- Key naming is standardized.
- Health checks pass.
- Unit tests pass.
- Integration tests pass.
- Performance targets achieved.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Acceptance Checklist

- [ ] Connection pooling implemented
- [ ] Lua abstraction implemented
- [ ] Key naming standardized
- [ ] TTL enforced
- [ ] Health checks implemented
- [ ] No business logic
- [ ] No HTTP dependency
- [ ] Tests complete
- [ ] Performance validated
- [ ] Documentation complete

---

# Related Contracts

- RATE_LIMITER.md
- GATEWAY.md
- OBSERVABILITY.md
- CONFIGURATION.md

---

# Related ADRs

- ADR-0008 — Redis as Distributed State
- ADR-0009 — Lua Scripts
- ADR-0010 — Stateless Gateway

---

# Final Contract

The Redis module owns **distributed state management exclusively**.

It is the sole authority for Redis connectivity, key management, TTL enforcement, serialization, and atomic Lua execution. It must remain a pure infrastructure module, completely isolated from business logic while providing a reliable and scalable distributed state layer for the entire Gateway.

# Engineering Contract — Gateway Module

Module: Gateway

Version: 1.0

Status: Active

Owner: Gateway Core

---

# Purpose

The Gateway module is the **orchestrator** of the entire application.

It coordinates request processing by invoking the appropriate modules in the correct order.

The Gateway itself should contain **almost no business logic**.

Its primary responsibility is orchestration.

The Gateway answers one question:

> **How should this request flow through the system?**

It never answers

- Who is the user?
- Should the request be rate limited?
- Where should the request be routed?

Those responsibilities belong to their respective modules.

---

# Business Goal

Provide a reliable, deterministic, extensible request processing pipeline capable of supporting distributed deployments while keeping every business capability isolated.

---

# Core Responsibilities

The Gateway owns

✓ Request Pipeline

✓ Module Orchestration

✓ Request Context

✓ Request Lifecycle

✓ Error Propagation

✓ Response Propagation

✓ Pipeline Configuration

✓ Correlation ID Initialization

✓ Request Timing

---

# Explicit Non-Responsibilities

The Gateway never owns

✗ Authentication Logic

✗ Authorization Rules

✗ JWT Parsing

✗ Rate Limiting Algorithms

✗ Redis Operations

✗ Route Resolution Logic

✗ Dashboard

✗ Metrics Storage

✗ Business Logic

The Gateway coordinates.

It does not implement.

---

# Module Position

```text
Client

↓

Gateway

↓

Authentication

↓

Authorization

↓

Rate Limiter

↓

Routing

↓

Backend

↓

Gateway

↓

Client
```

Every request enters and exits through the Gateway.

---

# Owned Packages

```
gateway/

pipeline/

context/

filter/

handler/

response/

exception/

config/
```

---

# Public API

The Gateway exposes

```
GatewayService

GatewayPipeline

GatewayContext
```

Other modules should never invoke internal pipeline components directly.

---

# Internal Components

Internal implementation classes

```
DefaultGatewayPipeline

PipelineExecutor

RequestContextFactory

ResponseHandler

GatewayExceptionHandler

CorrelationIdFilter
```

These remain implementation details.

---

# Primary Classes

Expected Version 1 classes

```
GatewayController

GatewayService

GatewayPipeline

GatewayContext

PipelineExecutor

CorrelationIdFilter

GatewayConfiguration

GatewayExceptionHandler

GatewayResponse
```

Future

```
PluginManager

PipelineExtension

TrafficMirror

CanaryPipeline
```

---

# Request Lifecycle

Every request follows exactly one pipeline.

```text
Receive Request

↓

Create Context

↓

Authentication

↓

Authorization

↓

Rate Limiter

↓

Route Resolution

↓

Forward Request

↓

Receive Response

↓

Return Response

↓

Publish Metrics
```

The order must never change without an approved ADR.

---

# Pipeline Rules

Every stage

Receives

↓

GatewayContext

Processes

↓

GatewayContext

Returns

↓

GatewayContext

No stage should directly manipulate unrelated modules.

---

# Gateway Context

The Gateway owns

```
GatewayContext
```

It contains

- Correlation ID
- Request Metadata
- Authenticated Principal
- Route Information
- Timing Data

The context exists only for the lifetime of a request.

It must never be persisted.

---

# Dependencies

The Gateway may depend on

✓ Authentication

✓ Authorization

✓ Rate Limiter

✓ Routing

✓ Observability

✓ Configuration

---

# Forbidden Dependencies

The Gateway must never depend directly on

✗ Redis Client

✗ Lua Scripts

✗ Dashboard

✗ Database

✗ Business Services

Infrastructure access must remain indirect.

---

# Request Processing Rules

The Gateway

Must

✓ Execute modules in order

✓ Stop on failures

✓ Preserve request integrity

✓ Preserve response integrity

Must Never

✗ Skip security

✗ Modify business payloads

✗ Bypass Rate Limiting

✗ Bypass Routing

---

# Error Handling

Gateway-owned errors

```
400 Bad Request

500 Internal Server Error

502 Bad Gateway

504 Gateway Timeout
```

Module-specific errors remain owned by their respective modules.

---

# Logging Responsibilities

Gateway logs

✓ Request Received

✓ Request Completed

✓ Processing Time

✓ Correlation ID

✓ Pipeline Failure

Never log

✗ Passwords

✗ JWT

✗ Secrets

✗ Sensitive Payloads

---

# Performance Requirements

Pipeline overhead

Target

< 2 ms

Gateway orchestration should contribute negligible latency compared to backend processing.

---

# Concurrency Requirements

Gateway components must remain

✓ Stateless

✓ Thread-safe

✓ Immutable where practical

Request Context must never be shared across requests.

---

# Testing Requirements

Mandatory Unit Tests

- Pipeline Order
- Context Creation
- Error Propagation
- Response Propagation

---

Mandatory Integration Tests

- Complete Request Lifecycle
- Authentication Failure
- Rate Limit Failure
- Successful Routing
- Backend Failure

---

Mandatory Failure Tests

- Invalid Request
- Unknown Route
- Redis Failure
- Authentication Failure
- Backend Timeout

---

Mandatory Performance Tests

Measure

- Pipeline Latency
- Request Throughput
- Context Creation Cost

---

# Invariants

The following must always remain true

✓ Every request passes through the Gateway.

✓ Module execution order remains fixed.

✓ Request Context is request-scoped.

✓ Gateway never implements business logic.

✓ Gateway never owns distributed state.

---

# Metrics Produced

The Gateway publishes

- Total Requests
- Successful Requests
- Failed Requests
- Average Latency
- Active Requests
- Request Duration Distribution

Aggregation belongs to the Observability module.

---

# Security Requirements

Gateway

Must

✓ Enforce pipeline order

✓ Prevent bypassing security stages

✓ Preserve authenticated identity

Gateway must never expose internal implementation details.

---

# Future Scope

Possible enhancements

- Plugin Architecture
- Dynamic Pipelines
- Traffic Shadowing
- Canary Routing
- Request Replay
- Request Recording

The orchestration model should remain unchanged.

---

# Definition of Done

The Gateway module is complete when

- Pipeline executes correctly.
- Request Context works.
- Errors propagate correctly.
- Responses propagate correctly.
- Pipeline remains stateless.
- Unit tests pass.
- Integration tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Acceptance Checklist

- [ ] Stateless
- [ ] Thread-safe
- [ ] Fixed pipeline order
- [ ] No business logic
- [ ] No direct Redis access
- [ ] Context request-scoped
- [ ] Tests complete
- [ ] Documentation complete

---

# Related Contracts

- AUTHENTICATION.md
- ROUTING.md
- RATE_LIMITER.md
- OBSERVABILITY.md
- CONFIGURATION.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0003 — Layered Architecture
- ADR-0010 — Stateless Gateway
- ADR-0015 — Observability Strategy

---

# Final Contract

The Gateway module owns **request orchestration exclusively**.

It is the central coordinator of the request lifecycle, ensuring that Authentication, Authorization, Rate Limiting, Routing, and Observability execute in the correct order without owning or duplicating their business logic. The Gateway must remain stateless, deterministic, lightweight, and focused solely on orchestrating the request pipeline.

# Engineering Contract — Dashboard Module

Module: Dashboard

Version: 1.0

Status: Active

Owner: Presentation Layer

---

# Purpose

The Dashboard module provides a real-time operational view of the Distributed API Gateway.

It visualizes system health, traffic, rate limiting statistics, and infrastructure metrics.

The Dashboard answers one question:

> **What is happening inside the Gateway right now?**

It never answers

> **How should the Gateway behave?**

The Dashboard is a presentation layer only.

---

# Business Goal

Provide a fast, intuitive, production-inspired monitoring interface for Gateway administrators without containing any business logic.

---

# Core Responsibilities

The Dashboard owns

✓ User Interface

✓ Metric Visualization

✓ Health Visualization

✓ Charts

✓ Tables

✓ API Consumption

✓ Dashboard Navigation

✓ UI State

✓ Refresh Logic

---

# Explicit Non-Responsibilities

The Dashboard never owns

✗ Authentication Logic

✗ Routing

✗ Rate Limiting

✗ Redis

✗ Business Logic

✗ Gateway Configuration

✗ Data Persistence

✗ Metric Calculation

The Dashboard displays information.

It never creates it.

---

# Module Position

```text
Administrator

↓

Dashboard

↓

Gateway APIs

↓

Gateway Modules
```

The Dashboard communicates only through public Gateway APIs.

---

# Owned Packages

```
dashboard/

pages/

components/

layouts/

charts/

services/

hooks/

models/

utils/
```

---

# Public API

The Dashboard exposes

```
DashboardApplication
```

No backend module should depend on Dashboard components.

Communication is strictly one-way.

---

# Internal Components

Examples

```
OverviewPage

TrafficChart

GatewayHealthCard

RedisHealthCard

RateLimiterChart

NavigationBar

Sidebar

DashboardService
```

These remain implementation details.

---

# Primary Pages

Version 1

```
Overview

Gateway Health

Redis Health

Traffic Analytics

Rate Limiter Analytics

Route Analytics

JVM Metrics
```

Future

```
Alerts

Cluster View

Historical Reports

User Activity
```

---

# Dependencies

The Dashboard may depend on

✓ React

✓ React Router

✓ Chart Library

✓ Axios (or Fetch)

✓ CSS Framework

---

# Forbidden Dependencies

The Dashboard must never depend on

✗ Redis

✗ Spring Boot

✗ Authentication Module

✗ Rate Limiter

✗ Routing

✗ Business Services

---

# Data Ownership

The Dashboard owns

```
Charts

UI State

Selected Filters

Table State

Display Preferences
```

The Dashboard does NOT own

```
Gateway Metrics

Authentication State

Rate Limit Counters

Redis State

Business Data
```

---

# API Communication Rules

Every request

```text
Dashboard

↓

REST API

↓

Gateway

↓

Response
```

The Dashboard never communicates directly with

- Redis
- Backend Services
- Database

---

# Refresh Policy

Version 1

Polling

```
Every 5 Seconds
```

Future

```
WebSocket

SSE
```

Refresh implementation should remain replaceable.

---

# Visualization Rules

Every visualization should communicate

- Current State
- Trends
- Failures
- Health

Avoid decorative charts.

Every visualization should provide operational value.

---

# User Interface Principles

The Dashboard should be

✓ Minimal

✓ Responsive

✓ Fast

✓ Accessible

✓ Information-focused

Avoid unnecessary animations.

---

# Error Handling

Dashboard failures

↓

Display Friendly Error

↓

Retry Option

↓

Continue Functionality

The UI should never crash because one API fails.

---

# Loading Strategy

Every page should display

- Loading Indicator
- Error State
- Empty State

The absence of data should never produce a broken interface.

---

# Logging Responsibilities

Dashboard logs

✓ Failed API Requests

✓ UI Errors

✓ Rendering Failures

Never log

✗ JWT

✗ Secrets

✗ Personal Information

---

# Performance Requirements

Dashboard load time

Target

< 2 seconds

Polling should not overload Gateway APIs.

Charts should render smoothly.

---

# Concurrency Requirements

Dashboard components should remain

✓ Stateless where practical

✓ Predictable

✓ Independent

Component state should remain local whenever possible.

---

# Testing Requirements

Mandatory Unit Tests

- Components
- Hooks
- Utility Functions

---

Mandatory Integration Tests

- Dashboard API Integration
- Navigation
- Polling
- Error Handling

---

Mandatory UI Tests

- Empty State
- Loading State
- Failure State
- Responsive Layout

---

# Invariants

The following must always remain true

✓ Dashboard contains no business logic.

✓ Dashboard consumes only public APIs.

✓ Dashboard never accesses Redis directly.

✓ UI remains independent from Gateway implementation.

---

# Metrics Displayed

Dashboard visualizes

- Request Rate
- Success Rate
- Error Rate
- Rate Limit Statistics
- JVM Memory
- CPU Usage
- Redis Status
- Gateway Health
- Cluster Health

The Dashboard does not calculate these metrics.

---

# Security Requirements

Version 1

Dashboard assumes trusted local access.

Future

- Login
- RBAC
- Admin Roles
- Audit Logging

Security implementation should not require UI redesign.

---

# Future Scope

Possible enhancements

- WebSockets
- Grafana Integration
- Alerting
- Dark Mode
- Export Reports
- Custom Dashboards
- User Preferences

The component architecture should support these additions.

---

# Definition of Done

The Dashboard module is complete when

- Metrics display correctly.
- Health pages work.
- Polling functions correctly.
- Error handling works.
- Components remain modular.
- Unit tests pass.
- Integration tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Acceptance Checklist

- [ ] No business logic
- [ ] REST-only communication
- [ ] Responsive UI
- [ ] Polling implemented
- [ ] Loading/Error states
- [ ] Tests complete
- [ ] Documentation complete

---

# Related Contracts

- GATEWAY.md
- OBSERVABILITY.md
- CONFIGURATION.md

---

# Related ADRs

- ADR-0012 — React Dashboard
- ADR-0015 — Observability Strategy

---

# Final Contract

The Dashboard module owns **presentation and visualization exclusively**.

It is the sole authority for rendering Gateway information while remaining completely independent of backend business logic. The Dashboard consumes public Gateway APIs, visualizes operational data, and provides administrators with real-time system visibility without influencing Gateway behavior.

# Engineering Contract — Observability Module

Module: Observability

Version: 1.0

Status: Active

Owner: Infrastructure Layer

---

# Purpose

The Observability module provides visibility into the behavior, health, and performance of the Distributed API Gateway.

Its objective is to answer operational questions without requiring developers to inspect code or attach a debugger.

The Observability module answers questions like

- Is the Gateway healthy?
- What is failing?
- Why is it failing?
- How many requests are being processed?
- How long are requests taking?
- Which Gateway instance handled the request?

It never changes Gateway behavior.

It only observes it.

---

# Business Goal

Provide production-grade operational visibility while remaining lightweight, reliable, and completely independent of business logic.

---

# Core Responsibilities

The Observability module owns

✓ Structured Logging

✓ Metrics Collection

✓ Health Checks

✓ Correlation IDs

✓ Request Timing

✓ JVM Metrics

✓ Application Metrics

✓ Redis Metrics

✓ Health Indicators

✓ Metric Publication

---

# Explicit Non-Responsibilities

The Observability module never owns

✗ Authentication

✗ Routing

✗ Rate Limiting

✗ Redis Business Logic

✗ Dashboard Rendering

✗ Business Rules

✗ Request Processing

✗ Gateway Decisions

Observability observes.

It never controls.

---

# Module Position

```text
Gateway Modules

↓

Observability

↓

Logs

Metrics

Health

↓

Dashboard

↓

Administrator
```

Every module may publish events.

Only Observability owns monitoring.

---

# Owned Packages

```
observability/

logging/

metrics/

health/

tracing/

correlation/

actuator/

config/
```

---

# Public API

The module exposes

```
MetricsService

LoggingService

HealthService

CorrelationIdService

ObservabilityFacade
```

Business modules interact only through these interfaces.

---

# Internal Components

Examples

```
MetricsPublisher

StructuredLogger

CorrelationFilter

GatewayHealthIndicator

RedisHealthIndicator

JvmMetricsCollector

RequestTimer

MicrometerConfiguration
```

These remain implementation details.

---

# Primary Classes

Expected Version 1 classes

```
MetricsService

LoggingService

CorrelationIdFilter

HealthService

GatewayHealthIndicator

RedisHealthIndicator

RequestTimer

ObservabilityConfiguration
```

Future

```
PrometheusExporter

TracingService

JaegerExporter

GrafanaPublisher

AlertManager
```

---

# Dependencies

The module may depend on

✓ Spring Boot Actuator

✓ Micrometer

✓ Logging Framework

✓ Configuration

✓ JVM Metrics

---

# Forbidden Dependencies

The Observability module must never depend on

✗ Authentication

✗ Routing

✗ Rate Limiting

✗ Dashboard

✗ Business Services

✗ Redis Business Logic

---

# Data Ownership

Observability owns

```
Metrics

Health Status

Correlation IDs

Request Timings

Application Logs
```

It does NOT own

```
Users

JWT

Business Entities

Rate Limit Policies

Route Definitions
```

---

# Logging Responsibilities

The module owns every structured log.

Examples

Application

✓ Startup

✓ Shutdown

Request

✓ Request Started

✓ Request Completed

Security

✓ Authentication Failure

✓ Authorization Failure

Gateway

✓ Route Selected

✓ Backend Failure

Infrastructure

✓ Redis Failure

✓ Health Status

---

# Logging Rules

Every log should include

✓ Timestamp

✓ Log Level

✓ Correlation ID

✓ Thread

✓ Module

✓ Message

Optional

✓ Request Path

✓ HTTP Method

✓ Latency

---

Never log

✗ Passwords

✗ JWT Tokens

✗ Secrets

✗ API Keys

✗ Sensitive Personal Data

---

# Metrics Responsibilities

The module owns publication of

Gateway

- Requests
- Latency
- Success Rate
- Error Rate

Authentication

- Login Success
- Login Failure

Routing

- Route Count
- Backend Errors

Rate Limiter

- Allowed Requests
- Blocked Requests

Redis

- Latency
- Connection Count

JVM

- Heap
- Threads
- CPU

The module publishes metrics.

It does not calculate business decisions.

---

# Health Responsibilities

Expose

```
/actuator/health
```

Health includes

- Gateway
- Redis
- JVM
- Disk
- Dependencies

Future

- External Services
- Queue Health

---

# Correlation ID Responsibilities

Every request receives exactly one Correlation ID.

Flow

```text
Incoming Request

↓

Correlation Filter

↓

Request Context

↓

Logs

↓

Metrics

↓

Response
```

Correlation IDs should propagate through the complete request lifecycle.

---

# Request Timing

Observability owns

- Request Start Time
- Request End Time
- Total Duration

Latency calculations should remain centralized.

---

# Error Handling

Observability failures

Must Never

stop request processing.

If metrics cannot be published

↓

Continue Request

If logging fails

↓

Continue Request

Observability is non-blocking.

---

# Performance Requirements

Target overhead

< 1 ms

Metric publication should be lightweight.

Logging should avoid unnecessary allocations.

Observability must never become a bottleneck.

---

# Concurrency Requirements

Components must remain

✓ Stateless

✓ Thread-safe

✓ Lock-free whenever practical

Metric publication must support concurrent requests.

---

# Testing Requirements

Mandatory Unit Tests

- Correlation ID
- Metrics Publication
- Health Indicators
- Logging Format

---

Mandatory Integration Tests

- Actuator
- Health Endpoint
- Metrics Endpoint
- Correlation Flow

---

Mandatory Failure Tests

- Logging Failure
- Redis Health Failure
- Metrics Publisher Failure

---

# Invariants

The following must always remain true

✓ Every request receives one Correlation ID.

✓ Logs remain structured.

✓ Health reflects actual application state.

✓ Metrics remain centralized.

✓ Observability never owns business logic.

---

# Metrics Produced

The module aggregates and publishes

- Gateway Metrics
- JVM Metrics
- Redis Metrics
- Authentication Metrics
- Routing Metrics
- Rate Limiting Metrics

It is the single authority for operational telemetry.

---

# Security Requirements

Health and metrics endpoints

Development

May remain public.

Production

Should require administrator access.

Sensitive implementation details should never be exposed.

---

# Future Scope

Possible enhancements

- Prometheus
- Grafana
- OpenTelemetry
- Jaeger
- Zipkin
- Distributed Tracing
- Alerting
- SLO Monitoring
- SLA Dashboards

The architecture should support these without changing business modules.

---

# Definition of Done

The Observability module is complete when

- Structured logging works.
- Metrics are published.
- Correlation IDs work.
- Health endpoints work.
- Request timing works.
- Unit tests pass.
- Integration tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Acceptance Checklist

- [ ] Structured logging implemented
- [ ] Metrics centralized
- [ ] Correlation IDs implemented
- [ ] Health checks implemented
- [ ] No business logic
- [ ] Thread-safe
- [ ] Tests complete
- [ ] Documentation complete

---

# Related Contracts

- GATEWAY.md
- REDIS.md
- DASHBOARD.md
- CONFIGURATION.md

---

# Related ADRs

- ADR-0015 — Observability Strategy
- ADR-0011 — Docker Compose
- ADR-0013 — Comprehensive Testing Strategy

---

# Final Contract

The Observability module owns **monitoring and operational visibility exclusively**.

It is the single authority for structured logging, metrics publication, health reporting, request correlation, and operational telemetry. It must remain lightweight, non-blocking, thread-safe, and completely isolated from business logic while providing the operational insight required to run, debug, and monitor the Distributed API Gateway in production.

# Engineering Contract — Configuration Module

Module: Configuration

Version: 1.0

Status: Active

Owner: Infrastructure Layer

---

# Purpose

The Configuration module is responsible for managing all application configuration required by the Distributed API Gateway.

It provides a centralized, type-safe, and environment-aware mechanism for configuring every module without requiring code changes.

The Configuration module answers one question:

> **How should the Gateway be configured?**

It never answers

- How should requests be processed?
- How should algorithms behave?
- How should users be authenticated?

Those responsibilities belong to other modules.

---

# Business Goal

Provide centralized, secure, environment-independent configuration management while preventing configuration duplication and hardcoded values throughout the application.

---

# Core Responsibilities

The Configuration module owns

✓ Application Configuration

✓ Environment Profiles

✓ Property Binding

✓ Bean Configuration

✓ External Configuration

✓ Default Values

✓ Feature Flags

✓ Validation of Configuration

✓ Configuration Documentation

---

# Explicit Non-Responsibilities

The Configuration module never owns

✗ Authentication Logic

✗ Routing Logic

✗ Rate Limiting Logic

✗ Redis Operations

✗ Metrics

✗ Dashboard

✗ Business Rules

✗ HTTP Processing

Configuration defines behavior.

It never executes behavior.

---

# Module Position

```text
Configuration Files

↓

Configuration Module

↓

Application Modules
```

Every module receives configuration.

No module owns global configuration.

---

# Owned Packages

```
config/

properties/

beans/

profiles/

validation/

constants/
```

---

# Public API

The module exposes

```
GatewayProperties

SecurityProperties

RedisProperties

RateLimiterProperties

DashboardProperties

ConfigurationFacade
```

Every module consumes strongly typed configuration objects.

---

# Internal Components

Examples

```
ApplicationConfiguration

WebClientConfiguration

RedisConfiguration

SecurityConfiguration

PropertyValidator

EnvironmentResolver
```

These remain implementation details.

---

# Primary Classes

Expected Version 1 classes

```
ApplicationProperties

GatewayProperties

RedisProperties

SecurityProperties

RateLimiterProperties

WebClientConfiguration

ConfigurationValidator

EnvironmentConfiguration
```

Future

```
DynamicConfiguration

ConfigurationServerClient

FeatureFlagManager

SecretManager
```

---

# Configuration Sources

Version 1

```
application.yml

↓

application-dev.yml

↓

application-prod.yml

↓

Environment Variables
```

Priority follows Spring Boot conventions.

---

# Dependencies

The Configuration module may depend on

✓ Spring Boot Configuration

✓ Bean Validation

✓ Environment

✓ Logging

---

# Forbidden Dependencies

Configuration must never depend on

✗ Authentication

✗ Routing

✗ Dashboard

✗ Redis Business Logic

✗ Rate Limiting Algorithms

✗ Controllers

---

# Data Ownership

Configuration owns

```
Application Properties

Environment Values

Timeouts

Ports

Limits

Secrets References

Feature Flags
```

Configuration does NOT own

```
JWT

Routes

Redis Data

Users

Metrics

Business Objects
```

---

# Configuration Principles

Every configurable value

Must

✓ Have one owner

✓ Have one default (where appropriate)

✓ Be externally configurable

✓ Be documented

Must Never

✗ Be hardcoded

✗ Be duplicated

✗ Be scattered across modules

---

# Environment Profiles

Supported profiles

```
development

testing

production
```

Each profile may override only the values that differ.

Business logic must remain identical across environments.

---

# Secret Management

Secrets include

- JWT Secret
- Redis Password
- API Keys

Secrets

Must

✓ Come from Environment Variables or Secret Managers

Must Never

✗ Be committed to Git

✗ Be hardcoded

✗ Appear in logs

---

# Bean Ownership

The Configuration module owns creation of shared infrastructure beans.

Examples

```
WebClient

RedisConnectionFactory

ObjectMapper

PasswordEncoder

Clock
```

Business modules must never instantiate these directly.

---

# Validation Responsibilities

Configuration should fail fast.

Examples

- Missing JWT Secret
- Invalid Port
- Negative Timeout
- Invalid Redis Host

Application startup should fail for invalid mandatory configuration.

---

# Logging Responsibilities

Log

✓ Active Profile

✓ Application Version

✓ Loaded Configuration Summary

✓ Startup Configuration

Never log

✗ Secrets

✗ Passwords

✗ Tokens

✗ Credentials

---

# Performance Requirements

Configuration loading occurs once during startup.

Runtime configuration access should be constant time.

Configuration should not allocate unnecessary objects during request processing.

---

# Concurrency Requirements

Configuration objects must be

✓ Immutable

✓ Thread-safe

✓ Singleton where appropriate

Configuration must not change unexpectedly during runtime.

---

# Testing Requirements

Mandatory Unit Tests

- Property Binding
- Validation Rules
- Default Values

---

Mandatory Integration Tests

- Profile Loading
- Bean Creation
- Environment Variable Overrides

---

Mandatory Failure Tests

- Missing Required Property
- Invalid Configuration
- Invalid Secret
- Invalid Timeout

---

# Invariants

The following must always remain true

✓ No hardcoded configuration.

✓ Configuration is externally managed.

✓ Secrets never appear in logs.

✓ Shared beans are centrally configured.

✓ Modules never duplicate configuration.

---

# Configuration Categories

Gateway

- Port
- Context Path

Authentication

- JWT Secret
- Expiration

Routing

- Timeout
- Retry Configuration (future)

Rate Limiter

- Default Policy
- Algorithm

Redis

- Host
- Port
- Password
- Pool Size

Dashboard

- Refresh Interval

Observability

- Logging Level
- Metrics
- Actuator

---

# Security Requirements

Configuration

Must

✓ Protect secrets

✓ Validate sensitive values

✓ Separate development and production

✓ Support external secret injection

---

# Future Scope

Possible enhancements

- Spring Cloud Config

- HashiCorp Vault

- AWS Secrets Manager

- Dynamic Configuration Reload

- Feature Flags

- Configuration Versioning

The architecture should support these additions without affecting business modules.

---

# Definition of Done

The Configuration module is complete when

- All application properties are externalized.
- Bean creation is centralized.
- Configuration validation works.
- Profiles load correctly.
- Secrets are externalized.
- Unit tests pass.
- Integration tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Acceptance Checklist

- [ ] No hardcoded values
- [ ] Environment profiles implemented
- [ ] Bean configuration centralized
- [ ] Secrets externalized
- [ ] Validation implemented
- [ ] Thread-safe
- [ ] Tests complete
- [ ] Documentation complete

---

# Related Contracts

- AUTHENTICATION.md
- GATEWAY.md
- REDIS.md
- OBSERVABILITY.md

---

# Related ADRs

- ADR-0002 — Spring Boot Framework
- ADR-0004 — Constructor Injection
- ADR-0011 — Docker Compose

---

# Final Contract

The Configuration module owns **application configuration exclusively**.

It is the single authority for application properties, environment profiles, bean creation, configuration validation, and externalized settings. It must ensure that the Gateway remains environment-independent, secure, maintainable, and free from hardcoded configuration while providing a centralized foundation for every other module in the system.