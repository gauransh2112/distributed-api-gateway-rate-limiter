# Sequence Diagrams

Version: 1.0

Status: Approved

---

# Purpose

This directory documents the runtime interactions between the modules of the Distributed API Gateway.

Unlike Architecture documents, which describe the static structure of the system, Sequence Diagrams describe **runtime behavior**.

They answer questions like

- What happens when a request arrives?
- Which module executes first?
- Who calls Redis?
- Where is JWT validated?
- When is the request blocked?
- How is a backend selected?

---

# Goals

Sequence diagrams exist to

- Remove implementation ambiguity
- Define execution order
- Standardize request lifecycle
- Simplify AI implementation
- Improve debugging
- Improve onboarding
- Support interview preparation

---

# Design Principles

Every sequence diagram must

✓ Represent one business flow

✓ Show exact execution order

✓ Show participating modules

✓ Show success flow

✓ Show failure flow

✓ Remain implementation independent

---

# Diagram Conventions

Participants are ordered from left to right according to responsibility.

Typical order

```
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

Redis

↓

Backend

↓

Gateway

↓

Client
```

---

# Diagram Components

Every sequence diagram contains

Purpose

Participants

Preconditions

Main Flow

Alternative Flow

Failure Flow

Postconditions

Related ADRs

Related Engineering Contracts

Related API Specification

---

# Participants

Possible participants

```
Client

Gateway

Authentication

Authorization

Rate Limiter

Routing

Redis

Dashboard

Monitoring

Configuration

Backend Service

JWT Service
```

---

# Message Rules

Messages should describe

Business Actions

Good

```
Validate JWT

Resolve Route

Increment Counter

Generate Token

Forward Request
```

Avoid implementation details like

```
call validateJwt()

invoke rec()

execute method()
```

---

# Alternative Flows

Every diagram should document

Success

↓

Failure

↓

Recovery

Examples

Authentication Failure

↓

401

Redis Failure

↓

Fail Closed

Backend Timeout

↓

504

---

# Error Handling

Every sequence diagram should indicate

- Failure point

- Response generated

- Module responsible

---

# Relationship with Other Documents

```
PRD

↓

Architecture

↓

Sequence Diagram

↓

Implementation

↓

Tests
```

Sequence diagrams define runtime behavior.

---

# Versioning

Every diagram belongs to

Version 1

Future versions should introduce new diagrams rather than modifying existing runtime behavior without documentation.

---

# Diagram List

01 Authentication Login

02 JWT Validation

03 Gateway Request Lifecycle

04 Route Resolution

05 Request Forwarding

06 Token Bucket Flow

07 Sliding Window Flow

08 Redis Lua Execution

09 Configuration Update

10 Dashboard Polling

11 Monitoring Flow

12 Health Check

13 Backend Failure Recovery

14 Gateway Startup

15 Graceful Shutdown

---

# Success Criteria

The Sequence Diagram documentation is complete when

- Every major runtime flow is documented.
- Execution order is unambiguous.
- Failure scenarios are represented.
- AI can implement request flow without guessing.
- Runtime behavior matches Engineering Contracts.

---

# End of Document

# Sequence Diagram — Authentication Login

Diagram ID

SEQ-001

Version

1.0

Status

Approved

Related API

Authentication API

Related Engineering Contract

AUTHENTICATION.md

Related ADRs

- ADR-0005 — JWT Authentication
- ADR-0010 — Stateless Gateway

---

# Purpose

Describe the complete runtime interaction when a user authenticates with the Gateway and receives a JWT.

---

# Participants

```
Client

Gateway

Authentication Controller

Authentication Service

User Repository

Password Encoder

JWT Service
```

---

# Preconditions

- Gateway is running
- Authentication module is initialized
- User exists
- Password is hashed in storage

---

# Main Success Flow

```text
Client
    │
    │ POST /api/v1/auth/login
    ▼
Gateway
    │
    │ Forward request
    ▼
Authentication Controller
    │
    │ Validate Request DTO
    ▼
Authentication Service
    │
    │ Find user
    ▼
User Repository
    │
    │ Return User
    ▼
Authentication Service
    │
    │ Verify password
    ▼
Password Encoder
    │
    │ Password matches
    ▼
Authentication Service
    │
    │ Generate JWT
    ▼
JWT Service
    │
    │ Return JWT
    ▼
Authentication Service
    │
    │ Build LoginResponse
    ▼
Authentication Controller
    │
    │ HTTP 200
    ▼
Gateway
    │
    ▼
Client
```

---

# Success Response

```
HTTP 200 OK
```

```json
{
  "accessToken": "<JWT>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

---

# Alternative Flow A

## Invalid Username

```text
Authentication Service

↓

User Repository

↓

User Not Found

↓

AUTH-001

↓

401 Unauthorized
```

---

# Alternative Flow B

## Invalid Password

```text
Authentication Service

↓

Password Encoder

↓

Password Mismatch

↓

AUTH-001

↓

401 Unauthorized
```

---

# Alternative Flow C

## Validation Failure

```text
Authentication Controller

↓

DTO Validation

↓

Validation Error

↓

422 Unprocessable Entity
```

---

# Failure Flow

```text
JWT Generation Failure

↓

Authentication Service

↓

500 Internal Server Error
```

---

# Security Considerations

Must

✓ Compare hashed passwords

✓ Generate signed JWT

✓ Never log passwords

✓ Never log JWT secret

Must Never

✗ Store plaintext passwords

✗ Return password hashes

✗ Leak implementation details

---

# Performance Target

```
<50 ms
```

---

# Postconditions

✓ User authenticated

✓ JWT generated

✓ No server-side session created

✓ Client receives Bearer token

---

# Related Documents

- 01_AUTHENTICATION_API.md
- AUTHENTICATION.md
- 06_ERROR_RESPONSES.md

---

# Definition of Done

- Login flow implemented
- Password verification works
- JWT generation works
- Error flows implemented
- Integration tests pass

---

# End of Diagram

# Sequence Diagram — JWT Validation

Diagram ID

SEQ-002

Version

1.0

Status

Approved

Related API

Authentication API

Gateway API

Related Engineering Contract

AUTHENTICATION.md

GATEWAY.md

Related ADRs

- ADR-0005 — JWT Authentication
- ADR-0010 — Stateless Gateway

---

# Purpose

Describe how every protected request validates a JWT before entering the Gateway pipeline.

This sequence executes for **every authenticated request**.

---

# Participants

```
Client

Gateway

Authentication Filter

JWT Service

Security Context

Gateway Pipeline
```

---

# Preconditions

- Gateway is running
- JWT Authentication Filter is registered
- JWT Secret is configured
- Request targets a protected endpoint

---

# Main Success Flow

```text
Client
    │
    │ Request
    │ Authorization: Bearer <JWT>
    ▼
Gateway
    │
    ▼
Authentication Filter
    │
    │ Extract JWT
    ▼
JWT Service
    │
    │ Verify Signature
    │
    │ Verify Expiration
    │
    │ Verify Token Format
    ▼
Authentication Filter
    │
    │ Create Authentication Object
    ▼
Security Context
    │
    │ Store Authenticated User
    ▼
Gateway Pipeline
    │
    │ Continue Request
    ▼
Gateway
    │
    ▼
Client
```

---

# Success Result

```
Authenticated Principal

↓

Security Context

↓

Gateway Pipeline Continues
```

---

# Alternative Flow A

## Missing Authorization Header

```text
Authentication Filter

↓

Authorization Header Missing

↓

AUTH-007

↓

401 Unauthorized
```

---

# Alternative Flow B

## Invalid JWT Signature

```text
Authentication Filter

↓

JWT Service

↓

Signature Verification Failed

↓

AUTH-006

↓

401 Unauthorized
```

---

# Alternative Flow C

## Expired JWT

```text
Authentication Filter

↓

JWT Service

↓

Token Expired

↓

AUTH-005

↓

401 Unauthorized
```

---

# Alternative Flow D

## Malformed JWT

```text
Authentication Filter

↓

JWT Parsing Failed

