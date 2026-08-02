11_REDIS_DESIGN/

README.md

00_REDIS_FUNDAMENTALS.md

01_REDIS_ARCHITECTURE.md

02_KEY_DESIGN.md

03_DATA_STRUCTURES.md

04_TTL_STRATEGY.md

05_LUA_SCRIPT_DESIGN.md

06_ATOMIC_OPERATIONS.md

07_CONCURRENCY_MODEL.md

08_MEMORY_OPTIMIZATION.md

09_EVICTION_STRATEGY.md

10_FAILURE_HANDLING.md

11_SCALING_STRATEGY.md

12_PERFORMANCE_GUIDELINES.md

13_SECURITY.md

14_REDIS_BEST_PRACTICES.md


# Redis Design

# Distributed API Gateway + Rate Limiter

Version: 1.0

---

# Purpose

This directory documents the complete Redis architecture used by the Distributed API Gateway.

Unlike implementation code, these documents define how Redis is used as a distributed state management system.

Every Redis interaction in the project must conform to these specifications.

Implementation follows these designs.

---

# Goals

Redis is used to

- Maintain distributed state
- Support horizontal scaling
- Execute atomic operations
- Store Rate Limiter state
- Provide low-latency access
- Eliminate local memory dependencies

---

# Design Philosophy

Redis is treated as

✓ Distributed State Store

✓ Atomic Execution Engine

✓ High-Speed Cache

✓ Synchronization Layer

It is **not** treated as a relational database.

---

# Responsibilities

Redis owns

- Rate Limiter State
- Request Counters
- Token Buckets
- Sliding Window Data
- Distributed Coordination
- Temporary Runtime State

Redis does not own

- Business Data
- User Accounts
- Gateway Configuration
- Long-Term Persistence

---

# Design Principles

Every Redis operation should be

✓ Atomic

✓ Deterministic

✓ Low Latency

✓ Horizontally Scalable

✓ Thread Safe

✓ Failure Aware

---

# Architecture Overview

```
Gateway Instance 1
        │
Gateway Instance 2
        │
Gateway Instance 3
        │
───────────────
      Redis
───────────────
Shared Distributed State
```

All Gateway instances communicate with the same Redis deployment.

---

# Repository Contents

00_REDIS_FUNDAMENTALS.md

Introduction to Redis and project usage.

---

01_REDIS_ARCHITECTURE.md

Overall Redis architecture and deployment model.

---

02_KEY_DESIGN.md

Redis key naming conventions and schema.

---

03_DATA_STRUCTURES.md

Redis data structures used throughout the project.

---

04_TTL_STRATEGY.md

Expiration strategy for every key.

---

05_LUA_SCRIPT_DESIGN.md

Lua scripting architecture and execution model.

---

06_ATOMIC_OPERATIONS.md

Atomic execution guarantees.

---

07_CONCURRENCY_MODEL.md

Concurrency and distributed consistency.

---

08_MEMORY_OPTIMIZATION.md

Memory usage strategy.

---

09_EVICTION_STRATEGY.md

Key eviction policies.

---

10_FAILURE_HANDLING.md

Redis failure scenarios.

---

11_SCALING_STRATEGY.md

Scaling Redis with multiple Gateway instances.

---

12_PERFORMANCE_GUIDELINES.md

Latency, throughput, optimization and benchmarking.

---

13_SECURITY.md

Redis security architecture.

---

14_REDIS_BEST_PRACTICES.md

Engineering guidelines and production recommendations.

---

# Relationship with Other Documents

Engineering Contracts

↓

Redis Design

↓

Implementation

↓

Testing

↓

Deployment

Redis Design defines every interaction with Redis before implementation begins.

---

# Success Criteria

This documentation is complete when

- Every Redis key is defined.
- Every data structure is documented.
- Every TTL policy is specified.
- Every Lua script is designed.
- Every failure scenario is covered.
- AI can implement Redis integration without making architectural decisions.

---

# End of README

# Redis Fundamentals

Version: 1.0

Status: Approved

---

# Purpose

This document introduces Redis and explains why it is a foundational technology in the Distributed API Gateway.

It answers

- What is Redis?
- Why do we need Redis?
- Why not use local memory?
- Why not use a database?
- Why is Redis perfect for Rate Limiting?
- How will our project use Redis?

This document intentionally focuses on engineering concepts rather than implementation.

---

# What is Redis?

Redis (Remote Dictionary Server) is an in-memory data store designed for extremely fast read and write operations.

Unlike relational databases, Redis stores data primarily in RAM instead of disk.

Because memory access is significantly faster than disk access, Redis can process hundreds of thousands to millions of operations per second.

---

# Why Redis Exists

Traditional databases are optimized for

✓ Durability

✓ Relationships

✓ Complex Queries

✓ Long-term Storage

They are **not** optimized for

- Microsecond latency
- Millions of updates per second
- Temporary state
- Real-time counters

Redis was designed to solve these problems.

---

# Where Redis Fits

```
                 PostgreSQL

                       │

      Permanent Business Data

                       │

──────────────────────────────────

                   Redis

                       │

 Temporary Runtime Data

 Fast Counters

 Cache

 Distributed State

 Atomic Operations
```

Redis complements databases.

It does not replace them.

---

# Why Our Project Needs Redis

Imagine our Gateway runs on only one machine.

```
Gateway

↓

HashMap

↓

Rate Limiter
```

Everything works.

Now imagine

```
Gateway 1

Gateway 2

Gateway 3
```

Each has

```
Own HashMap
```

Problem

```
Client

↓

Gateway 1

↓

Allowed

↓

Gateway 2

↓

Allowed

↓

Gateway 3

↓

Allowed
```

Every Gateway believes

it is processing

the first request.

Rate limiting completely breaks.

---

# Shared State

Instead

```
Gateway 1

Gateway 2

Gateway 3

↓

Redis

↓

Shared Counter
```

Now every Gateway observes

the exact same request count.

Redis becomes the single source of truth.

---

# Real World Analogy

Imagine

three ticket counters

selling tickets.

Each employee keeps

their own notebook.

Eventually

the same seat gets sold three times.

Now replace

three notebooks

with

one shared computer.

Every employee sees the same data.

Redis is that shared computer.

---

# Why Not Use HashMap?

HashMap works only

inside one JVM.

Problems

✗ Cannot be shared

✗ Lost after restart

✗ Doesn't support distributed systems

✗ Not thread-safe without synchronization

Perfect for

Learning

Bad for

Production Gateways.

---

# Why Not Use PostgreSQL?

PostgreSQL could store counters.

Problems

✗ Disk I/O

✗ Higher latency

✗ Lock contention

✗ Expensive updates

✗ Poor fit for millions of counter increments

Redis solves these with

memory

atomic commands

and optimized data structures.

---

# Why Redis is Ideal for Rate Limiting

Rate limiting requires

```
Read Counter

↓

Increment Counter

↓

Compare Limit

↓

Return Decision
```

All of this should happen

- atomically
- consistently
- quickly

Redis supports exactly this workflow.

---

# Redis in Our Project

Redis stores

✓ Token Bucket State

✓ Sliding Window Counters

✓ Sliding Window Logs

✓ Fixed Window Counters

✓ Leaky Bucket State

✓ Temporary Metrics

✓ Request Counters

It never stores

✗ User Accounts

✗ JWT Secrets

✗ Business Data

✗ Route Configuration

---

# Redis Responsibilities

Redis owns

✓ Shared State

✓ Atomic Execution

✓ Fast Counters

✓ TTL Management

✓ Distributed Coordination

Redis does not own

✗ Authentication

✗ Routing

✗ Authorization

✗ Business Logic

---

# Core Redis Features We Use

Version 1

✓ Strings

✓ Hashes

✓ Sorted Sets

✓ Lists

✓ Expiration (TTL)

✓ Lua Scripts

✓ Atomic Operations

Future

✓ Pub/Sub

✓ Streams

✓ Redis Cluster

---

# Why Lua Scripts?

A Rate Limiter needs to

```
Read Counter

↓

Calculate Decision

↓

Increment Counter

↓

Set TTL
```

If each step is executed separately,

another Gateway could modify the counter in between.

Lua executes everything

as

one atomic operation.

---

# Redis and Horizontal Scaling

Without Redis

```
Gateway A

↓

50 Requests
```

```
Gateway B

↓

50 Requests
```

Both believe

the client has sent

50 requests.

Actual

```
100 Requests
```

With Redis

```
Gateway A

↓

Redis

↑

Gateway B
```

Both update

one shared counter.

---

# Performance Characteristics

Typical Redis latency

```
< 1 millisecond
```

Memory access

is

100–1000×

faster

than disk access.

---

# Engineering Trade-offs

Advantages

✓ Extremely Fast

✓ Distributed

✓ Atomic

✓ Mature

✓ Simple

✓ Battle Tested

Limitations

✗ Memory Cost

✗ Temporary Storage

✗ Requires High Availability

✗ Network Dependency

---

# Production Usage

Redis powers systems at

- GitHub
- Stack Overflow
- Instagram
- Twitter (X)
- Discord
- Uber
- Netflix
- Pinterest

Typical use cases

- Session Storage
- Distributed Locks
- Leaderboards
- Caching
- Rate Limiting
- Job Queues
- Temporary State

---

# Relationship with Other Documents

This document introduces Redis.

Detailed design is documented in

- Redis Architecture
- Key Design
- Data Structures
- TTL Strategy
- Lua Script Design

---

# Success Criteria

The Redis Fundamentals documentation is complete when

- Redis is introduced from first principles.
- The need for Redis in this project is justified.
- Local memory limitations are explained.
- Database trade-offs are explained.
- Redis responsibilities are clearly defined.
- Readers understand why Redis is the foundation of the distributed Rate Limiter.

---

# End of Document

# Redis Architecture

Version: 1.0

Status: Approved

---

# Purpose

This document defines the complete Redis architecture for the Distributed API Gateway.

Unlike the Redis Fundamentals document, which explains *why* Redis is used, this document defines *how* Redis is deployed, how it interacts with the Gateway, and what architectural responsibilities it owns.

This document is the authoritative architectural reference for all Redis interactions.

---

# Goals

The Redis architecture should

✓ Support horizontal scaling

✓ Maintain shared distributed state

✓ Execute atomic operations

✓ Provide low latency

✓ Eliminate local state

✓ Remain production-ready

---

# High-Level Architecture

```
                    Client Requests
                           │
                           ▼
        ┌────────────────────────────────┐
        │        Load Balancer           │
        └────────────────────────────────┘
                 │       │       │
                 ▼       ▼       ▼
          ┌────────┐ ┌────────┐ ┌────────┐
          │Gateway1│ │Gateway2│ │Gateway3│
          └────────┘ └────────┘ └────────┘
                 │       │       │
                 └───────┼───────┘
                         │
                         ▼
               ┌────────────────┐
               │     Redis      │
               └────────────────┘
```

Redis is shared by every Gateway instance.

No Gateway owns local Rate Limiter state.

---

# Why Shared Redis?

Without Redis

```
Gateway A

↓

Local Counter

```

```
Gateway B

↓

Local Counter
```

Each Gateway observes different state.

Result

```
Incorrect Rate Limiting
```

With Redis

```
Gateway A

↓

Redis

↑

Gateway B

↑

Gateway C
```

Every Gateway observes

the exact same state.

---

# Architectural Responsibilities

