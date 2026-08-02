# Development Playbook

# Distributed API Gateway + Rate Limiter

Version: 1.0

Status: Active

---

# Purpose

This document defines the complete engineering roadmap for building the Distributed API Gateway.

Unlike the PRD, which defines **what** to build, and the Architecture document, which defines **how the system is structured**, this playbook defines **how the project will actually be developed**.

Every coding session should begin by referring to this document.

The playbook serves as the execution plan for the entire repository.

---

# Development Philosophy

Every feature follows the same lifecycle.

```

Learn

↓

Design

↓

Implement

↓

Test

↓

Benchmark

↓

Review

↓

Document

↓

Commit

↓

Next Feature

```

No phase is considered complete until every step has been completed.

---

# General Rules

Before beginning any phase:

- Read the Learning Objectives.
- Understand the architecture.
- Review previous ADRs.
- Review Engineering Checklist.
- Review Acceptance Criteria.

Only then begin implementation.

---

# Phase Structure

Every phase in this playbook follows the same template.

1. Purpose
2. Business Motivation
3. Engineering Motivation
4. Learning Objectives
5. Concepts Covered
6. Deliverables
7. Tech Stack
8. Architecture Changes
9. Folder Changes
10. Dependencies
11. APIs
12. Classes
13. Tests
14. Benchmarks
15. Engineering Checklist
16. Definition of Done
17. Git Milestone
18. Interview Questions

---

# Phase 0 — Project Foundation

---

## Objective

Build a production-ready Spring Boot project that serves as the foundation for every future phase.

No business functionality will be implemented during this phase.

The only goal is establishing a maintainable engineering baseline.

---

## Business Motivation

Every large software project begins with a solid foundation.

Investing time in project structure early prevents architectural debt later.

---

## Engineering Motivation

This phase introduces:

- Spring Boot
- Dependency Injection
- Configuration
- Logging
- Validation
- Docker
- Testing

These concepts are prerequisites for every later phase.

---

# Learning Objectives

By the end of this phase you should understand:

- Spring Boot architecture
- Gradle
- Dependency Injection
- Bean lifecycle
- Configuration
- Profiles
- Logging
- Validation
- Docker basics
- Health endpoints

---

# Concepts Covered

- REST
- IoC Container
- Beans
- Controllers
- Services
- Dependency Injection
- Configuration
- SLF4J
- Logback
- JUnit
- Mockito
- Docker

---

# Deliverables

By the end of this phase the repository should contain:

- Spring Boot project
- Production folder structure
- Health endpoint
- Version endpoint
- Dockerfile
- Global exception handler
- Configuration package
- Logging configuration
- Unit testing setup

---

# Tech Stack

Language

- Java 21

Framework

- Spring Boot

Build Tool

- Gradle

Libraries

- Spring Web
- Spring Validation
- Lombok
- SLF4J
- Logback
- JUnit 5
- Mockito

---

# Architecture Changes

Before Phase 0

```

Nothing

```

After Phase 0

```

Client

↓

Spring Boot

↓

Health Endpoint

```

---

# Folder Structure

```

src/main/java

gateway/

config/

controller/

service/

dto/

exception/

util/

model/

```

---

# Dependencies

Required

- Spring Boot Starter Web
- Validation
- Lombok

Optional

None

Forbidden

Redis

JWT

Spring Security

Monitoring

Dashboard

---

# APIs

## Health Endpoint

```

GET /health

```

Purpose

Verify application health.

Response

```

200 OK

{
"status":"UP"
}

```

---

## Version Endpoint

```

GET /version

```

Purpose

Return current application version.

---

# Classes To Create

GatewayApplication

HealthController

VersionController

GlobalExceptionHandler

ApplicationConfig

---

# Configuration

Create

application.yml

Configure

- Port
- Application Name
- Logging Level

No environment-specific profiles yet.

---

# Testing Requirements

Unit Tests

- Context Loads
- Health Endpoint
- Version Endpoint

Integration Tests

- Application Startup

---

# Benchmarks

Not applicable during this phase.

---

# Engineering Checklist

- [ ] Spring Boot starts successfully
- [ ] No warnings during startup
- [ ] Folder structure established
- [ ] Logging configured
- [ ] Validation enabled
- [ ] Health endpoint working
- [ ] Tests passing
- [ ] Dockerfile builds
- [ ] README updated

---

# Definition of Done

Phase 0 is complete when:

- Application starts without errors.
- Folder structure is finalized.
- Health endpoint works.
- Tests pass.
- Docker image builds successfully.
- Documentation updated.
- Semantic commit created.

---

# Expected Git Commits

```

chore: initialize spring boot project

chore: configure project structure

feat: add health endpoint

feat: add version endpoint

test: add application tests

docs: update phase 0 documentation

```

---

# Interview Questions

- What is Spring Boot?
- What happens during Spring Boot startup?
- What is Dependency Injection?
- Why Constructor Injection?
- What is a Bean?
- Why use Gradle?
- Why expose health endpoints?
- Why Dockerize early?
- Why separate configuration?

---

# Exit Criteria

Do **not** proceed to Phase 1 until:

- Every deliverable is complete.
- Engineering checklist passes.
- Tests pass.
- Documentation updated.
- Code reviewed.
- Git milestone completed.

---

# Next Phase

Phase 1 — Gateway Core

The next phase transforms the project from a basic Spring Boot application into a functioning API Gateway.


# Phase 1 — Gateway Core

---

# Objective

Transform the Spring Boot application into a configurable API Gateway capable of receiving requests, validating them, resolving routes, and forwarding them to backend services.

This phase establishes the backbone of the entire project.

Every future module (Authentication, Rate Limiting, Redis, Monitoring) will plug into this request pipeline.

---

# Business Motivation

Modern backend systems rarely expose services directly to clients.

Instead, requests first pass through an API Gateway.

The gateway becomes the single entry point responsible for:

- Routing
- Validation
- Authentication (future)
- Rate Limiting (future)
- Logging (future)
- Monitoring (future)

Without a Gateway:

- Clients know backend addresses.
- Security becomes inconsistent.
- Routing logic is duplicated.
- Scaling becomes difficult.

---

# Engineering Motivation

This phase introduces the Gateway Request Pipeline.

Future phases should extend this pipeline instead of replacing it.

By the end of Phase 1, every request should travel through a single execution path before reaching backend services.

---

# Learning Objectives

After completing this phase you should understand:

- API Gateway
- Reverse Proxy
- Request Lifecycle
- Dynamic Routing
- Route Matching
- REST Forwarding
- WebClient vs RestTemplate
- HTTP Status Propagation
- Request Validation

---

# Concepts Covered

- API Gateway
- Reverse Proxy
- Route Registry
- Request Dispatcher
- Proxy Pattern
- HTTP Forwarding
- Dynamic Configuration
- Request Validation
- Response Handling

---

# Deliverables

The Gateway should support:

- Route Registration
- Route Update
- Route Deletion
- Route Lookup
- Route Validation
- Dynamic Request Forwarding
- Health Verification
- Route Listing

---

# Tech Stack

Framework

- Spring Boot

Libraries

- Spring Web
- Spring Validation

HTTP Client

- WebClient (Preferred)

Testing

- JUnit
- Mockito

---

# Architecture Changes

Before Phase 1

```text
Client

↓

Spring Boot

↓

Health Endpoint
```

After Phase 1

```text
Client

↓

Gateway

↓

Request Validation

↓

Route Resolver

↓

Request Forwarder

↓

Backend Service
```

---

# New Packages

```
gateway/

routing/

controller/

dto/

service/

config/
```

---

# Folder Structure

```
gateway/

controller/

service/

routing/

    RouteRegistry

    RouteResolver

    RouteForwarder

dto/

request/

response/

config/

exception/
```

---

# New Components

## GatewayController

Responsibilities

- Accept proxy requests
- Validate request
- Invoke GatewayService

Must Never

- Resolve routes
- Forward requests

---

## GatewayService

Responsibilities

- Coordinate request flow
- Call RouteResolver
- Call RouteForwarder

Must Never

- Access HTTP directly

---

## RouteRegistry

Responsibilities

- Store routes
- Register routes
- Remove routes
- Update routes

---

## RouteResolver

Responsibilities

- Match incoming paths
- Find destination service

---

## RouteForwarder

Responsibilities

- Build outbound request
- Forward request
- Receive backend response

---

# APIs

## Register Route

```
POST /routes
```

Purpose

Register a new Gateway route.

---

## Update Route

```
PUT /routes/{id}
```

---

## Delete Route

```
DELETE /routes/{id}
```

---

## List Routes

```
GET /routes
```

---

## Proxy Request

```
POST /proxy/**
GET /proxy/**
PUT /proxy/**
DELETE /proxy/**
```

---

# Request Lifecycle