↓

AUTH-008

↓

400 Bad Request
```

---

# Failure Flow

```text
JWT Service

↓

Unexpected Exception

↓

Authentication Filter

↓

500 Internal Server Error
```

---

# Security Considerations

Must

✓ Validate signature

✓ Validate expiration

✓ Reject malformed tokens

✓ Populate Security Context

Must Never

✗ Trust unsigned JWT

✗ Ignore expiration

✗ Log JWT values

✗ Continue with invalid authentication

---

# Performance Target

```
<5 ms
```

JWT validation should never become a Gateway bottleneck.

---

# Postconditions

If successful

✓ Authenticated user stored in Security Context

✓ Gateway pipeline continues

If failed

✓ Request terminated

✓ Standard ErrorResponse returned

---

# Related Documents

- 01_AUTHENTICATION_API.md
- 02_GATEWAY_API.md
- AUTHENTICATION.md
- GATEWAY.md

---

# Definition of Done

- JWT extracted correctly
- Signature validation implemented
- Expiration validation implemented
- Security Context populated
- Invalid JWT rejected
- Integration tests pass

---

# End of Diagram

# Sequence Diagram — Gateway Request Lifecycle

Diagram ID

SEQ-003

Version

1.0

Status

Approved

Related API

Gateway API

Related Engineering Contract

GATEWAY.md

Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0003 — Layered Architecture
- ADR-0010 — Stateless Gateway

---

# Purpose

Describe the complete lifecycle of an incoming request as it traverses the Distributed API Gateway.

This is the **most important runtime sequence** in the system.

Every protected request follows this lifecycle.

---

# Participants

```
Client

Gateway

Authentication Filter

Authorization Layer

Rate Limiter

Redis

Route Resolver

Backend Service

Observability
```

---

# Preconditions

- Gateway is running
- Route configuration is loaded
- Redis is available
- JWT is present
- Backend service is healthy

---

# Main Success Flow

```text
Client
    │
    │ HTTP Request
    ▼
Gateway
    │
    │ Generate Correlation ID
    ▼
Authentication Filter
    │
    │ Validate JWT
    ▼
Authorization Layer
    │
    │ Verify Permissions
    ▼
Rate Limiter
    │
    │ Check Request Limit
    ▼
Redis
    │
    │ Increment Counter
    │
    │ Return Decision
    ▼
Rate Limiter
    │
    │ Request Allowed
    ▼
Route Resolver
    │
    │ Resolve Backend
    ▼
Backend Service
    │
    │ Process Request
    ▼
Gateway
    │
    │ Record Metrics
    ▼
Observability
    │
    │ Publish Logs
    │
    │ Publish Metrics
    ▼
Gateway
    │
    ▼
Client
```

---

# Success Response

The Gateway returns

- Backend Status Code
- Backend Headers
- Backend Response Body

Gateway processing remains transparent.

---

# Alternative Flow A

## Authentication Failure

```text
Authentication Filter

↓

Invalid JWT

↓

401 Unauthorized

↓

Gateway Stops Processing
```

---

# Alternative Flow B

## Authorization Failure

```text
Authorization Layer

↓

Permission Denied

↓

403 Forbidden

↓

Gateway Stops Processing
```

---

# Alternative Flow C

## Rate Limit Exceeded

```text
Rate Limiter

↓

Redis

↓

Request Blocked

↓

429 Too Many Requests

↓

Gateway Stops Processing
```

---

# Alternative Flow D

## Unknown Route

```text
Route Resolver

↓

Route Not Found

↓

404 Not Found

↓

Gateway Stops Processing
```

---

# Alternative Flow E

## Backend Timeout

```text
Backend Service

↓

Timeout

↓

Gateway

↓

504 Gateway Timeout
```

---

# Alternative Flow F

## Backend Unavailable

```text
Backend Service

↓

Connection Failure

↓

Gateway

↓

502 Bad Gateway
```

---

# Failure Flow

```text
Unexpected Gateway Exception

↓

Gateway Exception Handler

↓

500 Internal Server Error
```

---

# Security Considerations

Must

✓ Validate JWT before all business processing

✓ Authorize every protected request

✓ Apply Rate Limiting before forwarding

✓ Preserve Correlation ID

✓ Forward only authenticated requests

Must Never

✗ Bypass Authentication

✗ Bypass Rate Limiter

✗ Skip Authorization

✗ Modify business payloads

---

# Performance Target

Gateway Processing Overhead

```
<20 ms
```

excluding backend processing time.

---

# Postconditions

If successful

✓ Request reaches backend

✓ Metrics published

✓ Logs written

✓ Response returned

If failed

✓ Processing stops immediately

✓ Standard ErrorResponse returned

✓ Failure logged

---

# Runtime Invariants

Every request

Must execute

```
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

The execution order must never change without an approved ADR.

---

# Related Documents

- 02_GATEWAY_API.md
- GATEWAY.md
- RATE_LIMITER.md
- OBSERVABILITY.md

---

# Definition of Done

- Gateway pipeline implemented
- Authentication integrated
- Authorization integrated
- Rate Limiter integrated
- Route Resolution integrated
- Observability integrated
- Failure paths implemented
- Integration tests pass

---

# End of Diagram

# Sequence Diagram — Route Resolution

Diagram ID

SEQ-004

Version

1.0

Status

Approved

Related API

Gateway API

Configuration API

Related Engineering Contract

ROUTING.md

GATEWAY.md

Related ADRs

- ADR-0003 — Layered Architecture
- ADR-0010 — Stateless Gateway

---

# Purpose

Describe how the Gateway determines the correct backend service for an incoming request.

Route Resolution is responsible only for selecting the destination.

It does not

- Authenticate
- Authorize
- Rate Limit
- Process Business Logic

---

# Participants

```
Client

Gateway

Route Resolver

Route Registry

Configuration Store

Backend Service
```

---

# Preconditions

- Gateway has completed Authentication
- Authorization succeeded
- Rate Limiter allowed the request
- Route configuration is loaded

---

# Main Success Flow

```text
Client
    │
    │ HTTP Request
    ▼
Gateway
    │
    │ Forward Request Metadata
    ▼
Route Resolver
    │
    │ Extract Request Path
    ▼
Route Registry
    │
    │ Search Matching Route
    ▼
Configuration Store
    │
    │ Return Route Configuration
    ▼
Route Registry
    │
    │ Route Matched
    ▼
Route Resolver
    │
    │ Select Backend Service
    ▼
Gateway
    │
    │ Forward Request
    ▼
Backend Service
```

---

# Success Result

```
Resolved Route

↓

Backend Target

↓

Gateway Forwarding
```

---

# Route Matching Strategy

Matching priority

```
Exact Match

↓

Longest Prefix Match

↓

Wildcard Match

↓

Default Route

↓

404
```

Example

```
/users/profile

↓

Exact Route
```

wins over

```
/users/**
```

---

# Alternative Flow A

## Route Not Found

```text
Route Registry

↓

No Match

↓

GW-002

↓

404 Not Found
```

---

# Alternative Flow B

## Route Disabled

```text
Configuration Store

↓

Route Disabled

↓

GW-005

↓

503 Service Unavailable
```

---

# Alternative Flow C

## Invalid Route Configuration

```text
Configuration Store

↓

Malformed Configuration

↓

CFG-004

↓

500 Internal Server Error
```

---

# Failure Flow

```text
Unexpected Exception

↓

Route Resolver

↓

Gateway Exception Handler

↓

500 Internal Server Error
```

---

# Route Resolution Rules

The Route Resolver

Must

✓ Match only enabled routes

✓ Return exactly one backend

✓ Be deterministic

✓ Never modify requests

Must Never

✗ Authenticate users

✗ Rate Limit requests

✗ Contact Redis

✗ Execute business logic

---

# Performance Target

```
<2 ms
```

Route resolution should remain an in-memory operation.

---

# Postconditions

If successful

✓ Backend selected

✓ Gateway proceeds to forwarding

If failed

✓ Gateway terminates request

✓ Standard ErrorResponse returned