Redis owns

✓ Shared Counters

✓ Distributed State

✓ Token Bucket Storage

✓ Sliding Window State

✓ TTL Management

✓ Lua Execution

✓ Atomic Updates

Redis never owns

✗ Authentication

✗ Business Logic

✗ Request Routing

✗ Configuration

✗ Persistent Business Data

---

# Deployment Model

Version 1

```
Gateway Cluster

↓

Single Redis Instance
```

This keeps implementation simple while supporting horizontal Gateway scaling.

Future

```
Gateway Cluster

↓

Redis Sentinel

↓

Redis Replicas
```

Eventually

```
Gateway Cluster

↓

Redis Cluster
```

---

# Communication Flow

```
Gateway

↓

Redis Client

↓

Redis

↓

Lua Script

↓

Gateway
```

The Gateway never manipulates Redis state directly.

Every modification passes through the Redis abstraction layer.

---

# Redis Access Layer

The application should access Redis through

```
Gateway

↓

Rate Limiter

↓

Redis Service

↓

Redis Client

↓

Redis
```

No module should bypass the Redis Service.

---

# Layered Architecture

```
Gateway Layer

↓

Rate Limiter

↓

Redis Service

↓

Lua Executor

↓

Redis
```

Responsibilities remain separated.

---

# Data Ownership

Redis stores

```
Temporary Runtime State
```

Examples

✓ Request Counters

✓ Token Counts

✓ Sliding Windows

✓ Metrics

✓ Temporary Keys

Redis never stores

✓ User Database

✓ Route Configuration

✓ JWT Secrets

---

# State Lifetime

Redis state is

```
Temporary

↓

Automatically Expired

↓

Recreated When Needed
```

No Redis key should exist forever unless explicitly documented.

---

# Failure Boundary

Redis is an infrastructure dependency.

```
Gateway

↓

Redis

↓

Unavailable
```

Gateway executes

configured failure policy

```
Fail Open

or

Fail Closed
```

This behavior is defined in

Failure Handling.

---

# Redis Connection Strategy

Each Gateway maintains

```
Redis Connection Pool

↓

Shared Connections

↓

Thread Safe Access
```

Connections should never be created per request.

---

# Scalability Model

Horizontal Scaling

```
Gateway 1

Gateway 2

Gateway 3

Gateway N

↓

Shared Redis
```

Adding Gateways requires

no Rate Limiter changes.

---

# Concurrency Model

Concurrent requests

```
Gateway A

↓

Redis

↑

Gateway B

↑

Gateway C
```

Redis serializes atomic operations.

Lua Scripts eliminate race conditions.

---

# Atomic Execution

Every complex Redis operation

```
Read

↓

Compute

↓

Update

↓

Return
```

must execute

inside one Lua script.

Partial updates are prohibited.

---

# Availability Model

Version 1

```
Single Redis Instance
```

Future

```
Redis Sentinel

↓

Automatic Failover
```

Enterprise

```
Redis Cluster

↓

Sharding

↓

Replication

↓

High Availability
```

---

# Performance Characteristics

Expected latency

```
Gateway → Redis

<2 ms
```

Lua execution

```
<1 ms
```

Total Rate Limiter decision

```
<2 ms
```

---

# Security Architecture

Redis should

✓ Run inside private network

✓ Require authentication

✓ Disable dangerous commands

✓ Restrict client access

Redis should never be publicly accessible.

---

# Observability

Monitor

✓ Latency

✓ Memory Usage

✓ Command Rate

✓ Connection Count

✓ Key Count

✓ Lua Execution Time

Metrics feed

Monitoring Dashboard.

---

# Engineering Principles

The Redis architecture follows

✓ Single Responsibility

✓ Shared State

✓ Stateless Gateways

✓ Horizontal Scalability

✓ Atomic Consistency

✓ Infrastructure Isolation

---

# Relationship with Other Documents

Redis Architecture

↓

Key Design

↓

Data Structures

↓

TTL Strategy

↓

Lua Scripts

↓

Implementation

---

# Success Criteria

The Redis Architecture documentation is complete when

- Redis responsibilities are clearly defined.
- Gateway interaction is standardized.
- Deployment model is documented.
- Failure boundaries are identified.
- Concurrency model is specified.
- Scalability strategy is documented.
- AI can implement the Redis integration without making architectural decisions.

---

# End of Document

# Redis Key Design

Version: 1.0

Status: Approved

---

# Purpose

This document defines the Redis key naming strategy used throughout the Distributed API Gateway.

Keys are one of the most important parts of Redis design.

A poor key structure leads to

- difficult debugging
- inconsistent naming
- operational complexity
- memory waste
- collisions
- poor maintainability

This document establishes one standardized naming convention for every Redis key in the project.

---

# Goals

The key design should

✓ Be human readable

✓ Be deterministic

✓ Be scalable

✓ Avoid collisions

✓ Support monitoring

✓ Support debugging

✓ Support future clustering

---

# Design Philosophy

Every Redis key should answer

- What data is this?
- Who owns this?
- Which client does it belong to?
- Which algorithm uses it?
- When should it expire?

A developer should understand a key without reading the implementation.

---

# General Format

Every key follows

```
<project>:<module>:<resource>:<identifier>
```

Example

```
gateway:ratelimit:user:123
```

Never use random or ambiguous names.

---

# Naming Rules

Keys

Must

✓ Use lowercase

✓ Use colon (`:`) as separator

✓ Be descriptive

✓ Be deterministic

Must Never

✗ Contain spaces

✗ Contain special characters

✗ Depend on object hash codes

✗ Depend on JVM memory addresses

---

# Prefix Strategy

Every key begins with

```
gateway
```

This isolates our application from other Redis consumers.

Example

```
gateway:...
```

instead of

```
counter
```

---

# Module Prefixes

| Module | Prefix |
|----------|---------|
| Rate Limiter | ratelimit |
| Monitoring | metrics |
| Dashboard | dashboard |
| Gateway | gateway |
| Health | health |
| Redis Internal | redis |

---

# Identifier Strategy

Identifiers should uniquely identify the subject being limited.

Possible identifiers

```
User ID

API Key

Client ID

IP Address

JWT Subject
```

Example

```
gateway:ratelimit:user:123

gateway:ratelimit:apikey:abc123

gateway:ratelimit:ip:192.168.1.10
```

---

# Token Bucket Keys

Format

```
gateway:ratelimit:tokenbucket:<clientId>
```

Example

```
gateway:ratelimit:tokenbucket:user-101
```

Stores

- Current Tokens
- Capacity
- Last Refill Timestamp

---

# Fixed Window Keys

Format

```
gateway:ratelimit:fixed:<clientId>:<window>
```

Example

```
gateway:ratelimit:fixed:user-101:1722587600
```

Stores

Current window request count.

---

# Sliding Window Counter Keys

Current Window

```
gateway:ratelimit:sliding:current:<clientId>
```

Previous Window

```
gateway:ratelimit:sliding:previous:<clientId>
```

Example

```
gateway:ratelimit:sliding:current:user-101

gateway:ratelimit:sliding:previous:user-101
```

---

# Sliding Window Log Keys

Format

```
gateway:ratelimit:log:<clientId>
```

Example

```
gateway:ratelimit:log:user-101
```

Stores

Sorted Set

containing

timestamps of requests.

---

# Leaky Bucket Keys

Format

```
gateway:ratelimit:leaky:<clientId>
```

Stores

- Queue State
- Last Leak Timestamp

---

# Metrics Keys

Request Counter

```
gateway:metrics:requests
```

Blocked Requests

```
gateway:metrics:blocked
```

Successful Requests

```
gateway:metrics:success
```

Average Latency

```
gateway:metrics:latency
```

---

# Health Keys

Format

```
gateway:health:redis

gateway:health:gateway
```

Future

```
gateway:health:node:<instanceId>
```

---

# Temporary Keys

Temporary keys should clearly indicate their purpose.

Example

```
gateway:temp:request:<uuid>
```

Every temporary key

must have

TTL.

---

# Reserved Prefixes

Reserved

```
gateway

metrics

health

temp

dashboard

ratelimit
```

Application modules must not invent new prefixes without updating this document.

---

# Cluster Compatibility

Keys should support future Redis Cluster.

Related data should remain hash-slot friendly.

Future

```
gateway:ratelimit:{user-101}:tokenbucket
```

Hash tags may be introduced for co-location.

---

# Key Length Guidelines

Target

```
20–80 characters
```

Avoid

```
Very Short

↓

Unreadable
```

Avoid

```
Extremely Long

↓

Memory Waste
```

---

# Collision Prevention

Keys are unique because they include

```
Project

↓

Module

↓

Algorithm

↓

Identifier
```

Collisions should be impossible.

---

# Debugging Benefits

Good keys allow

```
SCAN gateway:ratelimit:*
```

or

```
SCAN gateway:metrics:*
```

Operations teams can immediately understand stored data.

---

# Security Considerations

Keys

Must

✓ Avoid secrets

✓ Avoid passwords

✓ Avoid JWT values

✓ Avoid personal information where possible

Good

```
user-101
```

Bad

```
john@example.com
```

---

# Future Evolution

Future prefixes

```
gateway:cache

gateway:session

gateway:locks

gateway:events
```

should follow the same convention.

---

# Relationship with Other Documents

Key Design

↓

Data Structures

↓

TTL Strategy

↓

Lua Script Design

↓

Implementation

---

# Success Criteria

The Redis Key Design is complete when

- Every key follows one naming convention.
- Every module has a documented prefix.
- Every algorithm has a deterministic key format.
- Future Redis Cluster compatibility is considered.
- Keys remain readable, scalable, and collision-free.
- AI can generate Redis keys without inventing names.

---

# End of Document

# Redis Data Structures

Version: 1.0

Status: Approved

---

# Purpose

This document defines the Redis data structures used throughout the Distributed API Gateway.

Choosing the correct Redis data structure is one of the most important engineering decisions in the project.

A poor choice leads to

- unnecessary memory consumption
- slower operations
- complex implementations
- scalability issues

Every Redis data structure selected in this project is intentionally chosen based on the required access pattern.

---

# Goals

The data structure strategy should

✓ Minimize memory usage

✓ Maximize performance

✓ Support atomic operations

✓ Match algorithm requirements

✓ Scale horizontally

✓ Remain easy to debug

---

# Design Philosophy

Never choose a Redis data structure because it is available.

Choose it because it matches

- access pattern
- update frequency
- lookup complexity
- memory characteristics
- algorithm requirements

The data model follows the algorithm.

The algorithm never follows the data model.

---

# Redis Data Structures Used

Version 1 uses

| Data Structure | Purpose |
|---------------|---------|
| String | Simple counters |
| Hash | Token Bucket state |
| Sorted Set | Sliding Window Log |
| List | Leaky Bucket queue |
| Set | Future active client tracking |

Future

- Streams
- Bitmaps
- HyperLogLog

---

# Strings

Purpose

Store single numeric values.

Examples

```
Request Counter

Blocked Requests

Active Requests
```

Example

```
gateway:metrics:requests

↓

125634
```

Operations

```
GET

SET

INCR

DECR

EXPIRE
```

Time Complexity

```
O(1)
```

---

# Why Strings?

Strings are

✓ Fast

✓ Memory Efficient

✓ Atomic

Perfect for counters.

---

# Hashes

Purpose

Store multiple related fields inside one Redis object.

Used for