```text
Client

↓

GatewayController

↓

GatewayService

↓

RouteResolver

↓

RouteForwarder

↓

Backend

↓

Gateway

↓

Client
```

---

# Request Validation

Validate

- HTTP Method
- Route Exists
- Supported Method
- Headers
- Request Body

Reject invalid requests before forwarding.

---

# Route Matching Strategy

Priority

1.

Exact Match

Example

```
/users/profile
```

---

2.

Longest Prefix

Example

```
/users/*
```

---

3.

Wildcard

Example

```
/**
```

---

4.

Default Route

If configured.

---

# Route Model

A Route should contain

```
id

path

targetUrl

allowedMethods

enabled

description
```

Future versions may include

- Authentication Required
- Rate Limit Policy
- Required Roles
- Timeout
- Retry Policy

---

# Route Registry Rules

Routes

Must

✓ Be uniquely identifiable

✓ Be dynamically configurable

✓ Support enable/disable

Must Never

✗ Contain business logic

---

# Error Handling

Gateway should return

404

Unknown Route

405

Unsupported Method

400

Invalid Request

500

Unexpected Error

---

# Testing Requirements

Unit Tests

- Route Registration
- Route Update
- Route Deletion
- Route Lookup
- Route Matching

---

Integration Tests

- Request Forwarding
- Invalid Route
- Invalid Method
- Multiple Routes

---

Edge Cases

- Empty Route List
- Duplicate Route
- Disabled Route
- Invalid URL
- Backend Timeout (mock)

---

# Benchmarks

Measure

- Route Resolution Time
- Request Forwarding Time
- Total Gateway Latency

Record

Average

Minimum

Maximum

95th Percentile

---

# Engineering Checklist

Architecture

- [ ] Layered Architecture preserved
- [ ] No business logic in controllers
- [ ] Route logic isolated

Code Quality

- [ ] Small methods
- [ ] Meaningful names
- [ ] Constructor injection
- [ ] No duplicate logic

Testing

- [ ] Unit tests complete
- [ ] Integration tests complete
- [ ] Edge cases covered

Documentation

- [ ] README updated
- [ ] Architecture updated
- [ ] API documentation updated

Performance

- [ ] Route lookup efficient
- [ ] No unnecessary object creation

---

# Definition of Done

Phase 1 is complete when:

- Gateway accepts requests.
- Dynamic routes work.
- Requests reach backend services.
- Route CRUD APIs work.
- Tests pass.
- Documentation updated.
- Benchmark results recorded.
- Engineering checklist passes.

---

# Expected Git Commits

```
feat: implement gateway request pipeline

feat: add route registry

feat: implement route resolver

feat: implement request forwarder

feat: add route management apis

test: add gateway integration tests

docs: update gateway architecture
```

---

# Interview Questions

- What is an API Gateway?
- Why use a Gateway instead of exposing services directly?
- Reverse Proxy vs Forward Proxy?
- Why separate RouteResolver from RouteForwarder?
- Why use WebClient instead of RestTemplate?
- How would you scale this Gateway?
- What happens if no route matches?
- How would you cache routes?
- How would you support service discovery later?
- What architectural changes will Authentication introduce?

---

# Exit Criteria

Do not begin Phase 2 until:

- Gateway successfully forwards requests.
- Route CRUD APIs are stable.
- Tests pass.
- Benchmarks recorded.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Next Phase

**Phase 2 — Authentication & Authorization**

The Gateway will evolve from a simple request router into a secure entry point by introducing JWT-based authentication, role-based authorization, and Spring Security.

# Phase 2 — Authentication & Authorization

---

# Objective

Transform the Gateway into a secure entry point by introducing authentication and authorization.

Only verified and authorized requests should reach backend services.

This phase establishes the Gateway's security architecture and lays the foundation for future production-ready features.

---

# Business Motivation

An API Gateway is the first line of defense for backend services.

Without centralized authentication:

- Every microservice validates users independently.
- Security rules become inconsistent.
- Duplicate authentication logic increases maintenance costs.
- Attack surface grows significantly.

By centralizing security within the Gateway, backend services can focus exclusively on business logic.

---

# Engineering Motivation

Authentication and Authorization are independent responsibilities.

Authentication answers:

> **Who are you?**

Authorization answers:

> **What are you allowed to do?**

These concerns should remain completely isolated throughout the project.

---

# Learning Objectives

After completing this phase you should understand:

- Spring Security
- JWT Authentication
- Authentication Filters
- Authorization Filters
- Security Context
- BCrypt
- Role-Based Access Control (RBAC)
- Stateless Authentication
- Filter Chain
- Security Best Practices

---

# Concepts Covered

- Authentication
- Authorization
- JWT
- Access Token
- Claims
- SecurityContext
- BCrypt
- Filter Chain
- Stateless APIs
- RBAC

---

# Deliverables

The Gateway should support:

- User Registration
- User Login
- JWT Generation
- JWT Validation
- Authentication Filter
- Authorization Filter
- Protected APIs
- Public APIs
- Role Validation
- Secure Error Responses

---

# Tech Stack

Framework

- Spring Security

Libraries

- JWT Library (jjwt)

Password Hashing

- BCrypt

Testing

- Spring Security Test
- Mockito
- JUnit

---

# Architecture Changes

Before Phase 2

```text
Client

↓

Gateway

↓

Route Resolver

↓

Backend
```

After Phase 2

```text
Client

↓

Authentication Filter

↓

Authorization Filter

↓

Gateway

↓

Route Resolver

↓

Backend
```

---

# New Packages

```
security/

jwt/

filter/

auth/

authorization/
```

---

# Folder Structure

```
security/

    SecurityConfig

jwt/

    JwtService

    JwtProvider

    JwtValidator

filter/

    JwtAuthenticationFilter

    AuthorizationFilter

auth/

    AuthenticationService

    AuthenticationController

authorization/

    RoleValidator
```

---

# Components

## SecurityConfig

Responsibilities

- Configure Spring Security
- Register Filters
- Configure Public Routes

Must Never

- Generate JWT
- Validate JWT

---

## JwtService

Responsibilities

- Generate Token
- Validate Token
- Parse Claims

Must Never

- Authenticate User
- Verify Roles

---

## JwtAuthenticationFilter

Responsibilities

- Extract JWT
- Validate JWT
- Build Security Context

Must Never

- Route Requests
- Query Redis

---

## AuthenticationService

Responsibilities

- Register User
- Login User
- Hash Password
- Generate JWT

---

## AuthorizationFilter

Responsibilities

- Verify Required Roles
- Reject Unauthorized Access

Must Never

- Authenticate User

---

# APIs

## Register

```
POST /auth/register
```

---

## Login

```
POST /auth/login
```

---

## Current User

```
GET /auth/me
```

---

## Protected Endpoint

```
GET /protected
```

---

# JWT Structure

Header

↓

Payload

↓

Signature

---

# JWT Claims

```
userId

email

roles

issuedAt

expiration
```

Future Claims

- Organization
- Tenant
- Permissions

---

# Security Flow

```text
Client

↓

Authorization Header

↓

JwtAuthenticationFilter

↓

JwtValidator

↓

SecurityContext

↓

AuthorizationFilter

↓

Gateway

↓

Backend
```

---

# Authentication Flow

```text
Login Request

↓

Validate Credentials

↓

Hash Comparison

↓

Generate JWT

↓

Return Token
```

---

# Authorization Flow

```text
Authenticated User

↓

Load Roles

↓

Compare Required Role

↓

Allowed

↓

Continue Request
```

---

# Public Endpoints

Authentication is NOT required for

```
/auth/login

/auth/register

/health

/version
```

---

# Protected Endpoints

Authentication IS required for

```
/proxy/**

/routes/**

/metrics/**

/dashboard/**
```

---

# Password Policy

Minimum Length

8

Must contain

- Uppercase
- Lowercase
- Number

Future

- Special Characters
- Password History

---

# Security Rules

Passwords

Must

✓ Be BCrypt hashed

✓ Never logged

✓ Never returned

Must Never

✗ Be stored in plaintext

---

JWT

Must

✓ Be signed

✓ Expire

✓ Validate Signature

✓ Validate Expiration

Must Never

✗ Store sensitive information

---

# Error Responses

401

Unauthorized

Invalid JWT

Expired JWT

Missing JWT

---

403

Forbidden

Insufficient Role

---

# Testing Requirements

Unit Tests

- JWT Generation
- JWT Validation
- BCrypt
- Login
- Registration

---

Integration Tests

- Login Flow
- Protected Endpoint
- Invalid JWT
- Expired JWT
- Forbidden User

---

Edge Cases

- Missing Header
- Empty Token
- Invalid Signature
- Expired Token
- Invalid Role
- Malformed JWT

---

# Benchmarks

Measure

- JWT Validation Time
- Login Latency
- Authentication Filter Overhead