---

# Runtime Invariants

Every request

Must resolve

exactly one backend.

No request may be forwarded without a resolved route.

---

# Related Documents

- ROUTING.md
- 02_GATEWAY_API.md
- 03_CONFIGURATION_API.md

---

# Definition of Done

- Route matching implemented
- Wildcard matching works
- Disabled routes rejected
- Unknown routes return 404
- Integration tests pass

---

# End of Diagram


# Sequence Diagram — Request Forwarding

Diagram ID

SEQ-005

Version

1.0

Status

Approved

Related API

Gateway API

Related Engineering Contract

GATEWAY.md

ROUTING.md

Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0003 — Layered Architecture
- ADR-0010 — Stateless Gateway

---

# Purpose

Describe how the Gateway forwards an authenticated and authorized request to the selected backend service and returns the backend response to the client.

Request Forwarding is responsible for

- Building the outbound request
- Forwarding headers
- Streaming the request body
- Receiving the backend response
- Returning the response unchanged

It never

- Authenticates users
- Performs business validation
- Modifies payloads
- Applies rate limiting

---

# Participants

```
Client

Gateway

Route Resolver

HTTP Client

Backend Service

Observability
```

---

# Preconditions

- Authentication successful
- Authorization successful
- Rate Limiter allowed request
- Route successfully resolved

---

# Main Success Flow

```text
Client
    │
    │ HTTP Request
    ▼
Gateway
    │
    │ Route Already Resolved
    ▼
HTTP Client
    │
    │ Build Outbound Request
    │
    │ Copy Allowed Headers
    │
    │ Copy Request Body
    ▼
Backend Service
    │
    │ Execute Business Logic
    ▼
HTTP Client
    │
    │ Receive Response
    ▼
Gateway
    │
    │ Publish Metrics
    ▼
Observability
    │
    │ Log Request
    │
    │ Record Latency
    ▼
Gateway
    │
    ▼
Client
```

---

# Success Result

```
Backend Response

↓

Gateway

↓

Client
```

The Gateway behaves as a transparent reverse proxy.

---

# Forwarding Rules

The Gateway forwards

✓ HTTP Method

✓ Path

✓ Query Parameters

✓ Request Body

✓ Authorization Header

✓ Content-Type

✓ Accept

✓ X-Correlation-ID

---

# Header Handling

Forward

```
Authorization

Accept

Content-Type

X-Correlation-ID
```

Remove

```
Connection

Keep-Alive

Transfer-Encoding

Upgrade

Proxy-Authorization
```

Hop-by-hop headers must never be forwarded.

---

# Alternative Flow A

## Backend Returns Error

```text
Backend

↓

404

↓

Gateway

↓

Forward Same Response

↓

Client
```

Gateway should not transform backend business errors.

---

# Alternative Flow B

## Backend Connection Failure

```text
HTTP Client

↓

Connection Refused

↓

Gateway

↓

502 Bad Gateway
```

---

# Alternative Flow C

## Backend Timeout

```text
HTTP Client

↓

Request Timeout

↓

Gateway

↓

504 Gateway Timeout
```

---

# Alternative Flow D

## Invalid Backend Response

```text
Backend

↓

Malformed Response

↓

Gateway

↓

502 Bad Gateway
```

---

# Failure Flow

```text
Unexpected Exception

↓

Gateway Exception Handler

↓

500 Internal Server Error
```

---

# Security Considerations

Must

✓ Preserve Authorization header

✓ Preserve Correlation ID

✓ Validate destination before forwarding

✓ Prevent forwarding to unknown hosts

Must Never

✗ Modify request body

✗ Modify response body

✗ Leak internal Gateway details

✗ Forward hop-by-hop headers

---

# Performance Target

Gateway forwarding overhead

```
<5 ms
```

excluding backend processing time.

---

# Postconditions

If successful

✓ Backend response returned unchanged

✓ Metrics published

✓ Logs written

✓ Request completed

If failed

✓ Appropriate Gateway error returned

✓ Failure logged

---

# Runtime Invariants

The Gateway

Must

✓ Forward exactly one request

✓ Return exactly one response

✓ Preserve request semantics

✓ Preserve response semantics

---

# Related Documents

- 02_GATEWAY_API.md
- GATEWAY.md
- ROUTING.md
- OBSERVABILITY.md

---

# Definition of Done

- HTTP forwarding implemented
- Header forwarding rules enforced
- Request body streaming works
- Response forwarding works
- Backend failures handled
- Integration tests pass

---

# End of Diagram

# Sequence Diagram — Token Bucket Rate Limiting

Diagram ID

SEQ-006

Version

1.0

Status

Approved

Related API

Gateway API

Monitoring API

Related Engineering Contract

RATE_LIMITER.md

REDIS.md

Related ADRs

- ADR-0008 — Redis as Distributed State
- ADR-0009 — Lua Scripts
- ADR-0010 — Stateless Gateway

---

# Purpose

Describe how the Token Bucket algorithm determines whether an incoming request should be allowed or rejected.

This diagram represents the runtime behavior of the Token Bucket implementation.

It is independent of

- Authentication
- Routing
- Backend Services

---

# Participants

```
Gateway

Rate Limiter

Policy Resolver

Token Bucket Strategy

Redis

Lua Script

Gateway
```

---

# Preconditions

- Authentication completed successfully
- Authorization completed successfully
- Route resolved
- Client identified
- Rate limiting policy loaded
- Redis available

---

# Main Success Flow

```text
Gateway
    │
    │ Incoming Request
    ▼
Rate Limiter
    │
    │ Resolve Policy
    ▼
Policy Resolver
    │
    │ Return Token Bucket Policy
    ▼
Rate Limiter
    │
    │ Select Strategy
    ▼
Token Bucket Strategy
    │
    │ Execute Lua Script
    ▼
Redis
    │
    │ Atomically
    │
    │ Refill Tokens
    │
    │ Consume One Token
    │
    │ Return Remaining Tokens
    ▼
Lua Script
    │
    │ Request Allowed
    ▼
Token Bucket Strategy
    │
    │ Allow
    ▼
Rate Limiter
    │
    ▼
Gateway
```

---

# Success Result

```
Token Available

↓

Consume Token

↓

Request Allowed

↓

Gateway Continues
```

---

# Alternative Flow A

## Bucket Empty

```text
Lua Script

↓

No Tokens Available

↓

Reject Request

↓

429 Too Many Requests
```

---

# Alternative Flow B

## Bucket Refilled

```text
Lua Script

↓

Elapsed Time

↓

Calculate New Tokens

↓

Update Bucket

↓

Consume Token

↓

Allow Request
```

---

# Alternative Flow C

## Bucket Created

```text
Redis

↓

Key Not Found

↓

Create Bucket

↓

Initialize Capacity

↓

Consume First Token

↓

Allow Request
```

---

# Failure Flow

## Redis Unavailable

```text
Redis

↓

Connection Failure

↓

Rate Limiter

↓

Configured Failure Policy

↓

Fail Open

or

Fail Closed
```

Failure policy must be configurable.

---

# Redis Operations

Each request performs

```
GET Bucket

↓

Calculate Refill

↓

Update Bucket

↓

Consume Token

↓

Set TTL

↓

Return Decision
```

Version 1 performs all operations using a **single Lua script**.

---

# Lua Script Responsibilities

The Lua script performs atomically

✓ Load Bucket

✓ Calculate Elapsed Time

✓ Refill Tokens

✓ Consume Token

✓ Save Bucket

✓ Return Decision

No partial updates are allowed.

---

# Token Bucket State

Stored in Redis

```
Capacity

Current Tokens

Last Refill Timestamp
```

Example

```json
{
    "tokens": 87,
    "capacity": 100,
    "lastRefill": 1722587200
}
```

---

# Security Considerations

Must

✓ Execute atomically

✓ Prevent race conditions

✓ Use distributed state

✓ Remain deterministic

Must Never

✗ Use JVM synchronization

✗ Store state locally

✗ Lose tokens under concurrency

---

# Performance Target