```
Token Bucket
```

Example

```
gateway:ratelimit:tokenbucket:user-101
```

Structure

```
tokens

capacity

lastRefill
```

Stored as

```
Hash

↓

Field

↓

Value
```

Operations

```
HGET

HSET

HMGET

HMSET
```

Time Complexity

```
O(1)
```

---

# Why Hashes?

Instead of

```
3 Redis Keys
```

we use

```
1 Hash
```

Benefits

✓ Lower memory

✓ Easier management

✓ Faster lookup

---

# Sorted Sets

Purpose

Maintain ordered request timestamps.

Used by

```
Sliding Window Log
```

Example

```
gateway:ratelimit:log:user-101
```

Members

```
Request Timestamp
```

Score

```
Unix Timestamp
```

Operations

```
ZADD

ZRANGEBYSCORE

ZREMRANGEBYSCORE

ZCARD
```

Time Complexity

```
O(log n)
```

---

# Why Sorted Sets?

Sliding Window Log requires

```
Insert Timestamp

↓

Remove Expired

↓

Count Active Requests
```

Sorted Sets support all three efficiently.

---

# Lists

Purpose

Maintain ordered queue.

Used by

```
Leaky Bucket
```

Example

```
gateway:ratelimit:leaky:user-101
```

Operations

```
LPUSH

RPUSH

LPOP

RPOP

LLEN
```

---

# Why Lists?

Leaky Bucket behaves like

```
Queue

↓

FIFO
```

Redis Lists naturally support FIFO operations.

---

# Sets

Purpose

Maintain unique values.

Version 1

Minimal usage.

Future

```
Connected Clients

Gateway Nodes

Feature Flags
```

Operations

```
SADD

SREM

SISMEMBER
```

---

# Streams

Version 1

Not Used

Future

Used for

```
Audit Events

Metrics Pipeline

Event Streaming
```

---

# HyperLogLog

Version 1

Not Used

Future

Estimate

```
Unique Visitors
```

with minimal memory.

---

# Bitmaps

Version 1

Not Used

Future

Track

```
Feature Usage

Daily Activity
```

---

# Data Structure Selection Matrix

| Requirement | Structure |
|------------|-----------|
| Counter | String |
| Object | Hash |
| Ordered Time Series | Sorted Set |
| Queue | List |
| Unique Collection | Set |

---

# Access Patterns

Request Counter

```
Read

↓

Increment

↓

Expire
```

↓

String

---

Token Bucket

```
Read Tokens

↓

Update Tokens

↓

Update Timestamp
```

↓

Hash

---

Sliding Window

```
Insert Timestamp

↓

Delete Old Entries

↓

Count Entries
```

↓

Sorted Set

---

Leaky Bucket

```
Enqueue

↓

Dequeue

↓

Queue Length
```

↓

List

---

# Memory Considerations

Preferred order

```
String

↓

Hash

↓

Set

↓

List

↓

Sorted Set
```

Choose the smallest structure that satisfies the algorithm.

---

# Serialization Rules

Version 1

Redis stores

✓ Integers

✓ Strings

✓ Hash Fields

Avoid storing

✗ Java Objects

✗ Serialized Entities

✗ JSON Blobs (unless explicitly required)

Redis should store runtime state, not application objects.

---

# Performance Characteristics

| Structure | Read | Write |
|-----------|------|-------|
| String | O(1) | O(1) |
| Hash | O(1) | O(1) |
| List | O(1) | O(1) |
| Set | O(1) | O(1) |
| Sorted Set | O(log n) | O(log n) |

---

# Engineering Guidelines

Choose

Strings

for counters.

Choose

Hashes

for state objects.

Choose

Sorted Sets

only when ordering is required.

Avoid

using Sorted Sets when Strings are sufficient.

---

# Relationship with Other Documents

Data Structures

↓

Key Design

↓

TTL Strategy

↓

Lua Script Design

↓

Atomic Operations

↓

Implementation

---

# Success Criteria

The Redis Data Structures documentation is complete when

- Every Redis data structure has a defined purpose.
- Every Rate Limiting algorithm maps to an appropriate structure.
- Performance characteristics are documented.
- Memory implications are understood.
- Future extensibility is considered.
- AI can implement Redis storage without selecting data structures on its own.

---

# End of Document

# Redis TTL Strategy

Version: 1.0

Status: Approved

---

# Purpose

This document defines the Time-To-Live (TTL) strategy for every Redis key used by the Distributed API Gateway.

TTL is a critical aspect of Redis design.

Without a well-defined expiration strategy, Redis can accumulate stale state, consume excessive memory, and eventually degrade system performance.

Every Redis key must have a documented lifecycle.

---

# Goals

The TTL strategy should

✓ Automatically clean temporary state

✓ Prevent memory leaks

✓ Minimize manual cleanup

✓ Support Rate Limiting algorithms

✓ Improve operational reliability

✓ Keep Redis memory bounded

---

# Design Philosophy

Redis stores

```
Runtime State
```

Runtime state is temporary.

Temporary data should disappear automatically once it is no longer useful.

The application should never rely on manual cleanup jobs unless absolutely necessary.

---

# Why TTL Exists

Without TTL

```
Gateway

↓

Redis

↓

Millions of Expired Counters

↓

Increasing Memory

↓

Performance Degradation
```

With TTL

```
Gateway

↓

Redis

↓

Automatic Expiration

↓

Stable Memory Usage
```

---

# General Rules

Every Redis key

Must

✓ Have a documented TTL

✓ Expire automatically when appropriate

✓ Match the business lifecycle

Must Never

✗ Live forever unintentionally

✗ Depend on scheduled cleanup

✗ Require administrator intervention

---

# TTL Categories

The project uses three categories.

| Category | Description |
|----------|-------------|
| Short-lived | Seconds to Minutes |
| Medium-lived | Minutes to Hours |
| Persistent | Explicitly managed |

---

# Short-Lived Keys

Examples

- Fixed Window Counters
- Sliding Window Logs
- Temporary Request State

Typical TTL

```
30 Seconds

↓

5 Minutes
```

These keys naturally expire after the Rate Limiting window.

---

# Medium-Lived Keys

Examples

- Token Bucket State
- Leaky Bucket State

Typical TTL

```
30 Minutes

↓

24 Hours
```

Inactive clients are automatically removed.

---

# Persistent Keys

Version 1

Very few keys are persistent.

Examples

```
Gateway Metrics

Configuration Cache (Future)
```

Persistent keys must be explicitly documented.

---

# Fixed Window TTL

Window

```
1 Minute
```

TTL

```
Window Duration

+

Safety Buffer
```

Example

```
60 Seconds

+

10 Seconds

=

70 Seconds
```

The safety buffer prevents premature expiration caused by clock differences or processing delays.

---

# Sliding Window Counter TTL

Current Window

```
Window Duration

+

Safety Buffer
```

Previous Window

```
Current Window TTL

+

One Additional Window
```

This ensures both windows remain available during overlap calculations.

---

# Sliding Window Log TTL

Each Sorted Set

expires after

```
Window Duration

+

Safety Buffer
```

Old timestamps are removed continuously.

The key itself expires once no active requests remain.

---

# Token Bucket TTL

Bucket state should remain available while the client is active.

Default

```
1 Hour
```

Every successful request refreshes the TTL.

Inactive buckets disappear automatically.

---

# Leaky Bucket TTL

Queue state remains valid while requests continue arriving.

Default

```
1 Hour
```

Each enqueue operation refreshes the expiration.

---

# Metrics TTL

Version 1

Global metrics

```
Persistent
```

Future

Time-series metrics may use rolling expiration.

---

# Health Keys

Health information should always represent current system status.

Default

```
30 Seconds
```

Health values are refreshed periodically.

---

# TTL Refresh Policy

Some keys

should refresh their TTL whenever they are updated.

Examples

✓ Token Bucket

✓ Leaky Bucket

Some keys

should never refresh.

Examples

✓ Fixed Window Counter

The TTL policy depends on the algorithm.

---

# Lua Script Responsibilities

Whenever applicable,

the Lua script must

✓ Update state

✓ Refresh TTL

✓ Return decision

All within the same atomic execution.

TTL updates must never require a second Redis command.

---

# Safety Buffer

Every expiration includes

a small buffer.

Reason

```
Clock Drift

↓

Network Delay

↓

Concurrent Requests
```

The buffer prevents valid state from disappearing too early.

Typical buffer

```
5–10 Seconds
```

---

# Memory Lifecycle

```
Request

↓

Redis Key Created

↓

TTL Assigned

↓

Requests Continue

↓

TTL Refreshed (if applicable)

↓

Client Becomes Inactive

↓

Key Expires Automatically
```

No manual cleanup is required.

---

# Expiration Matrix

| Key Type | Default TTL | Refresh on Access |
|----------|-------------|-------------------|
| Fixed Window | Window + Buffer | No |
| Sliding Window Counter | Window + Buffer | No |
| Sliding Window Log | Window + Buffer | No |
| Token Bucket | 1 Hour | Yes |
| Leaky Bucket | 1 Hour | Yes |
| Health | 30 Seconds | Yes |
| Metrics | Persistent | N/A |

---

# Failure Considerations

If a key expires

before it should,

the algorithm behaves as though

a new client has arrived.

This is acceptable only if the TTL policy is correctly chosen.

Incorrect TTL values can weaken Rate Limiting guarantees.

---

# Performance Considerations

Using TTL

✓ Eliminates cleanup jobs

✓ Reduces memory growth

✓ Simplifies operations

✓ Keeps Redis responsive

Expiration should rely on Redis's built-in mechanisms rather than application logic.

---

# Security Considerations

TTL helps reduce

✓ Stale runtime state

✓ Long-lived temporary identifiers

✓ Residual client information

Sensitive runtime state should never outlive its useful lifetime.

---

# Relationship with Other Documents

TTL Strategy

↓

Key Design

↓

Data Structures

↓

Lua Script Design

↓

Memory Optimization

↓

Implementation

---

# Success Criteria

The Redis TTL Strategy is complete when

- Every Redis key has a documented expiration policy.
- TTL values align with algorithm requirements.
- Automatic cleanup replaces manual cleanup.
- Lua scripts refresh TTL where required.
- Safety buffers are consistently applied.
- AI can implement expiration logic without making architectural decisions.

---

# End of Document

# Redis Lua Script Design

Version: 1.0

Status: Approved

---

# Purpose

This document defines the Lua scripting architecture used by the Distributed API Gateway.

Lua scripts are the foundation of our distributed Rate Limiter.

They guarantee

- Atomic execution
- Consistent state updates
- Race-condition prevention
- Single network round trip
- Deterministic behavior

Every Redis operation that requires multiple read/write steps must execute through a Lua script.

---

# Goals

The Lua scripting strategy should

✓ Execute atomically

✓ Eliminate race conditions

✓ Minimize Redis round trips

✓ Support horizontal scaling

✓ Remain deterministic

✓ Keep business logic outside Redis

---

# Why Lua Scripts?

Consider a Token Bucket implementation without Lua.

```
Gateway

↓

GET Tokens

↓

Calculate New Tokens

↓

SET Tokens

↓

SET TTL
```

Imagine two Gateway instances executing simultaneously.

```
Gateway A

↓

GET = 5
```

```
Gateway B

↓

GET = 5
```

Both believe

there are

```
5 Tokens
```

Both consume

```
1 Token
```