Target

Authentication overhead should remain minimal.

---

# Engineering Checklist

Architecture

- [ ] Authentication isolated
- [ ] Authorization isolated
- [ ] Filter chain correct

Security

- [ ] BCrypt used
- [ ] JWT signed
- [ ] Secrets externalized
- [ ] No sensitive logs

Code Quality

- [ ] Constructor Injection
- [ ] DTO validation
- [ ] No duplicated security logic

Testing

- [ ] Unit tests complete
- [ ] Integration tests complete
- [ ] Security tests complete

Documentation

- [ ] Security architecture updated
- [ ] README updated
- [ ] API documentation updated

---

# Definition of Done

Phase 2 is complete when:

- Users can register.
- Users can log in.
- JWT generation works.
- JWT validation works.
- Public endpoints remain accessible.
- Protected endpoints require authentication.
- Role-based authorization functions correctly.
- Tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Expected Git Commits

```
feat: configure spring security

feat: implement jwt service

feat: add authentication filter

feat: add authorization filter

feat: implement login endpoint

feat: implement registration endpoint

test: add authentication tests

docs: update security architecture
```

---

# Interview Questions

- Why JWT over Sessions?
- Why stateless authentication?
- What is Spring Security Filter Chain?
- Why BCrypt?
- Why shouldn't JWT contain passwords?
- How do you invalidate JWTs?
- What is RBAC?
- Authentication vs Authorization?
- Why use SecurityContext?
- How would OAuth2 fit into this architecture?

---

# Exit Criteria

Do not begin Phase 3 until:

- JWT authentication works.
- Authorization works.
- Public/protected routes behave correctly.
- All security tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Next Phase

**Phase 3 — Rate Limiting Engine**

The Gateway will evolve from a secure entry point into a traffic management system by implementing multiple interchangeable rate limiting algorithms, benchmarking them, and preparing the architecture for distributed state with Redis.

# Phase 3 — Rate Limiting Engine

---

# Objective

Transform the API Gateway into a traffic management system capable of protecting backend services from abuse, accidental traffic spikes, and malicious request floods.

This phase introduces a modular Rate Limiting Engine capable of supporting multiple interchangeable algorithms while keeping the Gateway architecture extensible.

This is the first phase where algorithmic design, concurrency, and performance become first-class engineering concerns.

---

# Business Motivation

Every production API Gateway protects backend services from excessive traffic.

Without rate limiting:

- One client can exhaust server resources.
- Backend services become unavailable.
- Infrastructure costs increase.
- Denial-of-Service attacks become easier.
- Fair resource sharing becomes impossible.

A Gateway without rate limiting is incomplete.

---

# Engineering Motivation

Rate limiting is intentionally designed using the Strategy Pattern.

The Gateway should never depend on a specific algorithm.

Algorithms must remain completely interchangeable.

The surrounding Gateway architecture should remain unchanged regardless of which algorithm is selected.

---

# Learning Objectives

After completing this phase you should understand:

- Fixed Window
- Sliding Window Counter
- Sliding Window Log
- Token Bucket
- Leaky Bucket
- Strategy Pattern
- Algorithm Trade-offs
- Time Complexity
- Space Complexity
- Burst Traffic
- Throughput
- Benchmarking

---

# Concepts Covered

- Rate Limiting
- Strategy Pattern
- Policy Engine
- Algorithm Selection
- Traffic Shaping
- Burst Handling
- Throughput
- Latency
- Benchmarking

---

# Deliverables

The Gateway should support:

- Multiple Algorithms
- Dynamic Algorithm Selection
- Route-specific Policies
- User-specific Policies
- Global Policies
- HTTP 429 Responses
- Retry-After Support
- Algorithm Benchmarks
- Metrics Collection

---

# Tech Stack

Language

- Java 21

Framework

- Spring Boot

Libraries

- Java Concurrency
- Java Time API

Testing

- JUnit
- Mockito

Benchmarking

- JMH (later)
- Custom Benchmark Utility

---

# Architecture Changes

Before Phase 3

```text
Client

↓

Authentication

↓

Authorization

↓

Gateway

↓

Routing

↓

Backend
```

After Phase 3

```text
Client

↓

Authentication

↓

Authorization

↓

Rate Limiter

↓

Gateway

↓

Routing

↓

Backend
```

---

# New Packages

```
ratelimiter/

algorithms/

policy/

strategy/

registry/

metrics/
```

---

# Folder Structure

```
ratelimiter/

RateLimiter

RateLimitPolicy

RateLimiterFactory

strategy/

RateLimitingStrategy

algorithms/

FixedWindowStrategy

SlidingWindowCounterStrategy

SlidingWindowLogStrategy

TokenBucketStrategy

LeakyBucketStrategy

policy/

PolicyResolver

PolicyRegistry

metrics/

RateLimitMetrics
```

---

# Component Responsibilities

## RateLimiter

Responsibilities

- Entry point
- Execute strategy
- Publish metrics

Must Never

- Know algorithm implementation details

---

## RateLimiterFactory

Responsibilities

- Return correct strategy
- Hide implementation details

---

## PolicyResolver

Responsibilities

- Resolve applicable policy
- Route Policy
- User Policy
- Global Policy

---

## RateLimitMetrics

Responsibilities

- Allowed Requests
- Blocked Requests
- Algorithm Statistics

---

# Strategy Interface

Every algorithm must implement

```
RateLimitingStrategy

allowRequest(ClientIdentifier, Policy)
```

The Gateway communicates only with the interface.

Never with concrete implementations.

---

# Algorithms

## Fixed Window

Characteristics

- Simple
- Fast
- Low Memory

Advantages

- Easy implementation
- O(1)

Disadvantages

- Boundary Burst Problem

---

## Sliding Window Counter

Characteristics

- More Accurate
- Medium Memory

Advantages

- Better fairness

Disadvantages

- Slightly more computation

---

## Sliding Window Log

Characteristics

- Most Accurate

Advantages

- Precise limiting

Disadvantages

- High Memory Usage

---

## Token Bucket

Characteristics

- Burst Friendly

Advantages

- Smooth traffic
- Industry standard

Disadvantages

- Slightly more complex

---

## Leaky Bucket

Characteristics

- Constant Outflow

Advantages

- Smooth processing

Disadvantages

- Less burst tolerance

---

# Policy Resolution

Policy precedence

```text
Route Policy

↓

User Policy

↓

IP Policy

↓

Global Policy

↓

Default Policy
```

---

# Request Flow

```text
Incoming Request

↓

Authentication

↓

Authorization

↓

Policy Resolver

↓

Strategy Factory

↓

Selected Algorithm

↓

Allow?

↓

Continue

OR

429
```

---

# Client Identification

Priority

1.

User ID

↓

2.

API Key (Future)

↓

3.

IP Address

↓

4.

Anonymous Client

---

# HTTP Response

Allowed

```
200
```

Blocked

```
429 Too Many Requests
```

Headers

```
Retry-After

X-RateLimit-Limit

X-RateLimit-Remaining

X-RateLimit-Reset
```

---

# Configuration Model

Every policy contains

```
algorithm

window

capacity

refillRate

burst

enabled
```

---

# Failure Handling

Unknown Algorithm

↓

Reject Configuration

---

Missing Policy

↓

Fallback to Default Policy

---

Invalid Configuration

↓

Validation Error

---

# Metrics

Collect

- Allowed Requests
- Blocked Requests
- Requests Per Algorithm
- Average Decision Time
- Current Active Policies

---

# Benchmark Requirements

Every algorithm must be benchmarked.

Metrics

- Decision Latency
- Memory Usage
- Throughput
- Burst Handling
- Accuracy

Benchmark Conditions

- 100 Requests
- 1,000 Requests
- 10,000 Requests
- Concurrent Requests (introduced fully in Phase 5)

Results must be documented.

---

# Testing Requirements

## Unit Tests

- Fixed Window
- Sliding Counter
- Sliding Log
- Token Bucket
- Leaky Bucket
- Policy Resolution
- Strategy Factory

---

## Integration Tests

- Route Policy
- User Policy
- Global Policy
- Retry Headers
- HTTP 429

---

## Edge Cases

- Zero Capacity
- Negative Capacity
- Expired Window
- Burst Traffic
- Unknown Algorithm
- Missing Policy

---

# Engineering Checklist

## Architecture

- [ ] Strategy Pattern implemented
- [ ] Algorithms isolated
- [ ] Policy engine separated

---

## Code Quality

- [ ] No duplicated algorithm logic
- [ ] Factory Pattern used
- [ ] Constructor Injection
- [ ] Small methods

---

## Performance

- [ ] O(1) where applicable
- [ ] Memory usage documented
- [ ] Benchmarks executed

---

## Testing

- [ ] Unit tests complete
- [ ] Integration tests complete
- [ ] Edge cases covered