Decision latency

```
<2 ms
```

Redis round trips

```
Exactly One
```

Lua execution

```
<1 ms
```

---

# Postconditions

If successful

✓ Token consumed

✓ Bucket updated

✓ TTL refreshed

✓ Request forwarded

If rejected

✓ Bucket unchanged except refill

✓ 429 returned

✓ Retry-After calculated

---

# Runtime Invariants

Every request

Must

✓ Consume at most one token

✓ Never produce negative tokens

✓ Execute atomically

✓ Produce deterministic results

---

# Related Documents

- RATE_LIMITER.md
- REDIS.md
- 02_GATEWAY_API.md
- 04_MONITORING_API.md

---

# Definition of Done

- Token Bucket implemented
- Redis integration complete
- Lua script implemented
- Atomic execution verified
- Retry-After supported
- Concurrent requests tested
- Stress tests passed

---

# End of Diagram

# Sequence Diagram — Sliding Window Counter

Diagram ID

SEQ-007

Version

1.0

Status

Approved

Related API

Gateway API

Monitoring API

Related Engineering Contract

RATE_LIMITER.md

REDIS.md

Related ADRs

- ADR-0008 — Redis as Distributed State
- ADR-0009 — Lua Scripts
- ADR-0010 — Stateless Gateway

---

# Purpose

Describe the runtime execution of the Sliding Window Counter algorithm.

Unlike the Fixed Window algorithm, Sliding Window Counter approximates a moving time window using the current and previous window counts to reduce burst traffic at window boundaries.

This algorithm is selected by the Rate Limiter through the Strategy Pattern.

---

# Participants

```
Gateway

Rate Limiter

Policy Resolver

Sliding Window Counter Strategy

Redis

Lua Script

Gateway
```

---

# Preconditions

- Authentication completed
- Authorization completed
- Route resolved
- Client identified
- Sliding Window Counter policy configured
- Redis available

---

# Main Success Flow

```text
Gateway
    │
    │ Incoming Request
    ▼
Rate Limiter
    │
    │ Resolve Policy
    ▼
Policy Resolver
    │
    │ Return Sliding Window Policy
    ▼
Rate Limiter
    │
    │ Select Strategy
    ▼
Sliding Window Counter Strategy
    │
    │ Execute Lua Script
    ▼
Redis
    │
    │ Load Current Window Count
    │
    │ Load Previous Window Count
    │
    │ Calculate Window Weight
    │
    │ Compute Effective Request Count
    │
    │ Compare Against Limit
    ▼
Lua Script
    │
    │ Request Allowed
    ▼
Sliding Window Counter Strategy
    │
    │ Increment Current Window
    ▼
Redis
    │
    │ Persist Updated Counter
    ▼
Gateway
```

---

# Success Result

```
Effective Count

↓

Within Limit

↓

Increment Counter

↓

Request Allowed
```

---

# Alternative Flow A

## Request Exceeds Limit

```text
Lua Script

↓

Effective Count > Limit

↓

Reject Request

↓

429 Too Many Requests
```

---

# Alternative Flow B

## Window Rolled Over

```text
Redis

↓

Current Window Expired

↓

Current Becomes Previous

↓

Create New Current Window

↓

Process Request
```

---

# Alternative Flow C

## First Request

```text
Redis

↓

Window Does Not Exist

↓

Initialize Window

↓

Count = 1

↓

Allow Request
```

---

# Failure Flow

## Redis Unavailable

```text
Redis

↓

Connection Failure

↓

Rate Limiter

↓

Configured Failure Policy

↓

Fail Open

or

Fail Closed
```

---

# Redis Operations

Each request performs

```
Read Current Window

↓

Read Previous Window

↓

Calculate Effective Count

↓

Update Current Counter

↓

Refresh TTL

↓

Return Decision
```

All operations execute atomically.

---

# Effective Count Formula

```
Effective Count

=

Current Window Count

+

Previous Window Count × Remaining Window Percentage
```

Example

```
Current Window

↓

40 Requests

Previous Window

↓

60 Requests

Remaining Weight

↓

50%

Effective Count

↓

40 + (60 × 0.5)

↓

70
```

---

# Redis State

Stored per client

```
Current Window Counter

Previous Window Counter

Current Window Timestamp
```

Example

```json
{
  "currentWindow": 42,
  "previousWindow": 58,
  "windowStart": 1722587600
}
```

---

# Lua Script Responsibilities

The Lua script performs atomically

✓ Read Window Counters

✓ Calculate Effective Count

✓ Determine Allow / Reject

✓ Update Counter

✓ Refresh TTL

✓ Return Remaining Capacity

---

# Security Considerations

Must

✓ Execute atomically

✓ Prevent race conditions

✓ Maintain distributed consistency

✓ Never lose increments

Must Never

✗ Use local JVM counters

✗ Execute multiple Redis transactions

✗ Produce inconsistent counts

---

# Performance Target

Decision latency

```
<2 ms
```

Redis operations

```
Single Lua Script
```

Memory

```
Constant per client
```

---

# Postconditions

If successful

✓ Current Window updated

✓ TTL refreshed

✓ Request forwarded

If rejected

✓ Counters preserved

✓ Retry-After calculated

✓ Standard ErrorResponse returned

---

# Runtime Invariants

Every request

Must

✓ Use current and previous windows

✓ Execute atomically

✓ Never double count

✓ Never exceed configured limit

---

# Related Documents

- RATE_LIMITER.md
- REDIS.md
- 02_GATEWAY_API.md
- 04_MONITORING_API.md

---

# Definition of Done

- Sliding Window Counter implemented
- Effective count calculation verified
- Redis Lua implementation complete
- Window rollover tested
- Concurrent requests tested
- Stress tests passed

---

# End of Diagram

# Sequence Diagram — Redis Lua Script Execution

Diagram ID

SEQ-008

Version

1.0

Status

Approved

Related API

Gateway API

Monitoring API

Related Engineering Contract

REDIS.md

RATE_LIMITER.md

Related ADRs

- ADR-0008 — Redis as Distributed State
- ADR-0009 — Lua Scripts
- ADR-0010 — Stateless Gateway

---

# Purpose

Describe how Redis executes Lua scripts atomically to support distributed rate limiting.

This sequence is shared by

- Token Bucket
- Sliding Window Counter
- Sliding Window Log
- Fixed Window
- Leaky Bucket

Every algorithm requiring multiple Redis operations must execute through a Lua script.

---

# Participants

```
Gateway

Rate Limiter

Rate Limiting Strategy

Redis Service

Lua Executor

Redis Server
```

---

# Preconditions

- Request reached Rate Limiter
- Strategy selected
- Redis connection available
- Lua script loaded
- Client identified

---

# Main Success Flow

```text
Gateway
    │
    │ Incoming Request
    ▼
Rate Limiter
    │
    │ Select Strategy
    ▼
Rate Limiting Strategy
    │
    │ Prepare Script Parameters
    ▼
Redis Service
    │
    │ Execute Lua Script
    ▼
Lua Executor
    │
    │ Send EVALSHA
    ▼
Redis Server
    │
    │ Execute Script
    │
    │ Read Keys
    │
    │ Calculate Decision
    │
    │ Update State
    │
    │ Refresh TTL
    │
    │ Return Result
    ▼
Lua Executor
    │
    │ Parse Result
    ▼
Redis Service
    │
    │ Return Decision
    ▼
Rate Limiting Strategy
    │
    │ Allow / Reject
    ▼
Rate Limiter
    │
    ▼
Gateway
```

---

# Success Result

```
Lua Script

↓

Atomic Decision

↓

Gateway Continues
```

---

# Atomic Operations

Every Lua script performs

```
Read State

↓

Compute Decision

↓

Update State

↓

Set TTL

↓

Return Result
```

No intermediate state should ever be visible.

---

# Alternative Flow A

## Script Not Cached

```text
Lua Executor

↓

EVALSHA

↓

NOSCRIPT

↓

Load Script

↓

Retry

↓

Execute Successfully
```

Script loading should happen automatically.