Result

```
Actual Tokens

↓

3

Stored Tokens

↓

4
```

One request is effectively free.

This is a race condition.

---

# Atomic Execution

With Lua

```
Read State

↓

Calculate

↓

Update State

↓

Refresh TTL

↓

Return Decision
```

Redis guarantees

the entire script executes

without interruption.

No other client can observe intermediate state.

---

# Why Not Redis Transactions?

Redis supports

```
MULTI

EXEC
```

However

transactions

do not allow

complex decision-making

between commands.

Lua allows

```
Read

↓

Calculate

↓

Branch

↓

Update

↓

Return
```

inside one atomic execution.

---

# Design Philosophy

Lua scripts

Own

✓ Atomic state transitions

✓ Data mutations

✓ TTL updates

✓ Algorithm calculations

Lua scripts

Do Not Own

✗ Business rules

✗ Authentication

✗ Route resolution

✗ HTTP responses

✗ Logging

The Gateway decides

what to execute.

Lua decides

how Redis state changes.

---

# Execution Flow

```
Gateway

↓

Rate Limiter

↓

Strategy

↓

Redis Service

↓

Lua Executor

↓

Redis

↓

Lua Script

↓

Result
```

Every algorithm follows this pipeline.

---

# Supported Algorithms

Version 1

✓ Token Bucket

✓ Fixed Window

✓ Sliding Window Counter

✓ Sliding Window Log

✓ Leaky Bucket

Each algorithm has

its own Lua script.

---

# Script Responsibilities

Every Lua script should

✓ Read required keys

✓ Validate state

✓ Perform calculations

✓ Update Redis

✓ Refresh TTL

✓ Return structured result

Nothing more.

---

# Script Inputs

Every script receives

```
KEYS

ARGV
```

KEYS

contain

```
Redis Keys
```

ARGV

contains

```
Current Timestamp

Window Size

Capacity

Refill Rate

Limit

TTL

Client Identifier
```

Scripts must never depend on JVM memory.

---

# Script Outputs

Every script returns

```
Allowed

Remaining Capacity

Retry After

Current State
```

Example

```json
{
  "allowed": true,
  "remainingTokens": 42,
  "retryAfter": 0
}
```

Every algorithm should follow a consistent response structure.

---

# Single Responsibility

One Lua script

implements

one algorithm.

Example

```
token_bucket.lua
```

should never implement

Sliding Window logic.

---

# Script Lifecycle

```
Application Startup

↓

Load Lua Scripts

↓

Redis SHA Generated

↓

Cache SHA

↓

Future Calls

↓

EVALSHA
```

Scripts are loaded once during application startup.

---

# EVALSHA Strategy

Never execute

```
EVAL
```

for every request.

Instead

```
Startup

↓

SCRIPT LOAD

↓

SHA Cached

↓

EVALSHA
```

Benefits

✓ Faster execution

✓ Reduced network traffic

✓ Better Redis performance

---

# Script Cache Miss

If Redis returns

```
NOSCRIPT
```

Gateway should

```
Reload Script

↓

Update SHA

↓

Retry Execution
```

This recovery must be automatic.

---

# TTL Handling

TTL updates belong inside

the Lua script.

Example

```
Update Counter

↓

Refresh TTL

↓

Return Decision
```

Never refresh TTL

using a second Redis command.

---

# Error Handling

If Lua execution fails

```
Lua Error

↓

Redis Service

↓

Gateway Failure Policy

↓

Fail Open

or

Fail Closed
```

The failure policy is configurable.

---

# Performance Characteristics

Target

Lua Execution

```
<1 ms
```

Redis Round Trips

```
Exactly One
```

Memory Allocation

```
Minimal
```

Scripts should avoid unnecessary temporary objects.

---

# Security Considerations

Lua scripts

Must

✓ Validate inputs

✓ Avoid dynamic code generation

✓ Use only provided keys

Must Never

✗ Access unauthorized keys

✗ Execute arbitrary commands

✗ Depend on external state

---

# Engineering Guidelines

Every Lua script should

✓ Be deterministic

✓ Be idempotent where applicable

✓ Avoid unnecessary branching

✓ Keep execution short

✓ Return structured responses

Complex business logic belongs in Java,

not in Redis.

---

# Relationship with Other Documents

Lua Script Design

↓

Atomic Operations

↓

Concurrency Model

↓

Failure Handling

↓

Implementation

---

# Success Criteria

The Lua Script Design is complete when

- Every Rate Limiting algorithm has an independent Lua script.
- Scripts execute atomically.
- EVALSHA is used instead of repeated EVAL.
- TTL updates occur inside scripts.
- Script inputs and outputs are standardized.
- AI can implement Lua integration without making architectural decisions.

---

# End of Document

# Atomic Operations

Version: 1.0

Status: Approved

---

# Purpose

This document defines the atomic execution guarantees required by the Distributed API Gateway.

Atomicity is one of the most critical requirements of the Rate Limiter.

Without atomic operations, concurrent Gateway instances can corrupt shared Redis state, resulting in

- Incorrect request counts
- Broken rate limits
- Race conditions
- Inconsistent behavior

Every Redis state transition that involves multiple steps must be atomic.

---

# Goals

The atomic execution strategy should

✓ Prevent race conditions

✓ Guarantee consistency

✓ Support horizontal scaling

✓ Eliminate partial updates

✓ Produce deterministic results

✓ Remain independent of Gateway instance count

---

# What is Atomicity?

An atomic operation is an operation that executes as one indivisible unit.

Either

```
Everything Happens
```

or

```
Nothing Happens
```

No intermediate state should ever be visible.

---

# Real World Analogy

Imagine withdrawing money from an ATM.

```
Check Balance

↓

Deduct Amount

↓

Dispense Cash
```

If electricity fails after

```
Deduct Amount
```

but before

```
Dispense Cash
```

the customer loses money.

The operation must be atomic.

Either

✓ Balance deducted AND cash dispensed

or

✓ Nothing happens.

Redis Lua scripts follow the same principle.

---

# Why Atomicity Matters

Consider

```
100 Tokens
```

Two Gateway instances receive requests simultaneously.

Gateway A

```
Read

↓

100
```

Gateway B

```
Read

↓

100
```

Both consume

```
1 Token
```

Expected

```
98 Tokens
```

Actual

```
99 Tokens
```

One request bypassed the Rate Limiter.

This is a race condition.

---

# Atomic Execution Model

Every Redis update follows

```
Read State

↓

Calculate Decision

↓

Update State

↓

Refresh TTL

↓

Return Result
```

This entire workflow executes as one atomic operation.

---

# Operations Requiring Atomicity

Version 1

✓ Token Bucket refill

✓ Token consumption

✓ Fixed Window increment

✓ Sliding Window update

✓ Sliding Window cleanup

✓ Leaky Bucket update

✓ TTL refresh

---

# Operations Not Requiring Atomicity

Simple operations

```
GET

EXISTS

TTL
```

may execute independently.

Complex read-modify-write operations must never be split.

---

# Atomic Boundary

The atomic boundary begins

```
Read Redis State
```

and ends

```
Return Updated Result
```

Nothing inside this boundary may execute outside the Lua script.

---

# Why Lua Instead of Application Locks?

Option 1

```
Java synchronized
```

Problems

✗ JVM-local only

✗ Does not work across multiple Gateway instances

✗ Breaks horizontal scaling

---

Option 2

```
Distributed Lock
```

Problems

✗ Additional complexity

✗ More Redis operations

✗ Higher latency

---

Option 3

```
Redis Lua Script
```

Benefits

✓ Atomic

✓ Single round trip

✓ Distributed

✓ Fast

Chosen architecture.

---

# Atomic Operations per Algorithm

## Token Bucket

Atomic steps

```
Read Bucket

↓

Calculate Refill

↓

Consume Token

↓

Update Bucket

↓

Refresh TTL

↓

Return Decision
```

---

## Fixed Window

Atomic steps

```
Read Counter

↓

Increment

↓

Check Limit

↓

Refresh TTL

↓

Return Decision
```

---

## Sliding Window Counter

Atomic steps

```
Read Current Window

↓

Read Previous Window

↓

Calculate Effective Count

↓

Increment Current Window

↓

Refresh TTL

↓

Return Decision
```

---

## Sliding Window Log

Atomic steps

```
Remove Expired Entries

↓

Insert Timestamp

↓

Count Requests

↓

Refresh TTL

↓

Return Decision
```

---

## Leaky Bucket

Atomic steps

```
Leak Requests

↓

Insert New Request

↓

Update Queue

↓

Refresh TTL

↓

Return Decision
```

---

# Partial Updates

Partial updates are prohibited.

Bad

```
Update Counter

↓

Network Failure

↓

TTL Not Updated
```

Good

```
Counter Updated

↓

TTL Updated

↓

Success Returned
```

Everything happens together.

---

# Concurrency Example

Three Gateways

```
Gateway A

Gateway B

Gateway C
```

simultaneously execute

```
Consume Token
```

Redis executes

```
Lua Script A

↓

Lua Script B

↓

Lua Script C
```

Each script completes before the next begins.

State remains consistent.

---

# Failure Behavior

If Lua execution fails

```
No State Updated
```

No partial modifications remain.

Gateway executes

configured failure policy

```
Fail Open

or

Fail Closed
```

---

# Performance Characteristics

Atomic execution

```
Exactly One Redis Round Trip
```

Expected latency

```
<2 ms
```

Lua execution

```
<1 ms
```

Atomicity must never significantly increase request latency.

---

# Design Principles

Atomic operations should

✓ Be deterministic

✓ Be isolated

✓ Minimize execution time

✓ Avoid unnecessary Redis commands

✓ Update TTL within the same execution

---

# Engineering Guidelines

Never

```
GET

↓

Business Logic

↓

SET
```

outside a Lua script.

Never

split one state transition into multiple Redis requests.

Every read-modify-write sequence belongs inside a single atomic execution.

---

# Relationship with Other Documents

Atomic Operations

↓

Concurrency Model

↓

Lua Script Design

↓

Failure Handling

↓

Implementation

---

# Success Criteria

The Atomic Operations documentation is complete when

- Every multi-step Redis operation executes atomically.
- Lua scripts define the atomic boundary.
- Partial updates are impossible.
- Concurrency across multiple Gateway instances is safe.
- AI can implement Redis operations without introducing race conditions.

---

# End of Document

# Concurrency Model

Version: 1.0

Status: Approved

---

# Purpose

This document defines how the Distributed API Gateway handles concurrent requests while maintaining consistent Rate Limiting behavior across multiple Gateway instances.

Concurrency is one of the primary reasons this project exists.

Without a well-defined concurrency model, the Gateway cannot guarantee correct behavior under production traffic.

This document establishes the rules that ensure every request is processed safely, consistently, and deterministically.

---

# Goals

The concurrency model should

✓ Support horizontal scaling

✓ Prevent race conditions

✓ Eliminate duplicate state

✓ Maintain distributed consistency

✓ Remain lock-free at the application level

✓ Scale with increasing traffic

---

# What is Concurrency?

Concurrency occurs when multiple requests are processed during overlapping periods of time.

Example

```
Client A

↓

Gateway 1

↓

Redis
```

simultaneously with

```
Client B

↓

Gateway 2

↓

Redis
```

Both requests may access the same Redis key.

The system must behave as if each request executed correctly.

---

# Real World Analogy