---

## Documentation

- [ ] Algorithm comparison added
- [ ] Benchmarks documented
- [ ] README updated

---

# Definition of Done

Phase 3 is complete when:

- All five algorithms are implemented.
- Algorithms are interchangeable.
- Policies resolve correctly.
- HTTP 429 responses work.
- Retry headers are returned.
- Benchmarks completed.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Expected Git Commits

```
feat: introduce rate limiting module

feat: implement strategy pattern

feat: add fixed window algorithm

feat: add sliding window counter algorithm

feat: add sliding window log algorithm

feat: add token bucket algorithm

feat: add leaky bucket algorithm

feat: implement policy resolver

feat: expose rate limit configuration

test: add rate limiter tests

benchmark: compare rate limiting algorithms

docs: document algorithm trade-offs
```

---

# Interview Questions

- Why is Rate Limiting important?
- Why use the Strategy Pattern?
- Fixed Window vs Sliding Window?
- Token Bucket vs Leaky Bucket?
- Which algorithm does Cloudflare likely use?
- Which algorithm would you choose for login APIs?
- Which algorithm would you choose for payment APIs?
- How do you benchmark rate limiting algorithms?
- What changes when Redis is introduced?
- Why shouldn't counters remain in memory?

---

# Exit Criteria

Do not begin Phase 4 until:

- All algorithms are implemented.
- Benchmarks completed.
- Strategy Pattern verified.
- Policy resolution tested.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Next Phase

**Phase 4 — Distributed State with Redis**

The Gateway evolves from a single-instance application into a distributed system by replacing local in-memory counters with Redis-backed shared state, introducing atomic operations, TTL management, and Lua scripts for consistency under concurrent traffic.

# Phase 4 — Distributed State with Redis

---

# Objective

Transform the Gateway from a single-instance application into a distributed infrastructure component by replacing local in-memory state with Redis-backed distributed state.

This phase introduces the first true distributed systems concepts into the project.

The Gateway should behave correctly regardless of how many Gateway instances are running.

---

# Business Motivation

A production API Gateway is rarely deployed as a single instance.

Traffic is distributed across multiple Gateway nodes.

If every node stores its own counters:

- Rate limits become inconsistent.
- Users bypass limits simply by hitting another node.
- Horizontal scaling becomes impossible.

Shared distributed state solves this problem.

---

# Engineering Motivation

Redis is introduced as the Gateway's distributed coordination layer.

The Gateway remains stateless.

Redis owns all temporary distributed state.

This prepares the architecture for horizontal scaling in Phase 6.

---

# Learning Objectives

After completing this phase you should understand:

- Redis Fundamentals
- Distributed State
- Atomic Operations
- TTL
- Redis Data Structures
- Lua Scripts
- Connection Pooling
- Distributed Counters
- Serialization
- Failure Recovery

---

# Concepts Covered

- Redis
- Key-Value Store
- Distributed Cache
- Atomic Operations
- INCR
- EXPIRE
- Lua Scripts
- TTL
- Connection Pool
- Distributed Coordination

---

# Deliverables

The Gateway should support:

- Redis Connection
- Distributed Counters
- TTL Management
- Shared Rate Limits
- Atomic Counter Updates
- Redis Health Monitoring
- Automatic Reconnection
- Lua-based Atomic Operations

---

# Tech Stack

Database

- Redis

Spring

- Spring Data Redis

Redis Client

- Lettuce

Scripting

- Lua

Testing

- Testcontainers
- Embedded Redis (optional)

Monitoring

- Redis Health Indicator

---

# Architecture Changes

Before Phase 4

```text
Gateway

↓

Local Memory Counter

↓

Backend
```

After Phase 4

```text
Gateway

↓

Redis Service

↓

Redis

↓

Backend
```

---

# New Packages

```
redis/

config/

health/

scripts/
```

---

# Folder Structure

```
redis/

RedisConfiguration

RedisService

RedisKeyBuilder

RedisHealthService

RedisConnectionManager

scripts/

increment.lua

token_bucket.lua

sliding_window.lua

health/

RedisHealthIndicator
```

---

# Components

## RedisConfiguration

Responsibilities

- Configure Redis Client
- Connection Pool
- Serialization
- Timeouts

---

## RedisService

Responsibilities

- Read Counters
- Update Counters
- Delete Keys
- Execute Lua Scripts

Must Never

- Implement Rate Limiting Logic

---

## RedisKeyBuilder

Responsibilities

- Generate Consistent Keys

Example

```
rate_limit:user:123

token_bucket:user:42

gateway:metrics

gateway:health
```

---

## RedisHealthIndicator

Responsibilities

- Verify Redis Availability
- Publish Health Status

---

## LuaScriptExecutor

Responsibilities

- Execute Atomic Scripts
- Prevent Race Conditions

---

# Redis Data Model

Rate Limiting

```
rate_limit:{clientId}
```

Token Bucket

```
token_bucket:{clientId}
```

Sliding Window

```
sliding_window:{clientId}
```

Metrics

```
metrics:gateway
```

Health

```
gateway:health
```

---

# Key Naming Rules

Keys must be

- Predictable
- Hierarchical
- Human-readable
- Environment-aware

Example

```
dev:rate_limit:user:123

prod:token_bucket:user:88
```

---

# TTL Strategy

| Key | TTL |
|------|-----|
| Fixed Window | Window Duration |
| Sliding Window | Window Duration |
| Token Bucket | Configurable |
| Metrics | Configurable |
| Health | Short TTL |

TTL prevents stale data accumulation.

---

# Atomic Operations

Redis operations must be atomic.

Preferred Commands

```
INCR

DECR

EXPIRE

SETNX

GETSET
```

Complex operations should use Lua Scripts.

---

# Lua Script Usage

Lua scripts will be introduced for:

- Token Bucket
- Sliding Window
- Multi-step Counter Updates

Reason

Prevent race conditions by executing multiple operations atomically.

---

# Request Flow

```text
Client

↓

Gateway

↓

Rate Limiter

↓

Redis Service

↓

Redis

↓

Decision

↓

Gateway

↓

Backend
```

---

# Redis Connection Strategy

Connection Pool

↓

Acquire Connection

↓

Execute Command

↓

Release Connection

Connections should never be manually managed by business logic.

---

# Failure Recovery

Redis Timeout

↓

Retry

↓

Reconnect

↓

Health Check

↓

Fallback Response

---

# Redis Health Check

Health endpoint should expose

```
Redis Status

Connection State

Latency

Last Successful Ping
```

---

# Distributed Counter Flow

```text
Gateway Instance 1

↓

Redis

↑

Gateway Instance 2

↑

Gateway Instance 3
```

All Gateway instances observe the same counter.

---

# Serialization Strategy

Primitive values

↓

String

Complex objects

↓

JSON

Future

↓

MessagePack (optional)

---

# Performance Targets

Redis Read

< 2 ms

Redis Write

< 3 ms

Connection Creation

Avoid per-request creation.

---

# Benchmark Requirements

Measure

- Read Latency
- Write Latency
- Lua Execution Time
- Connection Pool Performance
- TTL Accuracy

Compare

- Local Memory
- Redis
- Redis + Lua

---

# Testing Requirements

## Unit Tests

- Redis Service
- Key Builder
- Lua Executor
- Health Indicator

---

## Integration Tests

- Redis Connection
- Counter Increment
- TTL Expiry
- Shared Counters
- Multiple Gateway Instances

---

## Failure Tests

- Redis Restart
- Connection Timeout
- Connection Recovery
- Invalid Configuration

---

## Edge Cases

- Missing Key
- Expired Key
- Corrupted Value
- High Traffic
- Connection Pool Exhaustion

---

# Engineering Checklist

## Architecture

- [ ] Gateway remains stateless
- [ ] Redis abstraction respected
- [ ] No controller accesses Redis

---

## Performance

- [ ] Connection pooling configured
- [ ] Lua scripts benchmarked
- [ ] No unnecessary serialization

---

## Reliability

- [ ] Automatic reconnect
- [ ] Health checks implemented
- [ ] Graceful failure handling

---

## Testing

- [ ] Unit tests complete
- [ ] Integration tests complete
- [ ] Failure scenarios tested

---

## Documentation

- [ ] Redis architecture updated
- [ ] Key naming documented
- [ ] Benchmarks recorded

---

# Definition of Done

Phase 4 is complete when:

- Redis successfully replaces local counters.
- All Gateway instances share state.
- TTL functions correctly.
- Lua scripts execute atomically.
- Health checks pass.
- Benchmarks completed.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Expected Git Commits

```
feat: integrate redis

feat: configure redis connection pool

feat: implement redis service

feat: introduce distributed counters

feat: implement redis health indicator

feat: add lua scripts for atomic operations

test: add redis integration tests

benchmark: compare local vs redis counters

docs: document redis architecture
```