---

# Alternative Flow B

## Request Blocked

```text
Lua Script

↓

Limit Exceeded

↓

Return Reject

↓

429 Too Many Requests
```

---

# Alternative Flow C

## New Client

```text
Redis

↓

Key Missing

↓

Initialize State

↓

Apply Algorithm

↓

Return Allow
```

---

# Failure Flow A

## Redis Connection Failure

```text
Redis Service

↓

Connection Lost

↓

Rate Limiter

↓

Configured Failure Policy

↓

Fail Open

or

Fail Closed
```

---

# Failure Flow B

## Lua Execution Failure

```text
Redis Server

↓

Lua Runtime Error

↓

Redis Service

↓

Log Error

↓

Gateway Failure Policy
```

---

# Lua Script Inputs

Every script receives

```
Redis Keys

Current Timestamp

Client Identifier

Policy Configuration

Algorithm Parameters
```

No script should rely on JVM state.

---

# Lua Script Outputs

Every script returns

```
Allow / Reject

Remaining Capacity

Retry After

Updated State
```

The returned structure must remain consistent across algorithms.

---

# Redis Responsibilities

Redis owns

✓ Atomic execution

✓ Data persistence

✓ TTL updates

✓ Concurrency handling

Redis does not own

✗ Business rules

✗ Policy resolution

✗ Strategy selection

---

# Security Considerations

Must

✓ Execute atomically

✓ Prevent race conditions

✓ Never expose Redis internals

✓ Validate script parameters

Must Never

✗ Execute partial updates

✗ Depend on JVM synchronization

✗ Store local counters

---

# Performance Target

Lua Execution

```
<1 ms
```

Total Redis Latency

```
<2 ms
```

Network Round Trips

```
Exactly One
```

---

# Postconditions

If successful

✓ State updated

✓ TTL refreshed

✓ Atomic consistency preserved

✓ Decision returned

If failed

✓ No partial updates

✓ Error logged

✓ Failure policy applied

---

# Runtime Invariants

Every Lua execution

Must

✓ Be atomic

✓ Produce deterministic results

✓ Complete in one Redis transaction

✓ Never expose inconsistent state

---

# Related Documents

- REDIS.md
- RATE_LIMITER.md
- 11_REDIS_DESIGN/
- ADR-0009 — Lua Scripts

---

# Definition of Done

- Lua script implemented
- Script caching implemented
- Atomic execution verified
- Redis failure handling implemented
- Concurrent execution tested
- Stress tests passed

---

# End of Diagram

# Sequence Diagram — Configuration Update

Diagram ID

SEQ-009

Version

1.0

Status

Approved

Related API

Configuration API

Related Engineering Contract

CONFIGURATION.md

Related ADRs

- ADR-0003 — Layered Architecture
- ADR-0014 — Package-by-Feature

---

# Purpose

Describe how an administrator updates Gateway configuration, such as routes or Rate Limiting policies, while ensuring consistency across the application.

Configuration updates affect future requests only.

Requests already being processed continue using the previously loaded configuration.

---

# Participants

```
Administrator

Dashboard

Gateway

Configuration Controller

Configuration Service

Configuration Validator

Configuration Repository

Route Registry

Observability
```

---

# Preconditions

- Administrator authenticated
- Administrator authorized
- Gateway running
- Configuration subsystem initialized

---

# Main Success Flow

```text
Administrator
      │
      │ Update Configuration
      ▼
Dashboard
      │
      │ REST Request
      ▼
Gateway
      │
      ▼
Configuration Controller
      │
      │ Validate Request DTO
      ▼
Configuration Service
      │
      │ Validate Business Rules
      ▼
Configuration Validator
      │
      │ Configuration Valid
      ▼
Configuration Service
      │
      │ Persist Configuration
      ▼
Configuration Repository
      │
      │ Configuration Saved
      ▼
Configuration Service
      │
      │ Reload Route Registry
      ▼
Route Registry
      │
      │ Refresh Cache
      ▼
Observability
      │
      │ Publish Audit Event
      ▼
Gateway
      │
      ▼
Administrator
```

---

# Success Result

```
Configuration Updated

↓

Route Registry Refreshed

↓

Future Requests Use New Configuration
```

---

# Alternative Flow A

## Validation Failure

```text
Configuration Validator

↓

Validation Failed

↓

422 Unprocessable Entity
```

---

# Alternative Flow B

## Duplicate Route

```text
Configuration Repository

↓

Duplicate Route

↓

409 Conflict
```

---

# Alternative Flow C

## Invalid Backend URI

```text
Configuration Validator

↓

Invalid URI

↓

400 Bad Request
```

---

# Failure Flow

## Configuration Persistence Failure

```text
Configuration Repository

↓

Save Failed

↓

Configuration Service

↓

Rollback

↓

500 Internal Server Error
```

No partial configuration should become active.

---

# Configuration Validation Rules

Before activation

Configuration must pass

✓ Route uniqueness

✓ Valid URI

✓ Supported HTTP methods

✓ Valid Rate Limiter policy

✓ No conflicting routes

Only valid configurations may become active.

---

# Route Registry Reload

Configuration changes

```
Persist Configuration

↓

Invalidate Registry Cache

↓

Reload Registry

↓

Serve Future Requests
```

Active requests continue using the old registry.

---

# Observability Responsibilities

After successful update

Publish

✓ Configuration Changed

✓ Administrator ID

✓ Timestamp

✓ Correlation ID

✓ Updated Resource

Audit events must be immutable.

---

# Security Considerations

Must

✓ Require Administrator role

✓ Validate all input

✓ Audit every change

✓ Prevent partial updates

Must Never

✗ Allow anonymous configuration

✗ Activate invalid configuration

✗ Modify active requests

---

# Performance Target

Configuration Update

```
<100 ms
```

Registry Reload

```
<20 ms
```

---

# Postconditions

If successful

✓ Configuration persisted

✓ Registry refreshed

✓ Metrics published

✓ Audit log created

If failed

✓ Previous configuration preserved

✓ No partial activation

✓ Error returned

---

# Runtime Invariants

Configuration updates

Must

✓ Be validated before activation

✓ Be atomic

✓ Preserve consistency

✓ Never interrupt active requests

---

# Related Documents

- CONFIGURATION.md
- 03_CONFIGURATION_API.md
- OBSERVABILITY.md

---

# Definition of Done

- Configuration update implemented
- Validation implemented
- Route registry reload implemented
- Audit logging implemented
- Rollback on failure implemented
- Integration tests pass

---

# End of Diagram

# Sequence Diagram — Dashboard Polling

Diagram ID

SEQ-010

Version

1.0

Status

Approved

Related API

Dashboard API

Monitoring API

Related Engineering Contract

DASHBOARD.md

OBSERVABILITY.md

Related ADRs

- ADR-0012 — React Dashboard
- ADR-0015 — Observability Strategy

---

# Purpose

Describe how the Dashboard periodically retrieves operational data from the Gateway to provide administrators with real-time system visibility.

Version 1 uses **Polling**.

Future versions may replace polling with

- WebSocket
- Server-Sent Events (SSE)

without changing the Dashboard architecture.

---

# Participants

```
Administrator

Dashboard

Gateway

Monitoring Controller

Metrics Service

Redis

Observability
```

---

# Preconditions

- Administrator authenticated
- Dashboard loaded
- Gateway healthy
- Monitoring services available

---

# Main Success Flow

```text
Administrator
      │
      │ Open Dashboard
      ▼
Dashboard
      │
      │ Start Polling Timer (5 sec)
      ▼
Gateway
      │
      │ GET /api/v1/dashboard/overview
      ▼
Monitoring Controller
      │
      │ Collect Metrics
      ▼
Metrics Service
      │
      │ Query Runtime Metrics
      ▼
Redis
      │
      │ Return Counters
      ▼
Metrics Service
      │
      │ Aggregate Metrics
      ▼
Monitoring Controller
      │
      │ Build Dashboard DTO
      ▼
Gateway
      │
      │ JSON Response
      ▼
Dashboard
      │
      │ Refresh Widgets
      ▼
Administrator
```