Imagine three cashiers updating the inventory of the same product.

Without coordination

```
Cashier A

↓

Reads Stock = 10
```

```
Cashier B

↓

Reads Stock = 10
```

Both sell one item.

Expected

```
Stock = 8
```

Actual

```
Stock = 9
```

One sale disappeared.

Redis + Lua Scripts act like a centralized inventory system that processes one update at a time.

---

# Concurrency Sources

Concurrent requests may originate from

✓ Multiple users

✓ Multiple browser tabs

✓ Multiple mobile devices

✓ Multiple Gateway instances

✓ Automated systems

The architecture must handle all of them.

---

# Distributed Concurrency

Version 1 assumes

```
Gateway 1

Gateway 2

Gateway 3

↓

Shared Redis
```

Every Gateway processes requests independently.

Redis provides the shared synchronization point.

---

# Design Philosophy

Concurrency should be solved

at the data layer,

not at the application layer.

Gateway instances remain

✓ Stateless

✓ Independent

✓ Horizontally Scalable

State synchronization belongs to Redis.

---

# Application-Level Locks

Version 1

Does Not Use

```
synchronized

ReentrantLock

ReadWriteLock
```

Reason

Application locks work only inside one JVM.

They cannot coordinate multiple Gateway instances.

---

# Distributed Locks

Version 1

Does Not Use

Redis Distributed Locks.

Reason

Rate Limiting requires

state updates,

not resource ownership.

Lua scripts already provide the required atomicity.

Distributed locks would increase

- latency
- complexity
- operational overhead

without additional benefit.

---

# Concurrency Control Strategy

Every concurrent update follows

```
Gateway

↓

Redis Lua Script

↓

Atomic State Update

↓

Return Result
```

The application never performs

```
Read

↓

Modify

↓

Write
```

outside Redis.

---

# Concurrent Token Bucket Example

Three requests arrive simultaneously.

```
Gateway A

↓

Consume Token
```

```
Gateway B

↓

Consume Token
```

```
Gateway C

↓

Consume Token
```

Redis executes

```
Lua Script A

↓

Lua Script B

↓

Lua Script C
```

Each request observes the latest state.

No tokens are lost.

---

# Concurrent Sliding Window Example

Requests

```
A

B

C
```

arrive within the same millisecond.

Each request

```
Insert Timestamp

↓

Remove Expired Entries

↓

Count Requests

↓

Return Decision
```

Because the entire operation is atomic,

every request sees a consistent Sliding Window.

---

# Thread Safety

Gateway components

should be

✓ Stateless

✓ Immutable where possible

✓ Thread-safe

Services must never store request-specific mutable state.

---

# Shared Mutable State

Version 1

Shared mutable state exists only in Redis.

The JVM should not maintain

global counters,

global maps,

or shared Rate Limiting state.

---

# Race Conditions

Possible race conditions

✓ Simultaneous token consumption

✓ Concurrent counter increment

✓ Window rollover

✓ TTL refresh

All are resolved by atomic Lua execution.

---

# Deadlocks

Version 1 architecture

cannot produce deadlocks because

✓ No JVM locks

✓ No nested locking

✓ No distributed locking

The design intentionally avoids lock-based coordination.

---

# Consistency Model

Redis provides

```
Single Source of Truth
```

Every Gateway reads and writes the same state.

Consistency is stronger than using independent local memory.

---

# Failure During Concurrent Execution

If Redis fails

during an operation

```
Lua Execution

↓

Failure

↓

No Partial Update

↓

Gateway Failure Policy
```

Atomicity ensures incomplete state never becomes visible.

---

# Scalability Characteristics

Increasing Gateway instances

```
1

↓

5

↓

20

↓

100
```

does not require changes

to the concurrency model.

Every Gateway follows the same interaction pattern.

---

# Performance Considerations

Concurrency control should introduce

```
Zero JVM Locks

↓

One Redis Round Trip

↓

One Lua Execution
```

Target latency

```
<2 ms
```

for the complete Rate Limiting decision.

---

# Engineering Principles

The concurrency model follows

✓ Stateless Services

✓ Shared Distributed State

✓ Atomic Updates

✓ Lock-Free Application Design

✓ Deterministic Execution

---

# Future Evolution

Future versions may introduce

✓ Redis Cluster

✓ Multi-region Gateways

✓ Distributed Metrics

✓ Cross-region Rate Limiting

The concurrency model should remain unchanged.

---

# Relationship with Other Documents

Concurrency Model

↓

Memory Optimization

↓

Failure Handling

↓

Scaling Strategy

↓

Implementation

---

# Success Criteria

The Concurrency Model documentation is complete when

- Application-level locking is eliminated.
- Redis is established as the synchronization point.
- Concurrent request behavior is deterministic.
- Race conditions are prevented through atomic operations.
- The architecture supports horizontal scaling without concurrency changes.
- AI can implement concurrent request handling without introducing synchronization bugs.

---

# End of Document

# Memory Optimization

Version: 1.0

Status: Approved

---

# Purpose

This document defines the memory optimization strategy for Redis used by the Distributed API Gateway.

Although Redis is extremely fast because it stores data in memory (RAM), memory is a finite and expensive resource.

Every Redis key, data structure, and algorithm should be designed to minimize memory consumption without sacrificing correctness or performance.

---

# Goals

The memory strategy should

✓ Minimize RAM usage

✓ Prevent memory leaks

✓ Remove stale data automatically

✓ Support millions of clients

✓ Scale predictably

✓ Keep Redis responsive

---

# Design Philosophy

Memory is treated as a production resource.

Every byte stored in Redis should have a purpose.

If a piece of data is

- not actively used
- easily recomputable
- expired

it should not remain in memory.

---

# Memory Ownership

Redis stores only

✓ Runtime State

✓ Temporary Counters

✓ Active Rate Limiter Data

✓ Recent Metrics

✓ Short-lived Coordination State

Redis must never become a permanent datastore.

---

# Memory Lifecycle

Every Redis object follows

```
Created

↓

Updated

↓

Read

↓

Expires

↓

Removed
```

No runtime object should exist indefinitely unless explicitly documented.

---

# Object Size Principles

Choose

the smallest possible representation.

Good

```
Counter

↓

String
```

Instead of

```
JSON Object
```

---

Good

```
Hash

↓

3 Fields
```

Instead of

```
3 Separate Keys
```

---

# Avoid Redundant Keys

Bad

```
gateway:user:101:tokens

gateway:user:101:capacity

gateway:user:101:lastRefill
```

Good

```
gateway:ratelimit:tokenbucket:user-101

↓

Hash

↓

tokens

capacity

lastRefill
```

Benefits

✓ Fewer Keys

✓ Lower Memory

✓ Easier Management

---

# Data Structure Selection

Preferred order

```
String

↓

Hash

↓

Set

↓

List

↓

Sorted Set
```

Always choose the smallest structure that satisfies the required access pattern.

---

# Hash Optimization

Whenever related values belong together

store them inside one Hash.

Example

```
Token Bucket

↓

Hash
```

instead of

```
Multiple Strings
```

This reduces Redis object overhead.

---

# Sliding Window Log Optimization

Sliding Window Log

uses

```
Sorted Set
```

Because timestamps accumulate quickly,

expired entries should be removed

during every request.

The Sorted Set should never grow without bound.

---

# Automatic Cleanup

Redis should remove

✓ Expired Counters

✓ Old Sliding Window Entries

✓ Inactive Token Buckets

✓ Idle Leaky Buckets

Cleanup should rely on

TTL

not scheduled jobs.

---

# TTL and Memory

Every temporary key

must define

```
TTL
```

Benefits

✓ Automatic Cleanup

✓ Stable Memory Usage

✓ No Background Maintenance

---

# Duplicate Data

Never store

the same information

under multiple keys.

Bad

```
Counter A

Counter B

Counter C

↓

Same Value
```

Good

```
Single Source of Truth
```

---

# Serialization Strategy

Redis stores

✓ Integers

✓ Strings

✓ Hash Fields

Avoid

✗ Java Serialization

✗ Binary Objects

✗ Large JSON Documents

unless explicitly required.

Simple values consume less memory and are easier to inspect.

---

# Metrics Storage

Global metrics

should remain aggregated.

Example

Good

```
Total Requests

↓

125634
```

Avoid

storing every historical request in Redis.

Long-term analytics belong in specialized storage systems.

---

# Large Collections

Lists and Sorted Sets

must remain bounded.

Example

Sliding Window Log

```
Remove Expired Entries

↓

Insert Current Request
```

The number of stored timestamps should never grow indefinitely.

---

# Key Length Optimization

Keys should be descriptive

but not excessively long.

Preferred

```
20–80 Characters
```

Avoid

```
Very Short

↓

Unreadable
```

Avoid

```
Extremely Long

↓

Memory Waste
```

---

# Memory Growth Strategy

As traffic increases

```
More Clients

↓

More Keys

↓

TTL Removes Idle Clients

↓

Stable Memory Growth
```

Memory usage should grow

with active traffic,

not historical traffic.

---

# Memory Monitoring

The Monitoring module should track

✓ Total Redis Memory

✓ Used Memory

✓ Peak Memory

✓ Key Count

✓ Expired Keys

✓ Evicted Keys

These metrics should be visible in the Dashboard.

---

# Memory Pressure

If Redis approaches

configured memory limits

the system should

✓ Alert Operators

✓ Continue Expiring Idle Keys

✓ Avoid creating unnecessary objects

Eviction policies are documented separately.

---

# Engineering Guidelines

Always

✓ Prefer Hashes over multiple related keys

✓ Prefer Strings for counters

✓ Apply TTL

✓ Remove obsolete entries

✓ Minimize object count

Never

✗ Store business entities

✗ Store duplicate state

✗ Store permanent history

✗ Ignore memory growth

---

# Future Optimizations

Future versions may introduce

✓ Redis Memory Compression

✓ Redis Cluster Sharding

✓ Tiered Storage

✓ Memory Profiling

✓ Automatic Capacity Planning

The Version 1 design should remain compatible with these improvements.

---

# Relationship with Other Documents

Memory Optimization

↓

Eviction Strategy

↓

Performance Guidelines

↓

Scaling Strategy

↓

Implementation

---

# Success Criteria

The Memory Optimization documentation is complete when

- Every Redis object has a justified memory footprint.
- Redundant keys are eliminated.
- TTL removes stale runtime state.
- Appropriate data structures minimize overhead.
- Memory usage scales with active traffic.
- AI can implement Redis storage without introducing unnecessary memory consumption.

---

# End of Document

# Redis Eviction Strategy

Version: 1.0

Status: Approved

---

# Purpose

This document defines how Redis manages memory when the configured memory limit is reached.

Although the Distributed API Gateway is designed to automatically remove stale data using TTL, Redis may eventually exhaust available memory under sustained high traffic.

This document specifies

- which eviction policy Redis should use
- why it was selected
- how it impacts the Gateway
- operational guidelines for production deployments

---

# Goals

The eviction strategy should

✓ Prevent Redis crashes

✓ Preserve active Rate Limiter state

✓ Remove stale data first

✓ Maintain predictable behavior

✓ Minimize operational intervention

✓ Support production workloads

---

# What is Eviction?

Eviction is the process of automatically removing keys from Redis when memory usage reaches the configured maximum.

Without an eviction policy

```
Redis

↓

Memory Full

↓

Write Operation

↓

OOM Error

↓

Gateway Failure
```