---

# Interview Questions

- Why Redis instead of ConcurrentHashMap?
- Why is Redis suitable for rate limiting?
- What is TTL?
- Why use Lua Scripts?
- What operations are atomic in Redis?
- How do multiple Gateway instances share state?
- What happens if Redis crashes?
- How would Redis Cluster change this architecture?
- Why use Lettuce over Jedis?
- What are the trade-offs of introducing Redis?

---

# Exit Criteria

Do not begin Phase 5 until:

- Distributed counters work correctly.
- Multiple Gateway instances share the same state.
- Lua scripts verified.
- Failure recovery tested.
- Benchmarks documented.
- Engineering Review Checklist passes.

---

# Next Phase

**Phase 5 — Concurrency & Thread Safety**

The Gateway evolves into a highly concurrent system by addressing race conditions, lock-free programming, synchronization strategies, and stress testing under heavy parallel traffic.

# Phase 5 — Concurrency & Thread Safety

---

# Objective

Transform the Gateway into a thread-safe, highly concurrent system capable of handling thousands of simultaneous requests without race conditions, inconsistent state, or degraded performance.

This phase focuses on making the application correct under concurrent execution before introducing distributed deployment.

---

# Business Motivation

Production API Gateways process thousands of concurrent requests every second.

Multiple users may simultaneously:

- Access the same endpoint
- Consume the same rate limit bucket
- Update shared metrics
- Read shared configuration

The Gateway must produce correct results regardless of execution order.

---

# Engineering Motivation

Concurrency bugs are among the hardest software defects to reproduce.

This phase identifies and removes shared mutable state while ensuring every component behaves correctly under heavy parallel traffic.

Correctness takes priority over optimization.

---

# Learning Objectives

After completing this phase you should understand:

- Java Memory Model
- Threads
- Thread Safety
- Race Conditions
- Synchronization
- Atomic Variables
- CAS (Compare-And-Swap)
- Locks
- ReadWrite Locks
- Concurrent Collections
- Thread Pools
- Executors
- Visibility
- Deadlocks
- Lock Contention
- False Sharing (Overview)

---

# Concepts Covered

- Multithreading
- Synchronization
- Atomic Operations
- Lock-Free Programming
- Immutable Objects
- ExecutorService
- ConcurrentHashMap
- LongAdder
- CountDownLatch
- CompletableFuture (Introduction)

---

# Deliverables

The Gateway should support:

- Thread-safe request processing
- Concurrent metrics collection
- Thread-safe configuration cache
- Stress testing
- Lock-free implementations where appropriate
- Performance comparison of synchronization strategies

---

# Tech Stack

Language

- Java 21

Libraries

- java.util.concurrent

Testing

- JUnit 5

Stress Testing

- Custom Concurrent Test Suite

Benchmarking

- JMH (Later)
- Custom Load Generator

---

# Architecture Changes

Before Phase 5

```text
Gateway

↓

Redis

↓

Backend
```

After Phase 5

```text
Clients

↓

Thread Pool

↓

Gateway

↓

Authentication

↓

Rate Limiter

↓

Redis

↓

Routing

↓

Backend
```

---

# New Packages

```
concurrency/

executor/

stress/

benchmark/
```

---

# Folder Structure

```
concurrency/

ThreadSafetyConfig

AtomicCounter

LockManager

ConcurrentMetrics

executor/

GatewayExecutor

ThreadPoolConfiguration

stress/

StressTestRunner

ConcurrentRequestGenerator

benchmark/

ConcurrencyBenchmark
```

---

# Components

## GatewayExecutor

Responsibilities

- Manage request execution
- Configure thread pool
- Prevent thread exhaustion

Must Never

- Execute business logic

---

## ThreadPoolConfiguration

Responsibilities

- Configure executor
- Queue size
- Thread count
- Rejection policy

---

## ConcurrentMetrics

Responsibilities

- Thread-safe counters
- Request statistics
- Error statistics

---

## AtomicCounter

Responsibilities

- Atomic increment
- Atomic decrement
- Safe concurrent updates

---

## LockManager

Responsibilities

- Encapsulate synchronization
- Manage explicit locks where necessary

---

# Thread Model

Every HTTP request executes independently.

```text
Client

↓

Tomcat Thread Pool

↓

Gateway Pipeline

↓

Redis

↓

Backend

↓

Response
```

Each request should remain isolated.

---

# Shared Resources

Shared resources include

- Metrics
- Configuration Cache
- Redis Connection Pool
- Logging System

Business request data must never be shared.

---

# Thread Safety Rules

Rule 1

Prefer immutable objects.

---

Rule 2

Avoid shared mutable state.

---

Rule 3

Use Atomic classes instead of synchronized counters where possible.

---

Rule 4

Use Concurrent Collections instead of synchronized collections.

---

Rule 5

Minimize lock scope.

---

Rule 6

Never block request threads unnecessarily.

---

# Synchronization Strategy

Preferred order

1.

Immutable Objects

↓

2.

Atomic Classes

↓

3.

Concurrent Collections

↓

4.

ReadWriteLock

↓

5.

synchronized

---

# Atomic Classes

Introduce

- AtomicInteger
- AtomicLong
- AtomicReference
- LongAdder

Document where each should be used.

---

# Concurrent Collections

Use

- ConcurrentHashMap
- CopyOnWriteArrayList (when appropriate)
- ConcurrentLinkedQueue

Avoid

- HashMap
- ArrayList
- LinkedList

for shared mutable state.

---

# Executor Strategy

Use dedicated executors where required.

Document

- Core Pool Size
- Maximum Pool Size
- Queue Capacity
- Rejection Policy

Future versions may introduce separate pools for:

- Routing
- Metrics
- Logging

---

# Race Condition Examples

Example 1

```text
Thread A

Read Counter = 5

↓

Increment

↓

Write 6

------------------

Thread B

Read Counter = 5

↓

Increment

↓

Write 6
```

Expected

7

Actual

6

---

Example 2

Configuration updated while requests are reading it.

Discuss safe publication.

---

# Deadlock Prevention

Rules

- Consistent lock ordering
- Minimize nested locks
- Prefer lock-free structures
- Keep critical sections small

---

# Performance Targets

Request processing must remain scalable under concurrent traffic.

Measure

- Throughput
- Latency
- Lock contention
- Thread utilization

---

# Stress Testing

Scenarios

- 100 concurrent users
- 500 concurrent users
- 1000 concurrent users
- 5000 concurrent users (simulation)

Measure

- Success rate
- Failure rate
- Response time
- CPU usage
- Memory usage

---

# Benchmark Requirements

Compare

AtomicInteger

vs

LongAdder

Compare

ConcurrentHashMap

vs

HashMap + synchronized

Compare

Lock-free

vs

Explicit locking

Document results.

---

# Testing Requirements

## Unit Tests

- Atomic counters
- Lock manager
- Thread-safe metrics
- Executor configuration

---

## Integration Tests

- Concurrent requests
- Simultaneous route access
- Parallel metrics updates
- Parallel Redis access

---

## Stress Tests

- Sustained traffic
- Burst traffic
- Mixed workloads

---

## Failure Tests

- Thread pool exhaustion
- Executor shutdown
- Interrupted threads

---

## Edge Cases

- High contention
- Long-running requests
- Rapid configuration updates
- Simultaneous Gateway startup

---

# Engineering Checklist

## Architecture

- [ ] Shared mutable state minimized
- [ ] Thread-safe components isolated
- [ ] Synchronization documented

---

## Performance

- [ ] Lock contention measured
- [ ] Thread pool configured
- [ ] Atomic operations preferred

---

## Reliability

- [ ] No race conditions detected
- [ ] Deadlock prevention verified
- [ ] Stress tests completed

---

## Testing

- [ ] Concurrent tests pass
- [ ] Stress tests pass
- [ ] Failure tests completed

---

## Documentation

- [ ] Concurrency architecture updated
- [ ] Benchmark results documented
- [ ] Thread model documented

---

# Definition of Done

Phase 5 is complete when:

- Gateway behaves correctly under concurrent load.
- Shared resources are thread-safe.
- Race conditions are eliminated.
- Stress tests pass.
- Benchmark results documented.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Expected Git Commits

```text
feat: introduce concurrency module

feat: configure gateway executor

feat: implement thread-safe metrics

feat: add atomic counters

feat: implement lock manager

test: add concurrent request tests

test: add stress testing suite

benchmark: compare synchronization strategies

docs: document concurrency architecture
```

---

# Interview Questions

- What is thread safety?
- What causes race conditions?
- AtomicInteger vs LongAdder?
- synchronized vs ReentrantLock?
- What is CAS?
- What is the Java Memory Model?
- Why use ConcurrentHashMap?
- What is lock contention?
- How would you debug a deadlock?
- Why should Gateways avoid shared mutable state?