---

# Success Result

```
Latest Metrics

↓

Dashboard Updated

↓

Next Poll Scheduled
```

---

# Polling Interval

Default

```
5 Seconds
```

Future

```
Configurable

↓

1s

5s

10s

30s
```

---

# Alternative Flow A

## Monitoring API Failure

```text
Gateway

↓

500 Internal Server Error

↓

Dashboard

↓

Display Error Widget

↓

Retry Next Poll
```

Dashboard should remain usable.

---

# Alternative Flow B

## Redis Unavailable

```text
Metrics Service

↓

Redis Failure

↓

Partial Metrics

↓

Dashboard Shows Warning
```

Unavailable metrics should not crash the Dashboard.

---

# Alternative Flow C

## Administrator Closes Dashboard

```text
Dashboard

↓

Stop Polling Timer

↓

Release Resources
```

---

# Failure Flow

## Network Failure

```text
Dashboard

↓

HTTP Request Failed

↓

Retry Next Interval

↓

Display Connection Warning
```

The Dashboard should recover automatically.

---

# Dashboard Responsibilities

Dashboard

Must

✓ Schedule polling

✓ Update only changed components

✓ Handle failures gracefully

✓ Never block the UI

Must Never

✗ Crash because one API fails

✗ Perform business calculations

✗ Store operational metrics permanently

---

# Metrics Retrieved

Dashboard requests

✓ Gateway Status

✓ Redis Status

✓ Request Count

✓ Success Rate

✓ Error Rate

✓ Average Latency

✓ Rate Limiter Statistics

✓ JVM Statistics

---

# Performance Target

Dashboard API Response

```
<40 ms
```

UI Refresh

```
<100 ms
```

Polling Overhead

Negligible

---

# Security Considerations

Must

✓ Authenticate administrator

✓ Validate JWT

✓ Never expose secrets

✓ Return only operational information

Must Never

✗ Expose Redis credentials

✗ Expose JWT secrets

✗ Expose internal exceptions

---

# Postconditions

If successful

✓ Widgets refreshed

✓ Charts updated

✓ Metrics synchronized

If failed

✓ Previous values preserved

✓ Warning displayed

✓ Automatic retry scheduled

---

# Runtime Invariants

Dashboard

Must

✓ Never directly access Redis

✓ Consume only Dashboard APIs

✓ Poll at configured interval

✓ Continue operating despite partial failures

---

# Future Evolution

Version 2

```
Polling

↓

Server-Sent Events
```

Version 3

```
Server-Sent Events

↓

WebSockets
```

The Dashboard architecture should support these upgrades without redesign.

---

# Related Documents

- DASHBOARD.md
- OBSERVABILITY.md
- 04_MONITORING_API.md
- 05_DASHBOARD_API.md

---

# Definition of Done

- Polling implemented
- Dashboard refresh implemented
- Partial failure handling implemented
- Automatic retry implemented
- Performance targets achieved
- Integration tests pass

---

# End of Diagram

# Sequence Diagram — Monitoring Flow

Diagram ID

SEQ-011

Version

1.0

Status

Approved

Related API

Monitoring API

Dashboard API

Related Engineering Contract

OBSERVABILITY.md

Related ADRs

- ADR-0012 — React Dashboard
- ADR-0015 — Observability Strategy

---

# Purpose

Describe how operational metrics are collected, aggregated, and exposed by the Monitoring module.

The Monitoring module is responsible only for collecting and exposing telemetry.

It never participates in request processing.

---

# Participants

```
Gateway

Authentication

Rate Limiter

Routing

Redis

Observability

Metrics Registry

Monitoring Controller

Dashboard
```

---

# Preconditions

- Gateway running
- Metrics Registry initialized
- Observability enabled
- Dashboard connected

---

# Main Success Flow

```text
Gateway
      │
      │ Request Completed
      ▼
Observability
      │
      │ Record Request Metrics
      ▼
Metrics Registry
      │
      │ Update Counters
      │
      │ Update Timers
      │
      │ Update Gauges
      ▼
Monitoring Controller
      │
      │ GET /monitoring/metrics
      ▼
Metrics Registry
      │
      │ Aggregate Statistics
      ▼
Monitoring Controller
      │
      │ Build Response DTO
      ▼
Dashboard
      │
      │ Refresh Charts
      ▼
Administrator
```

---

# Success Result

```
Metrics Updated

↓

Metrics Aggregated

↓

Dashboard Refreshed
```

---

# Metrics Collection

Every completed request updates

```
Total Requests

↓

Successful Requests

↓

Failed Requests

↓

Latency

↓

Request Duration

↓

Rate Limiter Metrics

↓

Redis Metrics
```

---

# Alternative Flow A

## Redis Metrics Unavailable

```text
Redis

↓

Unavailable

↓

Observability

↓

Skip Redis Metrics

↓

Return Partial Metrics
```

Monitoring should continue.

---

# Alternative Flow B

## Dashboard Offline

```text
Dashboard

↓

Disconnected

↓

Metrics Continue Collecting

↓

No Data Lost
```

Metric collection is independent of the Dashboard.

---

# Alternative Flow C

## Monitoring Endpoint Requested

```text
Administrator

↓

GET /monitoring/metrics

↓

Monitoring Controller

↓

Metrics Registry

↓

Response DTO

↓

Administrator
```

---

# Failure Flow

## Metrics Collection Failure

```text
Observability

↓

Metric Update Failure

↓

Log Error

↓

Continue Request
```

Monitoring failures must never interrupt request processing.

---

# Metrics Registry Responsibilities

The Metrics Registry owns

✓ Counters

✓ Timers

✓ Gauges

✓ Histograms (Future)

✓ Percentiles (Future)

It does not own business logic.

---

# Metrics Published

Gateway

✓ Total Requests

✓ Active Requests

✓ Average Latency

Authentication

✓ Successful Logins

✓ Failed Logins

Rate Limiter

✓ Allowed Requests

✓ Blocked Requests

Redis

✓ Latency

✓ Connections

JVM

✓ Heap Usage

✓ Thread Count

✓ CPU Usage

---

# Performance Target

Metric Recording

```
<1 ms
```

Monitoring Endpoint

```
<30 ms
```

Metrics aggregation should never become a bottleneck.

---

# Security Considerations

Must

✓ Hide sensitive information

✓ Authenticate monitoring endpoints

✓ Protect administrator metrics

Must Never

✗ Expose secrets

✗ Expose JWTs

✗ Expose internal exceptions

---

# Postconditions

If successful

✓ Metrics updated

✓ Dashboard refreshed

✓ Logs correlated

If failed

✓ Request still succeeds

✓ Failure logged

---

# Runtime Invariants

Observability

Must

✓ Never modify business logic

✓ Never block request processing

✓ Collect metrics asynchronously where possible

✓ Remain stateless

---

# Related Documents

- OBSERVABILITY.md
- 04_MONITORING_API.md
- 05_DASHBOARD_API.md

---

# Definition of Done

- Metrics collection implemented
- Metrics registry implemented
- Monitoring endpoints implemented
- Dashboard integration complete
- Failure isolation verified
- Integration tests pass

---

# End of Diagram


# Sequence Diagram — Health Check

Diagram ID

SEQ-012

Version

1.0

Status

Approved

Related API

Monitoring API

Gateway API

Related Engineering Contract

OBSERVABILITY.md

REDIS.md

GATEWAY.md

Related ADRs

- ADR-0011 — Docker Compose
- ADR-0015 — Observability Strategy

---

# Purpose

Describe how the Gateway determines its operational health and exposes it through the Health endpoint.

The Health Check sequence is responsible for determining whether the Gateway is capable of serving requests.

It is **not** responsible for processing client traffic.

---

# Participants

```
Health Client

Gateway

Health Controller

Health Service

Redis

JVM

Observability
```

---

# Preconditions

- Gateway application started
- Health subsystem initialized
- Redis configuration loaded
- JVM running

---

# Main Success Flow