With eviction

```
Redis

↓

Memory Full

↓

Remove Eligible Keys

↓

Continue Operating
```

---

# Why Eviction Matters

Redis stores data in RAM.

RAM is finite.

As traffic increases

```
More Clients

↓

More Redis Keys

↓

Higher Memory Usage
```

Eventually

memory limits may be reached.

Redis must decide

which keys should be removed.

---

# Memory Limit

Every Redis deployment

must configure

```
maxmemory
```

Example

```
512 MB

1 GB

2 GB

4 GB
```

Memory limits should be based on expected production traffic.

---

# Recommended Policy

Version 1

uses

```
volatile-ttl
```

Reason

Only keys with

TTL

are eligible for eviction.

Since almost every Rate Limiter key has a TTL,

Redis naturally removes

the least useful runtime state.

---

# Why volatile-ttl?

Priority

```
Keys Near Expiration

↓

Removed First
```

Advantages

✓ Temporary runtime state removed

✓ Persistent metrics preserved

✓ Predictable behavior

✓ Matches project architecture

---

# Alternative Policies

## noeviction

Behavior

```
Memory Full

↓

Reject Writes
```

Advantages

✓ Never removes data

Problems

✗ Gateway failures

✗ Rate Limiter failures

Not suitable.

---

## allkeys-lru

Behavior

```
Least Recently Used

↓

Evicted
```

Advantages

✓ Good cache policy

Problems

May evict active Rate Limiter state.

Rejected.

---

## allkeys-lfu

Behavior

```
Least Frequently Used

↓

Evicted
```

Advantages

Useful for caches.

Problems

Active clients may still be removed unexpectedly.

Rejected.

---

## volatile-lru

Behavior

Only keys with TTL

participate.

Recently used keys survive longer.

Acceptable alternative,

but

Version 1 prefers

```
volatile-ttl
```

because expiration time directly matches our runtime lifecycle.

---

# Eligible Keys

Typical evictable keys

✓ Token Buckets

✓ Sliding Window Logs

✓ Fixed Window Counters

✓ Leaky Buckets

✓ Temporary Runtime Keys

Persistent operational data should remain unaffected.

---

# Non-Evictable Keys

Examples

✓ Persistent Metrics

✓ Future Configuration Cache

✓ System Metadata

These should either

avoid TTL

or use a different storage strategy.

---

# Relationship with TTL

TTL remains

the primary cleanup mechanism.

Eviction is

a last-resort safety mechanism.

Normal lifecycle

```
Key Created

↓

TTL Expires

↓

Redis Deletes Key
```

Eviction occurs only when

memory pressure exists.

---

# High Memory Scenario

```
Traffic Spike

↓

Millions of Active Clients

↓

Memory Limit Reached

↓

Redis Starts Evicting

↓

Gateway Continues Operating
```

This is preferable to rejecting writes.

---

# Monitoring Requirements

Monitor

✓ Used Memory

✓ Peak Memory

✓ Evicted Keys

✓ Expired Keys

✓ Memory Fragmentation

✓ Maxmemory Usage

These metrics should appear on the Dashboard.

---

# Alert Thresholds

Recommended

```
70%

↓

Warning
```

```
85%

↓

High Memory Alert
```

```
95%

↓

Critical Alert
```

Operators should investigate before eviction becomes frequent.

---

# Operational Guidelines

Memory pressure should first be addressed by

✓ Increasing Redis memory

✓ Reducing unnecessary key lifetime

✓ Optimizing data structures

Eviction should not be relied upon as routine cleanup.

---

# Engineering Trade-offs

Advantages

✓ Prevents OOM failures

✓ Keeps Gateway operational

✓ Automatic

✓ Matches temporary runtime state

Limitations

✗ Some inactive Rate Limiter state may disappear early

✗ Requires careful monitoring

These trade-offs are acceptable because Rate Limiter state is reconstructible.

---

# Future Evolution

Future deployments may adopt

```
Redis Cluster

↓

Per-node Memory Limits

↓

Independent Eviction
```

The eviction strategy should remain consistent across nodes.

---

# Security Considerations

Eviction

must never

remove

✓ Secrets

✓ Configuration

✓ Persistent business data

Only temporary runtime state should be eligible.

---

# Relationship with Other Documents

Eviction Strategy

↓

Failure Handling

↓

Scaling Strategy

↓

Performance Guidelines

↓

Implementation

---

# Success Criteria

The Redis Eviction Strategy is complete when

- Redis memory limits are defined.
- The selected eviction policy is justified.
- Evictable and non-evictable keys are documented.
- Monitoring and alerting requirements are specified.
- AI can configure Redis eviction without making architectural decisions.

---

# End of Document

# Redis Failure Handling

Version: 1.0

Status: Approved

---

# Purpose

This document defines how the Distributed API Gateway behaves when Redis becomes unavailable or behaves unexpectedly.

Redis is a critical infrastructure dependency for the distributed Rate Limiter.

However,

the Gateway should fail in a predictable, configurable, and observable manner instead of crashing or behaving inconsistently.

This document specifies

- Failure scenarios
- Failure policies
- Recovery strategies
- Operational guidelines

---

# Goals

The failure handling strategy should

✓ Keep Gateway behavior predictable

✓ Prevent inconsistent Rate Limiting

✓ Avoid application crashes

✓ Support automatic recovery

✓ Preserve observability

✓ Support production deployments

---

# Design Philosophy

Infrastructure failures

must never

cause undefined application behavior.

Every Redis failure should result in

a documented,

predictable,

and testable outcome.

---

# Failure Categories

Redis failures are grouped into

| Category | Examples |
|----------|----------|
| Connectivity | Connection refused, timeout |
| Availability | Redis process down |
| Resource | Memory exhausted |
| Execution | Lua script failure |
| Network | Packet loss, latency spike |
| Configuration | Authentication failure |

Each category has an independent recovery strategy.

---

# Failure Boundary

Redis owns

```
Distributed Runtime State
```

Gateway owns

```
Failure Decision
```

Redis never decides

whether a request should continue.

The Gateway applies the configured failure policy.

---

# Primary Failure Policy

Version 1 supports two policies.

```
Fail Open
```

and

```
Fail Closed
```

The active policy is configurable.

---

# Fail Open

Behavior

```
Redis Failure

↓

Bypass Rate Limiter

↓

Forward Request
```

Advantages

✓ High Availability

✓ Better User Experience

Problems

✗ Temporary loss of Rate Limiting

Recommended for

```
Internal APIs

Development

Non-Critical Services
```

---

# Fail Closed

Behavior

```
Redis Failure

↓

Reject Request

↓

503 Service Unavailable
```

Advantages

✓ Strict Rate Limiting

✓ Better Abuse Protection

Problems

✗ Reduced Availability

Recommended for

```
Public APIs

Production

Security-Sensitive Systems
```

---

# Configuration

Example

```
gateway.rateLimiter.failurePolicy

=

FAIL_OPEN
```

or

```
FAIL_CLOSED
```

This should be configurable

without code changes.

---

# Failure Scenario A

## Redis Connection Refused

```
Gateway

↓

Redis Client

↓

Connection Refused

↓

Failure Policy

↓

Gateway Response
```

No retry occurs during request processing.

---

# Failure Scenario B

## Redis Timeout

```
Gateway

↓

Redis

↓

Timeout

↓

Failure Policy
```

Timeout duration

must be configurable.

Long Redis waits should never block request threads.

---

# Failure Scenario C

## Authentication Failure

```
Gateway

↓

Redis

↓

AUTH Failed

↓

Startup Failure
```

This is considered

a fatal configuration error.

The Gateway should not start.

---

# Failure Scenario D

## Lua Script Failure

```
Gateway

↓

Redis

↓

Lua Runtime Error

↓

Failure Policy
```

No partial state should remain.

---

# Failure Scenario E

## Redis Memory Exhausted

```
Redis

↓

OOM

↓

Write Failure

↓

Failure Policy
```

Operations teams should receive alerts immediately.

---

# Automatic Recovery

When Redis becomes available again

```
Gateway

↓

Connection Restored

↓

Resume Normal Processing
```

No application restart should be required.

---

# Retry Strategy

Version 1

Request Processing

```
No Retry
```

Reason

Retrying inside

request processing

increases latency

and may duplicate operations.

Connection pools may retry

outside request execution.

---

# Health Integration

Redis failures should immediately affect

```
Health Endpoint
```

Example

```json
{
  "status": "DEGRADED",
  "redis": "DOWN"
}
```

or

```json
{
  "status": "DOWN",
  "redis": "DOWN"
}
```

depending on deployment policy.

---

# Monitoring Requirements

Every Redis failure should publish

✓ Failure Count

✓ Failure Type

✓ Connection Status

✓ Retry Attempts

✓ Recovery Time

✓ Correlation ID

These metrics should appear on the Dashboard.

---

# Logging Requirements

Every failure should log

✓ Timestamp

✓ Failure Category

✓ Error Code

✓ Redis Host

✓ Correlation ID

Never log

✗ Redis Password

✗ Secrets

✗ Authentication Tokens

---

# Operational Guidelines

Operators should investigate

✓ Frequent connection failures

✓ High timeout rates

✓ Repeated Lua failures

✓ Memory exhaustion

✓ Authentication failures

Healthy Redis should require

minimal operational intervention.

---

# Performance Considerations

Failure detection

should complete within

```
Configured Timeout
```

Recovery should occur

automatically

without application restart.

---

# Engineering Principles

Failure handling should

✓ Be deterministic

✓ Be configurable

✓ Be observable

✓ Be testable

✓ Preserve application stability

The Gateway should never enter

an undefined state.

---

# Future Evolution

Future versions may introduce

✓ Redis Sentinel

✓ Automatic Failover

✓ Redis Cluster

✓ Multi-region Redis

The failure handling model should remain compatible with these enhancements.

---

# Relationship with Other Documents

Failure Handling

↓

Scaling Strategy

↓

Performance Guidelines

↓

Security

↓

Implementation

---

# Success Criteria

The Redis Failure Handling documentation is complete when

- Every Redis failure scenario has a documented response.
- Fail Open and Fail Closed policies are defined.
- Recovery behavior is specified.
- Monitoring and logging requirements are documented.
- Startup and runtime failures are distinguished.
- AI can implement Redis failure handling without making architectural decisions.

---

# End of Document

# Redis Scaling Strategy

Version: 1.0

Status: Approved

---

# Purpose

This document defines how the Redis layer scales as the Distributed API Gateway grows from a single Gateway instance to a production-grade distributed system.

The architecture should allow Gateway instances to scale horizontally without requiring changes to the Rate Limiting algorithms.

Redis should remain the single source of truth for distributed runtime state.

---

# Goals

The scaling strategy should

✓ Support horizontal Gateway scaling

✓ Maintain shared distributed state

✓ Preserve Rate Limiting correctness

✓ Support future Redis clustering

✓ Minimize architectural changes

✓ Remain production-ready

---

# Design Philosophy

Gateway instances should scale independently.

Redis should provide a shared, centralized runtime state.

Adding new Gateway instances must not require

- code changes
- Rate Limiter modifications
- algorithm redesign

Scaling should be operational, not architectural.

---

# Version 1 Architecture