---

# Exit Criteria

Do not begin Phase 6 until:

- Concurrent tests pass.
- Stress tests pass.
- No race conditions remain.
- Benchmark results documented.
- Engineering Review Checklist passes.

---

# Next Phase

**Phase 6 — Distributed Gateway Cluster**

The Gateway evolves into a horizontally scalable distributed system by introducing multiple Gateway instances behind a Load Balancer, shared Redis state, health checks, failover handling, and production-grade deployment topology.

# Phase 6 — Distributed Gateway Cluster

---

# Objective

Transform the Gateway from a single-node application into a horizontally scalable distributed system capable of running multiple Gateway instances behind a Load Balancer while maintaining consistent behavior through shared Redis state.

This phase introduces true distributed system architecture.

---

# Business Motivation

Production API Gateways are never deployed as a single application.

They run as multiple instances because:

- Traffic increases.
- Servers fail.
- Maintenance is required.
- High Availability becomes mandatory.

Users should never know which Gateway instance handled their request.

---

# Engineering Motivation

The Gateway must become stateless.

All Gateway instances should behave identically.

Any instance should be capable of serving any request.

State belongs only in shared infrastructure.

---

# Learning Objectives

After completing this phase you should understand:

- Horizontal Scaling
- Stateless Services
- Load Balancing
- High Availability
- Distributed Systems
- Shared State
- Service Replication
- Health Checks
- Failover
- CAP Theorem (Gateway Perspective)
- Sticky Sessions
- Rolling Deployments

---

# Concepts Covered

- Horizontal Scaling
- Load Balancer
- Reverse Proxy
- High Availability
- Distributed Gateway
- Shared Redis
- Stateless Design
- Health Checks
- Failover
- Blue-Green Deployment (Introduction)

---

# Deliverables

The system should support:

- Multiple Gateway Instances
- Docker Compose Cluster
- Shared Redis
- Shared Configuration
- Gateway Health Checks
- Automatic Failover
- Load Distribution
- Gateway Metrics per Instance

---

# Tech Stack

Infrastructure

- Docker
- Docker Compose

Gateway

- Spring Boot

Distributed Store

- Redis

Networking

- Docker Network

Future

- Kubernetes

---

# Architecture Changes

Before Phase 6

```text
Client

↓

Gateway

↓

Redis

↓

Backend
```

After Phase 6

```text
Client

↓

Load Balancer

↓

Gateway 1

Gateway 2

Gateway 3

↓

Redis

↓

Backend Services
```

---

# Deployment Architecture

```mermaid
flowchart TD

Client

↓

LoadBalancer

↓

Gateway1

Gateway2

Gateway3

Gateway1 --> Redis

Gateway2 --> Redis

Gateway3 --> Redis

Gateway1 --> Backend

Gateway2 --> Backend

Gateway3 --> Backend
```

---

# New Packages

```
deployment/

cluster/

health/

docker/
```

---

# Folder Structure

```
deployment/

GatewayClusterConfig

DockerComposeConfiguration

cluster/

GatewayNode

NodeRegistry

LoadBalancerDocumentation

health/

HealthAggregator

NodeHealthChecker

docker/

docker-compose.yml

Dockerfile
```

---

# Components

## Gateway Node

Responsibilities

- Handle Requests
- Authenticate
- Rate Limit
- Route Requests
- Publish Metrics

Must Never

- Store Local Shared State

---

## HealthAggregator

Responsibilities

- Collect Gateway Health
- Aggregate Node Status
- Publish Cluster Health

---

## NodeHealthChecker

Responsibilities

- Monitor Gateway Nodes
- Detect Failures
- Publish Availability

---

# Stateless Design

Each Gateway instance must remain stateless.

Allowed Local State

- Temporary Request Objects
- Local Variables
- Cached Configuration (Read Only)

Forbidden Local State

- Rate Limit Counters
- User Sessions
- Shared Metrics
- Distributed Locks

---

# Shared Infrastructure

Shared Components

- Redis
- Configuration
- Metrics
- Dashboard

Dedicated Per Node

- JVM
- Thread Pool
- HTTP Server
- Local Logs

---

# Request Flow

```text
Client

↓

Load Balancer

↓

Gateway Instance

↓

Authentication

↓

Authorization

↓

Redis

↓

Routing

↓

Backend

↓

Client
```

---

# Load Balancing Strategy

Initial Strategy

Round Robin

Future Strategies

- Least Connections
- Weighted Round Robin
- IP Hash
- Consistent Hashing

---

# Health Check Strategy

Each Gateway exposes

```
GET /health

GET /ready

GET /live
```

Health checks determine whether a node should receive traffic.

---

# Node Failure Scenario

```text
Gateway 2

↓

Health Check Fails

↓

Removed from Load Balancer

↓

Traffic shifts to

Gateway 1

Gateway 3
```

No client changes required.

---

# Redis Interaction

Every Gateway communicates with the same Redis instance.

```text
Gateway 1

↓

Redis

↑

Gateway 2

↑

Gateway 3
```

This guarantees consistent rate limiting.

---

# Configuration Strategy

Every Gateway reads configuration from

Environment Variables

↓

application.yml

↓

Profiles

No node-specific code.

---

# Docker Compose Services

```
gateway-1

gateway-2

gateway-3

redis

backend

dashboard (future)
```

---

# Docker Network

All services communicate through a shared Docker network.

Direct host communication should be avoided.

---

# Scaling Strategy

Scale by adding Gateway instances.

No application code changes required.

Expected Flow

```
3 Nodes

↓

5 Nodes

↓

10 Nodes

↓

N Nodes
```

Redis remains shared.

---

# Failure Handling

## Gateway Failure

Traffic redirected automatically.

---

## Redis Failure

Gateway returns controlled failures.

Health status updated.

---

## Backend Failure

Gateway propagates appropriate response.

Metrics updated.

---

# Performance Targets

Support

- Multiple Gateway Nodes
- Horizontal Scaling
- Minimal Routing Overhead
- Consistent Rate Limiting

Measure

- Throughput
- Node Utilization
- Gateway Latency
- Failover Time

---

# Testing Requirements

## Unit Tests

- Node Health Checker
- Health Aggregator

---

## Integration Tests

- Multiple Gateway Instances
- Shared Redis
- Gateway Failover
- Health Checks

---

## Load Tests

- 3 Nodes
- 5 Nodes
- Uneven Traffic
- Node Shutdown

---

## Failure Tests

- Kill Gateway Node
- Restart Gateway Node
- Redis Restart
- Backend Restart

---

## Edge Cases

- Simultaneous Node Startup
- Simultaneous Node Shutdown
- Network Delay
- Node Recovery

---

# Benchmark Requirements

Measure

- Requests Per Second

- Failover Time

- Redis Latency

- Load Distribution

- Startup Time

Compare

Single Node

↓

Three Nodes

↓

Five Nodes (Simulation)

---

# Engineering Checklist

## Architecture

- [ ] Gateway stateless
- [ ] Shared Redis verified
- [ ] No local counters

---

## Reliability

- [ ] Health checks working
- [ ] Node failure handled
- [ ] Automatic recovery verified

---

## Scalability

- [ ] Multiple nodes operational
- [ ] Load distribution verified
- [ ] Docker Compose stable

---

## Testing

- [ ] Cluster tests pass
- [ ] Failure tests pass
- [ ] Load tests completed

---

## Documentation

- [ ] Deployment diagrams updated
- [ ] Cluster architecture documented
- [ ] Scaling strategy documented

---

# Definition of Done

Phase 6 is complete when:

- Three Gateway instances run successfully.
- Shared Redis functions correctly.
- Health checks work.
- Node failures do not interrupt service.
- Docker Compose deployment succeeds.
- Cluster tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Expected Git Commits

```text
feat: introduce gateway clustering

feat: configure docker compose cluster

feat: implement health aggregation

feat: add node health checker

feat: configure shared redis deployment

test: add distributed integration tests

test: add failover scenarios

benchmark: compare single node vs clustered deployment

docs: document distributed gateway architecture
```

---

# Interview Questions

- Why should an API Gateway be stateless?
- Why does horizontal scaling require shared state?
- What role does Redis play in a Gateway cluster?
- What is a Load Balancer?
- Round Robin vs Least Connections?
- What happens when a Gateway instance crashes?
- Why are health checks important?
- How would Kubernetes improve this deployment?
- What are sticky sessions?
- How does this architecture satisfy High Availability?

---

# Exit Criteria

Do not begin Phase 7 until:

- Gateway cluster operates correctly.
- Health checks pass.
- Failover scenarios verified.
- Docker Compose deployment stable.
- Benchmark results documented.
- Engineering Review Checklist passes.

---

# Next Phase

**Phase 7 — Observability & Monitoring Dashboard**