```text
Health Client
      │
      │ GET /api/v1/monitoring/health
      ▼
Gateway
      │
      ▼
Health Controller
      │
      │ Request Health Status
      ▼
Health Service
      │
      │ Check Gateway
      │
      │ Check Redis
      │
      │ Check JVM
      ▼
Redis
      │
      │ PING
      ▼
Health Service
      │
      │ Redis Healthy
      ▼
JVM
      │
      │ Heap
      │
      │ Threads
      │
      │ CPU
      ▼
Health Service
      │
      │ Aggregate Health
      ▼
Observability
      │
      │ Publish Health Metrics
      ▼
Gateway
      │
      │ HTTP 200
      ▼
Health Client
```

---

# Success Response

```json
{
  "status": "UP",
  "gateway": "UP",
  "redis": "UP",
  "jvm": "UP",
  "timestamp": "2026-08-02T11:40:00Z"
}
```

---

# Alternative Flow A

## Redis Unavailable

```text
Health Service

↓

Redis Ping Failed

↓

Redis = DOWN

↓

Gateway = DEGRADED

↓

Return Health Response
```

Gateway may still serve requests depending on failure policy.

---

# Alternative Flow B

## High JVM Memory Usage

```text
JVM

↓

Heap Above Threshold

↓

Status = DEGRADED

↓

Return Warning
```

Application continues operating.

---

# Alternative Flow C

## Multiple Failed Components

```text
Redis

↓

DOWN

JVM

↓

DOWN

↓

Gateway Status

↓

DOWN

↓

503 Service Unavailable
```

---

# Failure Flow

## Unexpected Health Check Failure

```text
Health Service

↓

Unexpected Exception

↓

Observability

↓

Log Error

↓

503 Service Unavailable
```

---

# Health Evaluation Rules

Gateway Health

Depends on

✓ Application Running

Redis Health

Depends on

✓ Successful PING

JVM Health

Depends on

✓ Heap Usage

✓ Thread Availability

✓ Runtime Status

Overall Health

```
All Components UP

↓

Gateway UP
```

Otherwise

```
One or More Critical Components DOWN

↓

Gateway DOWN
```

---

# Performance Target

Health Check

```
<20 ms
```

Redis Ping

```
<2 ms
```

---

# Security Considerations

Development

```
Public Endpoint
```

Production

```
Administrator Only
```

Health endpoint must never expose

✗ Passwords

✗ Secrets

✗ Internal IPs

✗ Stack Traces

---

# Postconditions

If successful

✓ Health status published

✓ Metrics updated

✓ Response returned

If failed

✓ Error logged

✓ Failure reported

---

# Runtime Invariants

Health checks

Must

✓ Never modify application state

✓ Never block request processing

✓ Complete quickly

✓ Return deterministic results

---

# Related Documents

- OBSERVABILITY.md
- REDIS.md
- 04_MONITORING_API.md

---

# Definition of Done

- Health endpoint implemented
- Redis health verified
- JVM health verified
- Aggregated health implemented
- Failure scenarios handled
- Integration tests pass

---

# End of Diagram

# Sequence Diagram — Backend Failure Recovery

Diagram ID

SEQ-013

Version

1.0

Status

Approved

Related API

Gateway API

Monitoring API

Related Engineering Contract

GATEWAY.md

OBSERVABILITY.md

ROUTING.md

Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0015 — Observability Strategy

---

# Purpose

Describe how the Gateway handles failures occurring while communicating with backend services.

The Gateway is responsible for

- Detecting backend failures
- Returning standardized responses
- Recording metrics
- Logging failures

It is not responsible for recovering backend services.

---

# Participants

```
Client

Gateway

Route Resolver

HTTP Client

Backend Service

Observability
```

---

# Preconditions

- Authentication successful
- Authorization successful
- Rate Limiter approved request
- Route successfully resolved

---

# Main Success Flow

```text
Client
      │
      │ HTTP Request
      ▼
Gateway
      │
      │ Resolve Backend
      ▼
Route Resolver
      │
      │ Backend Selected
      ▼
HTTP Client
      │
      │ Forward Request
      ▼
Backend Service
      │
      │ Successful Response
      ▼
HTTP Client
      │
      │ Return Response
      ▼
Gateway
      │
      │ Publish Metrics
      ▼
Observability
      │
      │ Log Success
      ▼
Client
```

---

# Alternative Flow A

## Backend Connection Refused

```text
HTTP Client

↓

Connection Refused

↓

Gateway

↓

Return

502 Bad Gateway

↓

Publish Failure Metrics
```

---

# Alternative Flow B

## Backend Timeout

```text
HTTP Client

↓

Timeout

↓

Gateway

↓

504 Gateway Timeout

↓

Record Timeout Metrics
```

---

# Alternative Flow C

## Backend Returns 5xx

```text
Backend Service

↓

500 Internal Server Error

↓

Gateway

↓

Forward Same Response

↓

Client
```

The Gateway should not modify backend-generated business errors.

---

# Alternative Flow D

## Backend Returns 4xx

```text
Backend

↓

404

↓

Gateway

↓

Forward Response

↓

Client
```

Business responses remain transparent.

---

# Failure Flow

## Unexpected Gateway Failure

```text
Gateway

↓

Unexpected Exception

↓

Gateway Exception Handler

↓

500 Internal Server Error

↓

Observability
```

---

# Failure Classification

Infrastructure Failures

- Connection Refused
- Timeout
- DNS Failure
- SSL Failure

Gateway Responses

```
502

504
```

Business Failures

Remain unchanged.

---

# Observability Responsibilities

On every backend failure

Publish

✓ Failure Count

✓ Backend Identifier

✓ Response Time

✓ Correlation ID

✓ Failure Type

Log entries must support production debugging.

---

# Retry Policy

Version 1

```
No Automatic Retry
```

Reason

Avoid duplicate requests.

Future

Retry policies may be introduced for

- Safe GET requests
- Idempotent operations

---

# Performance Target

Failure detection

```
< Timeout Threshold
```

Gateway overhead

```
<5 ms
```

---

# Security Considerations

Must

✓ Hide backend implementation

✓ Preserve Correlation ID

✓ Standardize infrastructure errors

Must Never

✗ Leak internal hostnames

✗ Leak stack traces

✗ Leak backend implementation details

---

# Postconditions

If successful

✓ Backend response forwarded

If infrastructure failure

✓ Standard Gateway error returned

✓ Metrics published

✓ Logs written

---

# Runtime Invariants

Gateway

Must

✓ Never retry unsafe requests

✓ Never modify successful backend responses

✓ Always publish failure metrics

✓ Always return standardized infrastructure errors

---

# Related Documents

- GATEWAY.md
- OBSERVABILITY.md
- 02_GATEWAY_API.md
- 04_MONITORING_API.md

---

# Definition of Done

- Connection failures handled
- Timeout handling implemented
- Standardized error responses implemented
- Metrics published
- Failure logging implemented
- Integration tests pass

---

# End of Diagram

# Sequence Diagram — Gateway Startup

Diagram ID

SEQ-014

Version

1.0

Status

Approved

Related API

Gateway API

Monitoring API

Related Engineering Contract

GATEWAY.md

CONFIGURATION.md

REDIS.md

OBSERVABILITY.md

Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0002 — Spring Boot Framework
- ADR-0008 — Redis as Distributed State
- ADR-0011 — Docker Compose

---

# Purpose

Describe how the Distributed API Gateway initializes itself during application startup.

Gateway startup is responsible for

- Initializing Spring Boot
- Loading configuration
- Connecting to Redis
- Initializing Gateway modules
- Registering routes
- Publishing readiness

No client traffic should be accepted until startup completes successfully.

---

# Participants

```
Spring Boot

Gateway

Configuration Loader

Route Registry

Redis

Rate Limiter

Observability

Health Service
```

---

# Preconditions

- Application process started
- Configuration files available
- Environment variables loaded

---

# Main Success Flow