```
                    Clients
                       │
                       ▼
              Load Balancer
                       │
        ┌────────┬────────┬────────┐
        ▼        ▼        ▼
    Gateway 1 Gateway 2 Gateway 3
        │        │        │
        └────────┴────────┘
                 │
                 ▼
             Redis Instance
```

All Gateway instances communicate with the same Redis server.

---

# Why This Works

Every Gateway

```
Stateless

↓

Independent

↓

Horizontally Scalable
```

Every Gateway reads and writes

the same Redis keys.

No synchronization occurs between Gateway instances.

Redis performs all coordination.

---

# Scaling the Gateway

Scaling the Gateway is straightforward.

```
Traffic Increases

↓

Start New Gateway

↓

Register with Load Balancer

↓

Gateway Connects to Redis

↓

Ready
```

No Rate Limiter configuration changes are required.

---

# Gateway Independence

Every Gateway owns

✓ Request Processing

✓ Authentication

✓ Authorization

✓ Routing

✓ HTTP Forwarding

Every Gateway shares

✓ Redis State

✓ Rate Limiter Counters

✓ Runtime Metrics

---

# Redis as the Scaling Layer

Redis provides

```
Shared State

↓

Atomic Operations

↓

Consistent Counters

↓

Distributed Synchronization
```

The Gateway remains unaware of

how many Gateway instances exist.

---

# Scaling Limits

Version 1

```
Gateway

↓

Unlimited (Practical Infrastructure Limits)
```

```
Redis

↓

Single Instance
```

Redis becomes the first scaling bottleneck.

This is acceptable for Version 1.

---

# Future Scaling

Stage 1

```
Single Redis
```

↓

Stage 2

```
Redis Replica
```

↓

Stage 3

```
Redis Sentinel
```

↓

Stage 4

```
Redis Cluster
```

The Gateway architecture remains unchanged throughout these upgrades.

---

# Redis Sentinel

Future deployments may introduce

```
Primary Redis

↓

Replica

↓

Replica

↓

Sentinel
```

Benefits

✓ Automatic Failover

✓ Higher Availability

✓ Minimal Gateway Changes

---

# Redis Cluster

Enterprise deployment

```
Gateway Cluster

↓

Redis Cluster

↓

Shard 1

Shard 2

Shard 3
```

Responsibilities

✓ Data Sharding

✓ Horizontal Scaling

✓ Fault Isolation

---

# Sharding Considerations

Redis Cluster distributes keys

using

```
Hash Slots
```

Key naming conventions defined in

```
02_KEY_DESIGN.md
```

are compatible with future sharding.

Future hash tags

```
gateway:ratelimit:{user-101}:tokenbucket
```

may improve key locality.

---

# Gateway Startup During Scaling

New Gateway

```
Start

↓

Connect Redis

↓

Load Configuration

↓

Register Health

↓

Ready
```

No synchronization with other Gateway instances is required.

---

# Load Balancer Responsibilities

The Load Balancer

owns

✓ Traffic Distribution

✓ Health-based Routing

✓ Instance Registration

✓ Instance Removal

It does not own

✗ Rate Limiting

✗ Authentication

✗ Redis Coordination

---

# Scaling Metrics

Monitor

✓ Gateway Count

✓ Redis CPU

✓ Redis Memory

✓ Redis Latency

✓ Connections

✓ Requests per Second

Scaling decisions should be driven by metrics,

not assumptions.

---

# Scaling Bottlenecks

Potential bottlenecks

Version 1

✓ Redis CPU

✓ Redis Memory

✓ Network Bandwidth

Future

✓ Cross-region Latency

✓ Cluster Rebalancing

These should be monitored continuously.

---

# Multi-Region Deployment

Version 1

Not Supported

Future

```
Region A

↓

Regional Redis
```

```
Region B

↓

Regional Redis
```

Cross-region Rate Limiting requires additional consistency mechanisms and is outside Version 1 scope.

---

# Performance Targets

Gateway Scaling

```
Linear
```

Redis Latency

```
<2 ms
```

Gateway Startup

```
<10 Seconds
```

Adding a new Gateway should not significantly increase request latency.

---

# Engineering Principles

The scaling strategy follows

✓ Stateless Gateways

✓ Shared Distributed State

✓ Independent Horizontal Scaling

✓ Infrastructure Evolution

✓ Minimal Architectural Change

---

# Relationship with Other Documents

Scaling Strategy

↓

Performance Guidelines

↓

Security

↓

Best Practices

↓

Implementation

---

# Success Criteria

The Redis Scaling Strategy is complete when

- Gateway horizontal scaling is documented.
- Redis responsibilities are clearly defined.
- Future Sentinel and Cluster evolution are planned.
- Load Balancer responsibilities are separated.
- Scaling bottlenecks are identified.
- AI can implement the Gateway without making scaling architecture decisions.

---

# End of Document

# Redis Performance Guidelines

Version: 1.0

Status: Approved

---

# Purpose

This document defines the performance objectives, optimization techniques, benchmarking strategy, and operational guidelines for Redis in the Distributed API Gateway.

The purpose is not merely to make Redis "fast", but to ensure that Redis remains predictable, scalable, and efficient under production workloads.

Performance is considered a design requirement rather than an afterthought.

---

# Goals

The Redis layer should

✓ Respond with consistently low latency

✓ Support high request throughput

✓ Scale with Gateway instances

✓ Minimize network overhead

✓ Maximize CPU efficiency

✓ Keep memory usage predictable

---

# Performance Philosophy

Performance optimization follows three principles

```
Correctness

↓

Consistency

↓

Speed
```

An incorrect but fast Rate Limiter is unacceptable.

Correctness always takes precedence over raw performance.

---

# Performance Targets

| Metric | Target |
|---------|---------|
| Redis Latency | < 2 ms |
| Lua Execution | < 1 ms |
| Rate Limiter Decision | < 2 ms |
| Gateway Processing Overhead | < 20 ms |
| Redis Connection Acquisition | < 1 ms |
| Redis Availability | > 99.9% |

---

# Request Flow

Every request should execute

```
Gateway

↓

Redis Connection

↓

Lua Script

↓

Redis Response

↓

Gateway Decision
```

Exactly

```
One

Redis Round Trip
```

is the target.

---

# Network Optimization

Every Redis interaction should

✓ Minimize round trips

✓ Use persistent TCP connections

✓ Reuse connection pool

✓ Execute Lua scripts

Avoid

```
GET

↓

SET

↓

EXPIRE
```

as separate network requests.

---

# Connection Pooling

Every Gateway maintains

```
Connection Pool

↓

Shared Connections

↓

Thread-safe Access
```

Never

```
Open

↓

Execute

↓

Close
```

a Redis connection for every request.

---

# Command Optimization

Prefer

```
Single Lua Script
```

instead of

```
Multiple Redis Commands
```

Benefits

✓ Lower latency

✓ Fewer network packets

✓ Atomic execution

---

# Data Structure Optimization

Choose the smallest appropriate data structure.

Preferred

```
Counter

↓

String
```

instead of

```
Hash

↓

Single Field
```

Use

```
Hash
```

only when multiple related fields exist.

---

# Key Lookup Optimization

Key lookup should be

```
O(1)
```

Avoid

```
SCAN
```

during request processing.

SCAN is acceptable only for

✓ Operations

✓ Maintenance

✓ Debugging

---

# Lua Performance

Lua scripts should

✓ Execute quickly

✓ Avoid loops over large datasets

✓ Minimize temporary variables

✓ Return compact responses

Target

```
<1 ms
```

execution time.

---

# Sliding Window Optimization

Sliding Window Log

must remove expired entries

during every request.

The Sorted Set should never grow indefinitely.

Expected complexity

```
O(log n)
```

---

# Memory Optimization

Performance depends heavily on memory usage.

Avoid

✓ Duplicate Keys

✓ Large Objects

✓ Serialized Java Objects

Prefer

✓ Hashes

✓ Strings

✓ Short-lived Keys

---

# TTL Optimization

TTL should

✓ Remove inactive clients

✓ Prevent stale data

✓ Reduce memory pressure

TTL updates should occur

inside Lua scripts,

not as separate commands.

---

# Redis CPU Optimization

Monitor

✓ CPU Usage

✓ Command Rate

✓ Lua Execution Time

High CPU usage may indicate

✓ Poor key design

✓ Inefficient Lua scripts

✓ Excessive Sorted Set growth

---

# Redis Memory Optimization

Monitor

✓ Used Memory

✓ Peak Memory

✓ Fragmentation Ratio

✓ Evicted Keys

✓ Expired Keys

Memory growth should correlate with

active clients,

not historical traffic.

---

# Benchmark Strategy

Benchmark

✓ Token Bucket

✓ Fixed Window

✓ Sliding Window Counter

✓ Sliding Window Log

✓ Leaky Bucket

Each algorithm should be tested independently.

---

# Load Testing

Test scenarios

```
100 Requests/sec

↓

1,000 Requests/sec

↓

10,000 Requests/sec

↓

100,000 Requests/sec
```

Observe

✓ Latency

✓ Throughput

✓ Memory

✓ CPU

---

# Stress Testing

Continue increasing load until

```
Performance Degrades
```

Measure

✓ Maximum Throughput

✓ Failure Rate

✓ Recovery Time

The breaking point should be documented.

---

# Latency Monitoring

Track

✓ Average Latency

✓ P95

✓ P99

✓ Maximum

Average latency alone is insufficient.

---

# Bottleneck Identification

Potential bottlenecks

✓ Redis CPU

✓ Redis Memory

✓ Network

✓ Lua Execution

✓ Connection Pool

Optimization should target the actual bottleneck,

not assumptions.

---

# Dashboard Metrics

Expose

✓ Redis Latency

✓ Commands/sec

✓ Requests/sec

✓ Lua Execution Time

✓ Memory Usage

✓ CPU Usage

✓ Connection Count

---

# Performance Anti-Patterns

Never

✗ Create Redis connections per request

✗ Execute multiple commands when Lua is sufficient

✗ Store unnecessary objects

✗ Ignore TTL

✗ Scan Redis during request processing

✗ Serialize large Java objects

---

# Engineering Guidelines

Always

✓ Batch logic inside Lua

✓ Use connection pooling

✓ Apply TTL

✓ Monitor latency

✓ Benchmark regularly

Performance improvements should never compromise correctness.

---

# Future Optimizations

Future versions may introduce

✓ Redis Cluster

✓ Read Replicas

✓ Pipelining

✓ Connection Multiplexing

✓ Client-side Caching

These optimizations should not require changes to Rate Limiting algorithms.

---

# Relationship with Other Documents

Performance Guidelines

↓

Security

↓

Best Practices

↓

Implementation

↓

Benchmarking

---

# Success Criteria

The Redis Performance Guidelines are complete when

- Performance targets are documented.
- Connection pooling is standardized.
- Lua optimization rules are defined.
- Benchmarking methodology is documented.
- Monitoring metrics are identified.
- AI can implement Redis with production-grade performance considerations.

---

# End of Document

# Redis Security

Version: 1.0

Status: Approved

---

# Purpose

This document defines the security architecture and operational security guidelines for Redis used by the Distributed API Gateway.

Redis stores the distributed runtime state that powers Rate Limiting.

Although Redis does **not** store business data or user credentials, compromising Redis can still allow attackers to

- bypass Rate Limiting
- corrupt runtime state
- deny service
- disrupt Gateway operation

Redis must therefore be treated as a critical infrastructure component.

---

# Goals

The Redis security model should