The Gateway evolves into an observable production system by introducing real-time metrics, analytics APIs, dashboards, structured monitoring, and operational visibility for administrators.

# Phase 7 — Observability & Monitoring Dashboard

---

# Objective

Transform the Distributed API Gateway into an observable production-ready infrastructure component by introducing centralized monitoring, real-time analytics, operational dashboards, health monitoring, and system-wide metrics.

At the end of this phase, administrators should be able to understand the health of the Gateway without reading application logs.

---

# Business Motivation

Modern distributed systems cannot be managed by logs alone.

Operators need immediate answers to questions like:

- Is the Gateway healthy?
- Which routes receive the most traffic?
- Which clients are being rate limited?
- Is Redis healthy?
- Are Gateway nodes overloaded?
- What changed in the last five minutes?

Observability answers these questions.

---

# Engineering Motivation

This phase introduces the three pillars of observability.

1. Metrics

Measure system behaviour.

2. Logs

Record individual events.

3. Health

Determine system availability.

Distributed systems become maintainable only when all three exist together.

---

# Learning Objectives

After completing this phase you should understand:

- Observability
- Metrics
- Health Indicators
- Monitoring Dashboards
- Analytics APIs
- Dashboard Architecture
- Operational Monitoring
- System Telemetry
- JVM Metrics
- Dashboard Design

---

# Concepts Covered

- Metrics
- Observability
- Dashboard
- Health Indicators
- Telemetry
- Counters
- Gauges
- Timers
- Histograms
- Monitoring APIs

---

# Deliverables

The Gateway should support:

- Live Dashboard
- Gateway Health
- Redis Health
- Cluster Health
- Request Analytics
- Error Analytics
- Traffic Analytics
- Rate Limiting Analytics
- Route Statistics
- JVM Statistics
- Operational Metrics APIs

---

# Tech Stack

Backend

- Spring Boot

Frontend

- React

Charts

- Chart.js

Optional

- WebSocket

Initial Update Strategy

- Polling

Future

- Server Sent Events

- WebSockets

---

# Architecture Changes

Before Phase 7

```text
Gateway Cluster

↓

Redis

↓

Backend
```

After Phase 7

```text
Administrator

↓

Dashboard

↓

Metrics API

↓

Gateway Cluster

↓

Redis

↓

Backend
```

---

# New Packages

```
dashboard/

analytics/

metrics/

health/

monitoring/
```

---

# Folder Structure

```
dashboard/

DashboardController

DashboardService

analytics/

AnalyticsService

RouteAnalytics

RateLimitAnalytics

metrics/

MetricsController

MetricsService

JvmMetrics

GatewayMetrics

health/

ClusterHealthService

RedisHealthService

GatewayHealthService

monitoring/

MonitoringConfiguration
```

---

# Components

## DashboardController

Responsibilities

- Serve dashboard APIs
- Aggregate statistics

Must Never

- Calculate metrics directly

---

## DashboardService

Responsibilities

- Aggregate dashboard data
- Build dashboard response

---

## MetricsService

Responsibilities

- Publish metrics
- Aggregate counters
- Track latency

---

## AnalyticsService

Responsibilities

- Route statistics
- Error statistics
- Traffic statistics

---

## ClusterHealthService

Responsibilities

- Gateway Health
- Redis Health
- Cluster Health

---

# Dashboard Sections

## System Overview

Displays

- Gateway Status
- Redis Status
- Cluster Status
- Active Nodes
- System Uptime

---

## Request Analytics

Displays

- Total Requests
- Requests Per Minute
- Requests Per Second
- Active Requests

---

## Route Analytics

Displays

- Most Visited Routes
- Route Latency
- Route Error Rate

---

## Authentication Analytics

Displays

- Successful Logins
- Failed Logins
- Unauthorized Requests

---

## Rate Limiting Analytics

Displays

- Allowed Requests
- Blocked Requests
- Top Limited Clients
- Algorithm Distribution

---

## Redis Analytics

Displays

- Connection Status
- Memory Usage
- Latency
- Commands Executed

---

## JVM Analytics

Displays

- Heap Usage
- Thread Count
- Garbage Collection
- CPU Usage
- Memory Usage

---

# Metrics APIs

## Gateway Metrics

```
GET /metrics
```

---

## Dashboard

```
GET /dashboard
```

---

## Cluster Health

```
GET /health/cluster
```

---

## Gateway Health

```
GET /health/gateway
```

---

## Redis Health

```
GET /health/redis
```

---

## Route Analytics

```
GET /analytics/routes
```

---

## Rate Limiting Analytics

```
GET /analytics/rate-limits
```

---

## JVM Metrics

```
GET /metrics/jvm
```

---

# Dashboard Layout

```text
------------------------------------------------

Gateway Status

Redis Status

Cluster Status

------------------------------------------------

Traffic

Authentication

Rate Limiting

------------------------------------------------

Top Routes

Top Clients

Errors

------------------------------------------------

JVM

Redis

Gateway Nodes

------------------------------------------------
```

---

# Metrics Collection Flow

```text
Request

↓

Gateway

↓

Metrics Collector

↓

Metrics Registry

↓

Dashboard API

↓

Dashboard
```

---

# Analytics Pipeline

```text
Request

↓

Metrics

↓

Aggregation

↓

Analytics API

↓

Dashboard
```

---

# Health Aggregation

Cluster Health

↓

Gateway Health

↓

Redis Health

↓

JVM Health

↓

Overall Health

---

# Dashboard Refresh Strategy

Version 1

Polling

Every 5 Seconds

Future

WebSockets

Real-time Streaming

---

# Performance Targets

Dashboard Load Time

< 500 ms

Metrics API

< 100 ms

Health Endpoint

< 50 ms

Analytics API

< 200 ms

---

# Testing Requirements

## Unit Tests

- Dashboard Service
- Metrics Service
- Analytics Service
- Health Aggregator

---

## Integration Tests

- Dashboard APIs
- Metrics APIs
- Analytics APIs
- Health APIs

---

## UI Tests

- Dashboard Rendering
- Chart Rendering
- Health Indicators
- Polling Behaviour

---

## Edge Cases

- Empty Metrics
- Redis Offline
- Gateway Node Failure
- Zero Requests
- High Traffic

---

# Benchmark Requirements

Measure

- Dashboard Response Time
- Metrics Aggregation Time
- Analytics Query Time
- Health Check Time

Compare

Idle System

↓

Normal Load

↓

High Load

---

# Engineering Checklist

## Architecture

- [ ] Dashboard separated from Gateway logic
- [ ] Analytics isolated
- [ ] Metrics reusable

---

## Performance

- [ ] Dashboard loads efficiently
- [ ] Polling optimized
- [ ] Aggregation efficient

---

## Reliability

- [ ] Health indicators accurate
- [ ] Dashboard handles failures
- [ ] Cluster status correct

---

## Testing

- [ ] Dashboard APIs tested
- [ ] Analytics tested
- [ ] Metrics tested

---

## Documentation

- [ ] Dashboard architecture documented
- [ ] API documentation updated
- [ ] Metrics documented

---

# Definition of Done

Phase 7 is complete when:

- Dashboard displays live system state.
- Metrics APIs work correctly.
- Analytics APIs return accurate data.
- Cluster health aggregation works.
- Dashboard updates automatically.
- Tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Expected Git Commits

```text
feat: introduce monitoring dashboard

feat: implement metrics api

feat: implement analytics service

feat: add cluster health monitoring

feat: add redis monitoring

feat: add jvm metrics

feat: build dashboard ui

test: add dashboard integration tests

docs: document monitoring architecture
```

---

# Interview Questions

- What is observability?
- Metrics vs Logs?
- Why build a dashboard?
- Polling vs WebSockets?
- Why aggregate metrics instead of querying logs?
- What metrics matter most for an API Gateway?
- How would Prometheus fit into this architecture?
- How would Grafana improve the dashboard?
- How do you monitor Redis health?
- What should trigger production alerts?

---

# Exit Criteria

Do not begin Phase 8 until:

- Dashboard is functional.
- Metrics APIs return accurate data.
- Health checks aggregate correctly.
- Analytics verified.
- Tests pass.
- Documentation updated.
- Engineering Review Checklist passes.

---

# Next Phase

**Phase 8 — Production Readiness & Deployment**

The Gateway will be prepared for production through CI/CD, container hardening, environment management, deployment automation, performance optimization, security hardening, and operational readiness.

# Phase 8 — Production Readiness & Deployment

---

# Objective

Prepare the Distributed API Gateway for production deployment by introducing deployment automation, environment management, CI/CD, container optimization, security hardening, observability integration, and operational best practices.

This phase transforms the repository from an engineering project into a production-inspired backend system.

---

# Business Motivation

Writing code is only part of software engineering.

Production systems must also be:

- Deployable
- Observable
- Secure
- Maintainable
- Recoverable
- Configurable

This phase focuses on the operational lifecycle of the Gateway.

---

# Engineering Motivation

The Gateway has all major functionality.

Now it must be productionized.

Production readiness means:

- predictable deployments
- repeatable environments
- automated testing
- secure configuration
- operational monitoring
- maintainability

---

# Learning Objectives

After completing this phase you should understand:

- Docker Best Practices
- Docker Compose
- Multi-stage Builds
- Environment Variables
- Spring Profiles
- CI/CD
- GitHub Actions
- Production Logging
- Health Checks
- Release Management
- Deployment Strategies

---

# Concepts Covered

- DevOps
- CI/CD
- Continuous Integration
- Continuous Deployment
- Infrastructure as Code
- Production Configuration
- Secrets Management
- Multi-stage Docker
- Release Pipelines

---

# Deliverables

The Gateway should support:

- Production Docker Image
- Multi-stage Docker Build
- Docker Compose Deployment
- Environment Profiles
- GitHub Actions CI Pipeline
- Automated Testing
- Production Logging
- Health Checks
- Startup Validation
- Deployment Documentation

---

# Tech Stack

Containers

- Docker

Orchestration

- Docker Compose

CI

- GitHub Actions

Profiles

- Spring Profiles

Environment

- .env

Future

- Kubernetes
- Helm

---

# Architecture Changes

Before Phase 8

```text
Gateway Cluster

↓

Dashboard

↓

Redis

↓

Backend
```

After Phase 8

```text
GitHub

↓

CI Pipeline

↓

Docker Image

↓

Docker Compose

↓

Gateway Cluster

↓

Redis

↓

Dashboard

↓

Backend
```

---

# New Packages

```
deployment/

ci/

scripts/

profiles/

docker/
```

---

# Folder Structure

```
docker/

Dockerfile

docker-compose.yml

.env.example

.github/

workflows/

ci.yml

deployment/

DeploymentGuide.md

Runbook.md

scripts/

start.sh

stop.sh

health-check.sh

profiles/

application-dev.yml

application-test.yml

application-prod.yml
```

---

# Components

## Dockerfile

Responsibilities

- Build Gateway Image
- Multi-stage Build
- Optimize Image Size

---

## Docker Compose

Responsibilities

- Deploy Gateway Cluster
- Deploy Redis
- Deploy Dashboard
- Configure Networking

---

## GitHub Actions

Responsibilities

- Build Project
- Execute Tests
- Verify Quality
- Build Docker Image

Future

- Deploy Automatically

---

## Deployment Scripts

Responsibilities

- Start Environment
- Stop Environment
- Restart Environment
- Verify Health

---

# Environment Profiles

Development

```
application-dev.yml
```

Testing

```
application-test.yml
```

Production

```
application-prod.yml
```

---

# Environment Variables

Examples

```
SERVER_PORT

REDIS_HOST

REDIS_PORT

JWT_SECRET

LOG_LEVEL

SPRING_PROFILE
```

No secrets should exist inside source code.

---

# Docker Services

```
gateway-1

gateway-2

gateway-3

redis

dashboard
```

---

# Docker Network

Single internal network

```
gateway-network
```

Every service communicates through Docker DNS.

---

# CI Pipeline

Pipeline Stages

```text
Checkout

↓

Build

↓

Unit Tests

↓

Integration Tests

↓

Code Quality

↓

Build Docker Image

↓

Artifact Generation

↓

Pipeline Success
```

---

# Deployment Workflow

```text
Git Push

↓

GitHub Actions

↓

Gradle Build

↓

Tests

↓

Docker Build

↓

Docker Compose

↓

Health Verification
```

---

# Logging Strategy

Production Logs

- JSON Format (Future)
- Structured Logs
- Correlation ID
- Timestamp
- Log Level

---

# Health Verification

Verify

- Gateway Health
- Redis Health
- Dashboard Health

Deployment succeeds only if every service reports healthy.

---

# Startup Validation

During startup verify

- Redis Connectivity
- Configuration
- JWT Secret
- Required Environment Variables
- Port Availability

Startup should fail fast when configuration is invalid.

---

# Security Hardening

Configuration

Must

✓ Externalize Secrets

✓ Validate Startup

✓ Restrict Admin APIs

✓ Use HTTPS (Future)

Must Never

✗ Hardcode Secrets

✗ Expose Internal Stack Traces

✗ Commit Environment Files

---

# Performance Optimization

Review

- JVM Settings
- Docker Resources
- Thread Pool Configuration
- Redis Connections
- Startup Time

Document optimization decisions.

---

# Release Checklist

Before every release

- Tests Pass
- Documentation Updated
- Benchmarks Current
- ADR Updated (if required)
- Docker Builds
- Dashboard Operational
- Redis Healthy
- Cluster Healthy

---

# Production Runbook

Document

- Startup Procedure
- Shutdown Procedure
- Health Verification
- Log Locations
- Failure Recovery
- Redis Recovery
- Gateway Restart
- Dashboard Restart

---

# Testing Requirements

## Unit Tests

- Configuration Validation
- Startup Validation

---

## Integration Tests

- Docker Deployment
- Multi-node Startup
- Redis Connectivity

---

## End-to-End Tests

- Complete Request Flow
- Authentication
- Rate Limiting
- Dashboard
- Cluster

---

## Failure Tests

- Redis Offline
- Gateway Restart
- Dashboard Restart
- Invalid Configuration

---

# Benchmark Requirements

Measure

- Startup Time
- Docker Build Time
- Deployment Time
- Memory Usage
- CPU Usage
- Throughput
- Average Response Time

Document final benchmark report.

---

# Engineering Checklist

## Deployment

- [ ] Docker builds successfully
- [ ] Docker Compose operational
- [ ] Profiles configured

---

## CI/CD

- [ ] Pipeline passes
- [ ] Tests automated
- [ ] Build reproducible

---

## Security

- [ ] Secrets externalized
- [ ] Startup validation complete
- [ ] Production profile verified

---

## Reliability

- [ ] Health checks operational
- [ ] Restart procedure documented
- [ ] Recovery tested

---

## Documentation

- [ ] Deployment guide complete
- [ ] Runbook complete
- [ ] Production architecture updated

---

# Definition of Done

Phase 8 is complete when:

- Gateway deploys with Docker Compose.
- CI pipeline executes successfully.
- Production profiles work.
- Health checks verify deployment.
- Documentation completed.
- Final benchmark report published.
- Engineering Review Checklist passes.

---

# Expected Git Commits

```text
feat: add production docker configuration

feat: add docker compose deployment

feat: configure production profiles

feat: implement startup validation

ci: add github actions workflow

docs: add deployment guide

docs: add production runbook

benchmark: publish final performance report

release: v1.0.0
```

---

# Final Acceptance Checklist

## Functional

- [ ] Gateway Routing
- [ ] Authentication
- [ ] Authorization
- [ ] Rate Limiting
- [ ] Redis Integration
- [ ] Dashboard
- [ ] Analytics

---

## Engineering

- [ ] Clean Architecture
- [ ] SOLID Principles
- [ ] Modular Design
- [ ] Package Boundaries

---

## Testing

- [ ] Unit Tests
- [ ] Integration Tests
- [ ] Stress Tests
- [ ] End-to-End Tests
- [ ] Failure Tests

---

## Performance

- [ ] Benchmarks Published
- [ ] Throughput Measured
- [ ] Latency Measured
- [ ] Resource Usage Documented

---

## Operations

- [ ] Dockerized
- [ ] CI/CD Enabled
- [ ] Health Checks
- [ ] Monitoring Dashboard
- [ ] Deployment Guide
- [ ] Runbook

---

## Documentation

- [ ] README Complete
- [ ] PRD Complete
- [ ] Engineering Spec Complete
- [ ] Architecture Complete
- [ ] Development Playbook Complete
- [ ] ADR Updated
- [ ] Interview Guide Complete

---

# Repository Completion Criteria

The repository is considered complete only when all of the following are true:

✓ Every planned phase has been implemented.

✓ Every engineering checklist has passed.

✓ All automated tests pass.

✓ The Gateway can be deployed using Docker Compose.

✓ Multiple Gateway instances share Redis successfully.

✓ Production documentation is complete.

✓ Benchmark reports are published.

✓ Every major architectural decision is documented.

✓ The developer can explain every module, trade-off, and design decision during an interview.

---

# Repository Outcome

By completing this project, the developer demonstrates practical experience with:

- Java 21
- Spring Boot
- REST APIs
- Spring Security
- JWT Authentication
- Redis
- Distributed Systems
- Rate Limiting Algorithms
- Concurrency
- Docker
- CI/CD
- Observability
- System Design
- Clean Architecture
- Production Engineering

---

# End of Development Playbook