```text
Spring Boot
      │
      │ Start Application
      ▼
Gateway
      │
      │ Load Configuration
      ▼
Configuration Loader
      │
      │ Parse Properties
      ▼
Gateway
      │
      │ Initialize Beans
      ▼
Redis
      │
      │ Establish Connection
      ▼
Gateway
      │
      │ Initialize Rate Limiter
      ▼
Rate Limiter
      │
      │ Load Algorithms
      ▼
Gateway
      │
      │ Load Route Configuration
      ▼
Route Registry
      │
      │ Register Routes
      ▼
Gateway
      │
      │ Initialize Observability
      ▼
Observability
      │
      │ Register Metrics
      ▼
Health Service
      │
      │ Mark Application READY
      ▼
Gateway
      │
      │ Accept Requests
```

---

# Success Result

```
Gateway Started

↓

Routes Loaded

↓

Redis Connected

↓

Health = UP

↓

Gateway Ready
```

---

# Startup Order

Initialization order

```
Configuration

↓

Spring Context

↓

Redis

↓

Rate Limiter

↓

Route Registry

↓

Observability

↓

Health Service

↓

Gateway Ready
```

This order must remain deterministic.

---

# Alternative Flow A

## Redis Connection Failure

```text
Gateway

↓

Redis Connection Failed

↓

Retry Connection

↓

Failure Policy

↓

Startup Failed
```

Gateway should not enter READY state.

---

# Alternative Flow B

## Configuration Error

```text
Configuration Loader

↓

Invalid Configuration

↓

Startup Aborted

↓

Application Exit
```

---

# Alternative Flow C

## Route Loading Failure

```text
Route Registry

↓

Invalid Route

↓

Startup Failed

↓

Gateway Stops
```

---

# Failure Flow

## Bean Initialization Failure

```text
Spring Boot

↓

Bean Creation Exception

↓

Application Startup Failed

↓

Exit Process
```

---

# Startup Validation

Before accepting requests

Gateway verifies

✓ Configuration loaded

✓ Redis connected

✓ Routes registered

✓ Health initialized

✓ Metrics registered

✓ Security filters initialized

---

# Readiness Criteria

Gateway becomes READY only when

```
Configuration Loaded

AND

Redis Connected

AND

Routes Loaded

AND

Health Service Active

AND

Observability Initialized
```

---

# Performance Target

Complete Startup

```
<10 seconds
```

Redis Connection

```
<2 seconds
```

Route Loading

```
<500 ms
```

---

# Security Considerations

Must

✓ Validate configuration

✓ Secure Redis credentials

✓ Initialize JWT security before serving traffic

Must Never

✗ Accept requests before initialization

✗ Ignore startup failures

✗ Expose secrets in startup logs

---

# Postconditions

If successful

✓ Gateway ready

✓ Health endpoint reports UP

✓ Metrics available

✓ Requests accepted

If failed

✓ Gateway not started

✓ Failure logged

✓ No client traffic accepted

---

# Runtime Invariants

Gateway

Must

✓ Finish initialization before serving traffic

✓ Initialize components in deterministic order

✓ Fail fast on critical startup errors

---

# Related Documents

- GATEWAY.md
- CONFIGURATION.md
- REDIS.md
- OBSERVABILITY.md

---

# Definition of Done

- Startup sequence implemented
- Configuration loading implemented
- Redis initialization implemented
- Route loading implemented
- Health initialization implemented
- Readiness checks implemented
- Startup integration tests pass

---

# End of Diagram

# Sequence Diagram — Graceful Shutdown

Diagram ID

SEQ-015

Version

1.0

Status

Approved

Related API

Gateway API

Monitoring API

Related Engineering Contract

GATEWAY.md

OBSERVABILITY.md

REDIS.md

Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0010 — Stateless Gateway
- ADR-0015 — Observability Strategy

---

# Purpose

Describe how the Distributed API Gateway shuts down without interrupting requests that are already being processed.

Graceful Shutdown is responsible for

- Stopping acceptance of new requests
- Completing in-flight requests
- Releasing resources
- Publishing shutdown events
- Closing infrastructure connections

The objective is to prevent request loss and maintain system consistency.

---

# Participants

```
System

Gateway

Load Balancer

Request Processor

Redis

Observability

Health Service

Spring Boot
```

---

# Preconditions

- Gateway running
- Active requests may exist
- Redis connected
- Health endpoint reporting UP

---

# Main Success Flow

```text
System
      │
      │ Shutdown Signal (SIGTERM)
      ▼
Spring Boot
      │
      │ Begin Graceful Shutdown
      ▼
Gateway
      │
      │ Stop Accepting New Requests
      ▼
Health Service
      │
      │ Status = DOWN
      ▼
Load Balancer
      │
      │ Stop Routing New Traffic
      ▼
Gateway
      │
      │ Wait for Active Requests
      ▼
Request Processor
      │
      │ Finish Processing
      ▼
Gateway
      │
      │ Flush Metrics
      ▼
Observability
      │
      │ Publish Shutdown Event
      ▼
Redis
      │
      │ Close Connection Pool
      ▼
Spring Boot
      │
      │ Destroy Beans
      ▼
System
      │
      │ Process Exit
```

---

# Success Result

```
No New Requests

↓

Active Requests Completed

↓

Resources Released

↓

Gateway Stopped
```

---

# Shutdown Order

The shutdown sequence follows

```
Stop New Traffic

↓

Health = DOWN

↓

Drain Active Requests

↓

Flush Metrics

↓

Close Redis

↓

Destroy Beans

↓

Terminate Process
```

The order must remain deterministic.

---

# Alternative Flow A

## No Active Requests

```text
Gateway

↓

No Active Requests

↓

Immediately Release Resources

↓

Shutdown Complete
```

---

# Alternative Flow B

## Active Requests Complete Before Timeout

```text
Gateway

↓

Wait

↓

All Requests Finished

↓

Shutdown Continues
```

---

# Alternative Flow C

## Shutdown Timeout Reached

```text
Gateway

↓

Maximum Wait Time Reached

↓

Force Shutdown

↓

Remaining Requests Aborted
```

Timeout should be configurable.

---

# Failure Flow

## Redis Shutdown Failure

```text
Redis

↓

Connection Close Failure

↓

Log Error

↓

Continue Shutdown
```

Infrastructure cleanup failures must not block process termination.

---

# Request Draining Rules

During shutdown

Existing requests

✓ Continue normally

New requests

✗ Rejected

Load Balancer should redirect traffic to healthy Gateway instances.

---

# Health Check Behavior

Immediately after shutdown begins

Health endpoint reports

```json
{
  "status": "DOWN"
}
```

This allows orchestration platforms to remove the instance from service.

---

# Observability Responsibilities

Before process termination

Publish

✓ Shutdown Started

✓ Active Request Count

✓ Shutdown Duration

✓ Final Metrics

✓ Shutdown Completed

These events assist in production diagnostics.

---

# Performance Target

Shutdown Initiation

```
<1 second
```

Maximum Graceful Shutdown

```
30 seconds
```

(Configurable)

---

# Security Considerations

Must

✓ Complete authenticated requests safely

✓ Preserve logs

✓ Flush metrics before exit

✓ Close all external connections

Must Never

✗ Accept new traffic

✗ Drop requests unnecessarily

✗ Leave resources open

---

# Postconditions

If successful

✓ Gateway stopped

✓ No resource leaks

✓ Metrics published

✓ Redis disconnected

✓ Process exited cleanly

If forced

✓ Remaining requests terminated

✓ Shutdown reason logged

---

# Runtime Invariants

Graceful Shutdown

Must

✓ Stop accepting new requests immediately

✓ Allow existing requests to finish

✓ Close infrastructure resources safely

✓ Exit in a deterministic order

---

# Related Documents

- GATEWAY.md
- OBSERVABILITY.md
- REDIS.md
- 04_MONITORING_API.md

---

# Definition of Done

- Graceful shutdown implemented
- Request draining implemented
- Health status updated during shutdown
- Metrics flushed before exit
- Redis connections closed
- Shutdown timeout configurable
- Integration tests pass

---

# End of Diagram