✓ Protect runtime state

✓ Prevent unauthorized access

✓ Secure network communication

✓ Protect credentials

✓ Minimize attack surface

✓ Support production deployments

---

# Security Philosophy

Redis should never rely on

```
Trust

↓

Internal Network
```

Instead

Every connection

Every client

Every deployment

must be explicitly secured.

---

# Security Objectives

Protect

✓ Runtime State

✓ Rate Limiter State

✓ Redis Credentials

✓ Lua Scripts

✓ Connection Integrity

Prevent

✗ Unauthorized Reads

✗ Unauthorized Writes

✗ Configuration Changes

✗ Command Injection

✗ Denial of Service

---

# Threat Model

Potential threats

✓ Unauthorized Redis access

✓ Credential leakage

✓ Command injection

✓ Data tampering

✓ Memory exhaustion attacks

✓ Network sniffing

✓ Infrastructure compromise

Redis security should mitigate each of these risks.

---

# Network Isolation

Redis should never be exposed

directly

to the public Internet.

Recommended deployment

```
Internet

↓

Load Balancer

↓

Gateway

↓

Private Network

↓

Redis
```

Only Gateway instances may communicate with Redis.

---

# Firewall Rules

Allow

✓ Gateway Nodes

✓ Operations Infrastructure

✓ Monitoring Systems

Block

✗ Public Internet

✗ Unknown Clients

✗ Direct External Access

Redis should listen only on trusted interfaces.

---

# Authentication

Redis must require

authentication.

Example

```
Gateway

↓

Authenticate

↓

Redis

↓

Access Granted
```

Unauthenticated Redis deployments are prohibited.

---

# Credential Storage

Redis passwords

Must

✓ Be stored in environment variables

✓ Be injected at deployment

✓ Be excluded from source code

Must Never

✗ Appear in Git

✗ Appear in Dockerfiles

✗ Appear in documentation

✗ Be hardcoded

---

# TLS

Development

```
Optional
```

Production

```
Required
```

Traffic between

Gateway

↓

Redis

should be encrypted.

---

# Principle of Least Privilege

Gateway instances should receive

only the permissions required

to

✓ Read Keys

✓ Write Keys

✓ Execute Lua Scripts

Administrative commands should not be available to application clients.

---

# Dangerous Commands

Production Redis should disable

```
FLUSHALL

FLUSHDB

CONFIG

DEBUG

SHUTDOWN

MONITOR
```

Application code should never execute these commands.

---

# Lua Script Security

Lua scripts

Must

✓ Use only supplied KEYS

✓ Validate ARGV

✓ Return deterministic results

Must Never

✗ Execute arbitrary commands

✗ Depend on external state

✗ Access undocumented keys

---

# Key Design Security

Redis keys

Must Never contain

✓ Passwords

✓ JWT Tokens

✓ Secrets

✓ Personal Information

Good

```
gateway:ratelimit:user:101
```

Bad

```
gateway:john@example.com
```

---

# Runtime State Protection

Redis stores

temporary runtime state.

Even temporary state

should not be modifiable

by unauthorized clients.

Redis integrity directly affects

Rate Limiting correctness.

---

# Connection Security

Gateway should use

```
Connection Pool

↓

Authenticated Connections

↓

Persistent Sessions
```

Connections should never expose

credentials in logs.

---

# Logging

Log

✓ Authentication Failures

✓ Connection Failures

✓ Unauthorized Access Attempts

✓ Lua Errors

Never Log

✗ Redis Password

✗ Secrets

✗ TLS Keys

✗ Sensitive Configuration

---

# Monitoring

Monitor

✓ Failed Authentication

✓ Unauthorized Commands

✓ Connection Count

✓ Memory Usage

✓ CPU Usage

✓ Latency

✓ Command Rate

Security monitoring should integrate with

the Observability module.

---

# Backup Security

If Redis persistence is enabled

Backups

Must

✓ Be encrypted

✓ Be access controlled

✓ Be stored securely

Temporary runtime state generally does not require long-term archival.

---

# Denial of Service Protection

Protect Redis from

✓ Excessive Connections

✓ Connection Flooding

✓ Large Payloads

✓ Malicious Lua Scripts

Gateway-level Rate Limiting provides

the first layer of defense.

---

# Deployment Guidelines

Development

✓ Local Redis

✓ Simple Authentication

Production

✓ Private Network

✓ Authentication Enabled

✓ TLS Enabled

✓ Firewall Configured

✓ Monitoring Enabled

---

# Incident Response

If Redis credentials are compromised

Immediately

```
Rotate Credentials

↓

Restart Gateway Connections

↓

Audit Access Logs

↓

Verify Runtime State
```

Credential rotation should require

minimal downtime.

---

# Future Enhancements

Future versions may introduce

✓ Redis ACLs

✓ Mutual TLS

✓ Secret Management (Vault)

✓ Certificate Rotation

✓ Managed Redis Services

The Version 1 architecture should remain compatible with these improvements.

---

# Engineering Principles

Redis security follows

✓ Defense in Depth

✓ Least Privilege

✓ Secure by Default

✓ Private Infrastructure

✓ Credential Isolation

---

# Relationship with Other Documents

Security

↓

Best Practices

↓

Deployment

↓

Implementation

---

# Success Criteria

The Redis Security documentation is complete when

- Redis authentication is mandatory.
- Public access is prohibited.
- Credential management is documented.
- TLS requirements are defined.
- Dangerous commands are disabled.
- Monitoring and incident response are specified.
- AI can deploy Redis securely without making security decisions.

---

# End of Document

# Redis Best Practices

Version: 1.0

Status: Approved

---

# Purpose

This document defines the engineering best practices that every developer must follow while integrating Redis into the Distributed API Gateway.

Unlike previous documents that define the architecture, this document defines the day-to-day engineering rules.

These rules ensure that every Redis interaction remains

- consistent
- maintainable
- scalable
- production-ready

regardless of who implements the feature.

---

# Goals

The Redis engineering practices should

✓ Keep Redis usage consistent

✓ Prevent architectural drift

✓ Improve maintainability

✓ Improve debugging

✓ Support future scaling

✓ Encourage production-grade engineering

---

# Philosophy

Redis is not

```
A Global HashMap
```

Redis is

```
A Distributed Infrastructure Component
```

Every Redis interaction should be designed with the same discipline as a database interaction.

---

# Design Principles

Every Redis operation should be

✓ Atomic

✓ Deterministic

✓ Stateless

✓ Observable

✓ Recoverable

✓ Efficient

---

# Key Naming

Always

Use

```
project:module:resource:identifier
```

Example

```
gateway:ratelimit:tokenbucket:user-101
```

Never

```
counter1

bucket

temp123
```

---

# One Responsibility Per Key

Every Redis key should own

one concept.

Good

```
Token Bucket
```

↓

One Hash

Bad

```
One Key

↓

Stores

Token Bucket

Metrics

Configuration
```

---

# Prefer Hashes

When storing multiple related fields

Prefer

```
Hash
```

instead of

multiple independent keys.

Benefits

✓ Lower Memory

✓ Easier Maintenance

✓ Better Readability

---

# Prefer Strings

Use Strings only for

✓ Counters

✓ Small Numeric Values

Do not use Strings

to serialize entire Java objects.

---

# Always Use TTL

Every temporary key

must define

```
TTL
```

If a key should never expire,

that decision must be explicitly documented.

Never rely on

manual cleanup.

---

# One Redis Round Trip

Every Rate Limiting decision

should perform

```
Exactly One

Redis Round Trip
```

If multiple commands are required,

use

Lua Scripts.

---

# Avoid Read-Modify-Write

Never

```
GET

↓

Business Logic

↓

SET
```

outside Redis.

Instead

```
Lua Script

↓

Atomic Execution
```

---

# Keep Lua Small

Lua scripts should

✓ Solve one problem

✓ Execute quickly

✓ Avoid unnecessary branching

✓ Return structured results

Never move

business logic

into Lua.

---

# Connection Management

Always

Use

```
Connection Pool
```

Never

```
Create

↓

Execute

↓

Close

Connection

Per Request
```

---

# Avoid Large Objects

Redis should store

runtime state

not

business objects.

Avoid

✗ Serialized Entities

✗ Large JSON Documents

✗ Binary Objects

---

# Minimize Memory

Prefer

✓ Hashes

✓ Short Keys

✓ Small Values

✓ TTL

Avoid

✓ Duplicate State

✓ Permanent Runtime Data

✓ Large Collections

---

# Logging

Log

✓ Redis Failures

✓ Connection Failures

✓ Latency

✓ Lua Errors

Never Log

✗ Passwords

✗ Secrets

✗ Authentication Tokens

---

# Monitoring

Continuously monitor

✓ Latency

✓ Memory

✓ CPU

✓ Connection Count

✓ Evicted Keys

✓ Expired Keys

✓ Lua Execution Time

If Redis is not monitored,

it is not production ready.

---

# Error Handling

Every Redis failure

must trigger

documented failure policy.

Never

ignore exceptions.

Never

retry blindly.

---

# Benchmarking

Every new Redis feature

should be benchmarked.

Measure

✓ Latency

✓ Throughput

✓ Memory

✓ CPU

Optimization without measurement

is guesswork.

---

# Thread Safety

Never

store mutable shared state

inside the JVM.

Redis

is the only shared runtime state.

Gateway services

should remain

stateless.

---

# Security

Always

✓ Authenticate Redis

✓ Restrict Network Access

✓ Protect Credentials

✓ Use TLS in Production

Never expose Redis

directly

to the Internet.

---

# Documentation

Every Redis feature

must document

✓ Keys

✓ TTL

✓ Data Structure

✓ Lua Script

✓ Failure Behavior

✓ Performance Impact

No Redis feature should exist

without documentation.

---

# Testing

Every Redis integration

requires

✓ Unit Tests

✓ Integration Tests

✓ Concurrency Tests

✓ Failure Tests

✓ Performance Tests

Testing should include

multiple Gateway instances.

---

# Anti-Patterns

Never

✗ Use Redis as a relational database

✗ Store permanent business data

✗ Use SCAN in request processing

✗ Ignore TTL

✗ Create duplicate keys

✗ Execute multiple commands when Lua is sufficient

✗ Hardcode Redis credentials

✗ Skip monitoring

---

# Production Readiness Checklist

Before deploying Redis

Verify

- [ ] Authentication Enabled
- [ ] Private Network
- [ ] TLS Configured
- [ ] Connection Pool Configured
- [ ] Lua Scripts Loaded
- [ ] TTL Defined
- [ ] Monitoring Enabled
- [ ] Alerts Configured
- [ ] Backup Strategy Documented
- [ ] Failure Policy Configured

---

# Engineering Principles

Redis usage follows

✓ Simplicity

✓ Determinism

✓ Atomicity

✓ Scalability

✓ Observability

✓ Security

Every Redis interaction should be explainable,

testable,

and production-ready.

---

# Relationship with Other Documents

Redis Best Practices

↓

Implementation

↓

Testing

↓

Deployment

These practices apply across every Redis-related implementation in the project.

---

# Success Criteria

The Redis Best Practices documentation is complete when

- Engineering conventions are standardized.
- Anti-patterns are documented.
- Operational practices are defined.
- Production readiness checklist is available.
- Developers can implement Redis consistently without inventing new conventions.
- AI can generate Redis code that follows the project's engineering standards.

---

# End of Document