# ADR README

# Architecture Decision Records (ADR)

Version: 1.0

---

# Purpose

Architecture Decision Records (ADRs) document every significant engineering decision made throughout the development of the Distributed API Gateway.

Each ADR explains:

- Why the decision was necessary
- What alternatives were considered
- Why the chosen solution was selected
- The trade-offs involved
- Long-term consequences
- Future evolution

The purpose of an ADR is not to justify a decision after implementation.

Its purpose is to preserve engineering knowledge.

---

# Why ADRs?

Software evolves.

Months later developers often remember **what** was built but forget **why**.

ADRs preserve the reasoning behind important decisions.

Benefits include:

- Easier onboarding
- Better code reviews
- Historical context
- Easier refactoring
- Better interview preparation
- Reduced architectural drift

---

# ADR Lifecycle

Every ADR follows the lifecycle below.

```
Problem

↓

Investigation

↓

Alternatives

↓

Decision

↓

Implementation

↓

Review

↓

Accepted

↓

Referenced by Code
```

---

# ADR Status

Every ADR must have one of the following statuses.

| Status | Meaning |
|----------|---------|
| Proposed | Decision under discussion |
| Accepted | Approved and implemented |
| Superseded | Replaced by another ADR |
| Deprecated | No longer recommended |
| Rejected | Considered but not selected |

---

# ADR Naming Convention

```
ADR-0001-project-architecture.md

ADR-0002-spring-boot.md

ADR-0003-layered-architecture.md
```

Rules

- Four-digit numbering
- Kebab-case filenames
- Never reuse numbers

---

# ADR Template

Every ADR follows the same structure.

```
Title

Status

Date

Decision Makers

Context

Problem Statement

Decision

Alternatives

Decision Drivers

Consequences

Implementation Notes

Future Considerations

Related Documents

Interview Questions
```

---

# ADR Principles

Every ADR should be

- Objective
- Concise
- Permanent
- Versioned
- Traceable

Never write ADRs based on personal preference.

Every decision should be technically justified.

---

# ADR Review Rules

An ADR should be created whenever

- Architecture changes
- Technology changes
- Deployment strategy changes
- Security strategy changes
- Package structure changes
- Infrastructure changes

Minor code refactoring does not require an ADR.

---

# Current ADR Index

| ADR | Title | Status |
|------|-------|--------|
| ADR-0001 | Overall Project Architecture | Planned |
| ADR-0002 | Spring Boot Framework | Planned |
| ADR-0003 | Layered Architecture | Planned |
| ADR-0004 | Constructor Injection | Planned |
| ADR-0005 | JWT Authentication | Planned |
| ADR-0006 | WebClient over RestTemplate | Planned |
| ADR-0007 | Strategy Pattern | Planned |
| ADR-0008 | Redis Distributed State | Accepted |
| ADR-0009 | Lua Scripts | Planned |
| ADR-0010 | Stateless Gateway | Planned |
| ADR-0011 | Docker Compose | Accepted |
| ADR-0012 | React Dashboard | Planned |
| ADR-0013 | Testing Strategy | Planned |
| ADR-0014 | Package-by-Feature | Planned |
| ADR-0015 | Observability Strategy | Planned |

---

# Cross References

Each ADR should reference related documents whenever applicable.

Examples

- Architecture.md
- Engineering_spec.md
- Development_Playbook.md
- Product_requirements.md

Likewise, architecture and implementation documents should reference relevant ADRs.

---

# End of ADR README

# ADR-0001 — Overall Project Architecture

Status: Accepted

ADR ID: ADR-0001

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

This project aims to build a production-inspired Distributed API Gateway from scratch.

Unlike a typical CRUD application, this project will eventually contain:

- Authentication
- Authorization
- Multiple Rate Limiting Algorithms
- Distributed State
- Redis
- Concurrency
- Horizontal Scaling
- Monitoring
- Dashboard
- Docker Deployment

The architecture chosen today must continue supporting every future phase without requiring major restructuring.

Architecture changes become increasingly expensive as the project grows.

Therefore the initial architecture must prioritize long-term maintainability over short-term implementation speed.

---

# Problem Statement

What overall software architecture should the project follow?

The chosen architecture must satisfy the following requirements.

- Easy for new developers to understand
- Supports incremental development
- Supports isolated modules
- Easy to test
- Easy to extend
- Suitable for distributed systems
- Suitable for production engineering
- Suitable for interviews

---

# Decision

The project will adopt a **Layered Modular Monolith Architecture**.

The system will initially remain a single deployable Spring Boot application while being internally divided into well-defined modules.

Every module owns one responsibility.

Business capabilities remain isolated through package boundaries rather than deployment boundaries.

Microservices are intentionally excluded from Version 1.

---

# Architecture Overview

```text
                    Client

                       │

                       ▼

               Presentation Layer

                       │

                       ▼

               Application Layer

                       │

                       ▼

                  Domain Layer

                       │

                       ▼

              Infrastructure Layer

                       │

        ┌──────────────┴──────────────┐

        ▼                             ▼

      Redis                    Backend Services
```

---

# Architectural Characteristics

| Characteristic | Decision |
|---------------|----------|
| Deployment | Modular Monolith |
| Scalability | Horizontal |
| Gateway | Stateless |
| Shared State | Redis |
| Configuration | External |
| Testing | Layered |
| Security | Centralized |
| Logging | Structured |
| Monitoring | Built-in |

---

# Why Layered Modular Monolith?

The project intentionally separates

Deployment Architecture

from

Software Architecture.

Although deployment consists of

Gateway

↓

Redis

↓

Dashboard

the application itself remains modular internally.

This provides

- Simplicity
- Maintainability
- Clear package ownership
- Easier debugging
- Easier learning
- Easier testing

---

# Alternatives Considered

## Alternative 1

Traditional Layered Architecture

Advantages

- Familiar
- Easy

Disadvantages

- Package boundaries become weak
- Features become scattered
- Large service layer

Decision

Rejected.

Reason

Poor scalability for long-term maintenance.

---

## Alternative 2

Hexagonal Architecture

Advantages

- Excellent testability
- Infrastructure independent
- Highly maintainable

Disadvantages

- Higher complexity
- Large learning curve
- Too much abstraction for current project scope

Decision

Rejected for Version 1.

Future possibility.

---

## Alternative 3

Clean Architecture

Advantages

- Excellent dependency control
- Highly scalable
- Strong separation

Disadvantages

- Significant boilerplate
- Slower implementation
- Harder for beginners to understand

Decision

Rejected for Version 1.

Concepts will still influence implementation.

---

## Alternative 4

Microservices

Advantages

- Independent deployment
- Independent scaling
- Team autonomy

Disadvantages

- Operational complexity
- Service discovery
- Network communication
- Distributed debugging
- Deployment overhead

Decision

Rejected.

Reason

The project aims to learn distributed systems inside one repository before introducing service decomposition.

---

## Selected Architecture

Layered Modular Monolith

Reason

Best balance between

- Learning
- Maintainability
- Simplicity
- Scalability
- Interview Value

---

# Decision Drivers

The following factors influenced the decision.

## Maintainability

Very High

Modules remain isolated.

---

## Scalability

High

Gateway remains stateless.

Distributed scaling added later.

---

## Testability

High

Layers remain independently testable.

---

## Learning Value

Very High

Introduces enterprise engineering practices without unnecessary complexity.

---

## Resume Value

Very High

Shows understanding of architecture evolution.

---

## Engineering Discipline

Very High

Architecture remains stable throughout every phase.

---

# Consequences

## Positive

- Simple deployment
- Easy debugging
- Modular package ownership
- Easier onboarding
- Easier documentation
- Easier testing
- Supports incremental evolution
- Strong interview discussion points

---

## Negative

- Entire application deployed together
- Not independently deployable
- Large codebase over time
- Requires discipline to maintain boundaries

---

# Architectural Rules

Every implementation must follow these rules.

## Rule 1

Controllers never contain business logic.

---

## Rule 2

Business logic belongs only inside Services and Domain modules.

---

## Rule 3

Infrastructure never owns business rules.

---

## Rule 4

Dependencies always point inward.

---

## Rule 5

Redis is accessed only through Infrastructure.

---

## Rule 6

Authentication remains independent from Routing.

---

## Rule 7

Rate Limiting remains independent from Authentication.

---

## Rule 8

Every module owns one responsibility.

---

# Implementation Impact

This decision directly affects

Package Structure

↓

Development Playbook

↓

Engineering Specification

↓

Testing Strategy

↓

Folder Organization

↓

Documentation

No future phase should violate these architectural principles.

---

# Risks

Potential risks include

- Large service classes
- Poor package ownership
- Layer violations
- Tight coupling
- Utility class abuse

These risks are mitigated through

- Engineering Review Checklist
- AI Implementation Guide
- Architecture Document
- Code Reviews

---

# Future Evolution

Possible future migration paths

Version 1

↓

Layered Modular Monolith

↓

Version 2

↓

Modular Monolith

↓

Version 3

↓

Selective Microservices

Migration should occur only if justified by measurable requirements.

---

# Related Documents

- Architecture.md
- Engineering_spec.md
- Development_Playbook.md
- AI_Guide.md
- Engineering_review_checklist.md

---

# Success Criteria

This architectural decision is considered successful if

- Every phase builds upon the same architecture.
- Package boundaries remain respected.
- No large-scale refactoring is required.
- New modules integrate without architectural changes.
- Developers can understand the project quickly.
- The architecture can be defended confidently during technical interviews.

---

# Interview Questions

- Why choose a Modular Monolith instead of Microservices?
- Why not use Clean Architecture?
- What are the trade-offs of Layered Architecture?
- What architectural changes would be required to migrate to Microservices?
- How does this architecture support scalability?
- How do package boundaries enforce modularity?
- Why is the Gateway stateless?
- How does Redis complement this architecture?
- What risks does a Modular Monolith introduce?
- Under what conditions would you migrate away from this architecture?

---

# References

Related ADRs

- ADR-0002 — Spring Boot Framework
- ADR-0003 — Layered Architecture
- ADR-0008 — Redis as Distributed State
- ADR-0010 — Stateless Gateway

---

# Final Decision

**Accepted**

The Distributed API Gateway will follow a **Layered Modular Monolith Architecture** for Version 1.

This architecture provides the best balance between maintainability, extensibility, engineering discipline, interview value, and incremental learning while leaving a clear migration path toward more advanced architectures if future requirements justify the transition.


# ADR-0002 — Spring Boot as the Primary Backend Framework

Status: Accepted

ADR ID: ADR-0002

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The Distributed API Gateway requires a backend framework capable of supporting:

- REST APIs
- Authentication
- Authorization
- Middleware
- Dependency Injection
- Configuration Management
- Redis Integration
- Metrics
- Logging
- Docker Deployment
- Production Readiness

The framework must also support future project phases without requiring architectural changes.

Since this repository is intended as both a production-inspired engineering project and a learning project, the chosen framework should balance industry relevance with developer productivity.

---

# Problem Statement

Which backend framework should be used for the project?

The framework should satisfy the following requirements:

- Enterprise adoption
- Excellent documentation
- Strong ecosystem
- Dependency Injection
- Testing support
- Security support
- Redis integration
- Docker friendliness
- Long-term maintainability
- Interview relevance

---

# Decision

The project will use **Spring Boot 3.x** running on **Java 21**.

Spring Boot will provide the application framework while the project's architecture, business logic, and engineering decisions remain framework-independent wherever possible.

Spring Boot is treated as infrastructure, not business logic.

---

# Why Spring Boot?

Spring Boot provides an opinionated yet highly extensible framework for building production-ready backend systems.

It eliminates boilerplate while still exposing enterprise-grade capabilities.

The project benefits from:

- Embedded web server
- Dependency Injection
- Configuration management
- Validation
- Spring Security
- Spring Data Redis
- Actuator
- Testing support
- Production tooling

---

# Architecture Placement

```text
Application

↓

Spring Boot

↓

Application Configuration

↓

Gateway Modules

↓

Infrastructure

↓

Redis
```

Spring Boot owns application startup.

Business modules remain independent from framework details whenever possible.

---

# Alternatives Considered

## Alternative 1

Spring MVC (without Spring Boot)

Advantages

- Complete control
- Lightweight

Disadvantages

- Significant manual configuration
- Boilerplate
- Slower development

Decision

Rejected.

Reason

Unnecessary complexity.

---

## Alternative 2

Micronaut

Advantages

- Fast startup
- Low memory
- Compile-time dependency injection

Disadvantages

- Smaller ecosystem
- Less common in enterprise
- Less interview familiarity

Decision

Rejected.

---

## Alternative 3

Quarkus

Advantages

- Native image support
- Kubernetes focused
- Fast startup

Disadvantages

- More cloud-native oriented
- Smaller learning ecosystem

Decision

Rejected.

---

## Alternative 4

Node.js + Express

Advantages

- Simple
- Fast development

Disadvantages

- Different technology stack
- Less aligned with Java backend interviews

Decision

Rejected.

---

## Alternative 5

ASP.NET Core

Advantages

- Excellent performance
- Mature ecosystem

Disadvantages

- Requires C#
- Outside project learning goals

Decision

Rejected.

---

# Decision Drivers

## Industry Adoption

★★★★★

One of the most widely used Java backend frameworks.

---

## Learning Value

★★★★★

Introduces enterprise backend development practices.

---

## Ecosystem

★★★★★

Large ecosystem including

- Security
- Redis
- Testing
- Monitoring
- Validation

---

## Maintainability

★★★★★

Convention-over-configuration reduces boilerplate.

---

## Resume Value

★★★★★

Highly recognized by recruiters.

---

## Community Support

★★★★★

Extensive documentation and community resources.

---

# Consequences

## Positive

- Rapid development
- Mature ecosystem
- Excellent testing support
- Built-in dependency injection
- Production-ready features
- Easy Redis integration
- Easy Docker deployment

---

## Negative

- Learning curve
- Hidden framework complexity
- Startup overhead compared to lighter frameworks
- Framework conventions must be understood

---

# Architectural Rules

Spring Boot should only provide infrastructure.

Business logic must remain independent.

Examples

Correct

```text
Controller

↓

Service

↓

RateLimiter
```

Incorrect

```text
Controller

↓

Spring Framework Utility

↓

Business Logic
```

---

# Spring Boot Responsibilities

Owns

- Application startup
- Bean management
- Configuration
- Dependency Injection
- Embedded server
- Validation
- Security integration

Does NOT own

- Rate limiting algorithms
- Routing logic
- Business workflows
- Engineering decisions

---

# Dependency Injection Policy

Constructor Injection only.

Forbidden

```java
@Autowired
private JwtService jwtService;
```

Preferred

```java
public AuthenticationService(JwtService jwtService) {
    this.jwtService = jwtService;
}
```

Reason

- Immutable dependencies
- Easier testing
- Better readability

---

# Configuration Strategy

Use

```
application.yml

application-dev.yml

application-test.yml

application-prod.yml
```

Never hardcode

- Secrets
- Ports
- Redis host
- JWT secret
- Timeouts

---

# Spring Modules Used

Version 1

- Spring Boot Starter Web
- Spring Validation
- Spring Security
- Spring Data Redis
- Spring Boot Actuator
- Spring Test

Future

- Spring Cloud
- Spring Gateway (comparison only)
- Spring Retry

---

# Rejected Spring Modules

Spring Cloud Gateway

Reason

The purpose of this repository is to build an API Gateway from scratch.

Using Spring Cloud Gateway would eliminate the learning objectives.

---

# Risks

Potential risks

- Overusing Spring annotations
- Framework-dependent business logic
- Large configuration classes
- Hidden magic

Mitigation

- Explicit architecture
- Clear package ownership
- Constructor injection
- AI implementation rules
- Engineering review checklist

---

# Future Considerations

Potential future enhancements

- Native Image (GraalVM)
- Spring Cloud
- Spring Boot 4.x
- Kubernetes integration
- Spring Observability

None are required for Version 1.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- Engineering_spec.md
- AI_Guide.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0003 — Layered Architecture
- ADR-0004 — Constructor Injection

---

# Success Criteria

This decision is successful if

- Spring Boot supports every planned project phase.
- Business logic remains framework-independent.
- Dependency Injection improves modularity.
- Configuration remains externalized.
- Testing remains straightforward.
- Framework upgrades require minimal architectural changes.

---

# Interview Questions

- Why Spring Boot over Micronaut?
- Why Spring Boot over Quarkus?
- Why not build the Gateway using Spring Cloud Gateway?
- What problems does Spring Boot solve?
- What happens during Spring Boot startup?
- What is Dependency Injection?
- Why Constructor Injection?
- How does Spring Boot improve developer productivity?
- Which Spring modules are actually used in this project?
- How do you prevent business logic from becoming framework-dependent?

---

# Final Decision

**Accepted**

Spring Boot 3.x running on Java 21 will serve as the backend framework for the Distributed API Gateway.

The framework provides enterprise-grade infrastructure while business logic remains modular, testable, and independent of framework-specific implementation details wherever practical.

# ADR-0003 — Layered Architecture

Status: Accepted

ADR ID: ADR-0003

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The Distributed API Gateway will gradually evolve into a production-inspired backend system consisting of multiple independent modules including:

- Authentication
- Authorization
- Routing
- Rate Limiting
- Redis Integration
- Metrics
- Logging
- Dashboard

Without clearly defined architectural boundaries, responsibilities become mixed, resulting in:

- God Classes
- Tight Coupling
- Difficult Testing
- Difficult Refactoring
- Poor Maintainability

A consistent architectural style is required before implementation begins.

---

# Problem Statement

How should the internal application be organized?

The architecture must:

- Be easy to understand.
- Support incremental development.
- Allow independent modules.
- Encourage testing.
- Prevent architecture drift.
- Scale as new features are introduced.
- Remain suitable for interview discussion.

---

# Decision

The project will adopt a **Layered Architecture** consisting of four primary layers.

```text
Presentation Layer

↓

Application Layer

↓

Domain Layer

↓

Infrastructure Layer
```

Each layer has a clearly defined responsibility.

Dependencies always point downward.

No layer may directly bypass another layer.

---

# Layer Overview

## Presentation Layer

Responsibilities

- HTTP APIs
- Request Validation
- DTO Mapping
- Response Formatting

Packages

```
controller/

dto/
```

Knows

- HTTP
- DTOs

Does NOT Know

- Redis
- Algorithms
- Infrastructure

---

## Application Layer

Responsibilities

- Use Cases
- Workflow Coordination
- Module Orchestration

Packages

```
service/
```

Knows

- Domain
- Infrastructure Interfaces

Does NOT Know

- HTTP
- Redis Implementation

---

## Domain Layer

Responsibilities

- Gateway Logic
- Algorithms
- Business Rules

Packages

```
routing/

ratelimiter/

security/

model/
```

Knows

Only business concepts.

Must remain independent of Spring Boot whenever practical.

---

## Infrastructure Layer

Responsibilities

- Redis
- Logging
- Metrics
- Configuration

Packages

```
redis/

metrics/

logging/

config/
```

Knows

External systems.

Does NOT own

Business Rules.

---

# Architecture Diagram

```text
Client

↓

Presentation

↓

Application

↓

Domain

↓

Infrastructure

↓

Redis

↓

Backend Services
```

---

# Dependency Rules

Allowed

```text
Presentation

↓

Application

↓

Domain

↓

Infrastructure
```

Forbidden

```text
Infrastructure

↓

Presentation
```

---

Forbidden

```text
Controller

↓

Redis
```

---

Forbidden

```text
Controller

↓

Rate Limiting Algorithm
```

---

Correct

```text
Controller

↓

Gateway Service

↓

Rate Limiter

↓

Redis Service
```

---

# Why Layered Architecture?

The architecture separates responsibilities.

Instead of asking

"What package does this class belong to?"

We ask

"What responsibility does this class own?"

This creates predictable boundaries.

---

# Alternatives Considered

## Alternative 1

Package by Layer

Example

```
controllers/

services/

repositories/
```

Advantages

- Simple
- Familiar

Disadvantages

- Features become scattered.
- Large service package.
- Weak modularity.

Decision

Rejected.

---

## Alternative 2

Clean Architecture

Advantages

- Excellent dependency management
- Extremely testable

Disadvantages

- Significant abstraction
- Additional complexity
- Large number of interfaces

Decision

Rejected for Version 1.

---

## Alternative 3

Hexagonal Architecture

Advantages

- Infrastructure independent
- Excellent testing

Disadvantages

- More complex
- Harder for beginners
- Slower implementation

Decision

Rejected.

---

## Alternative 4

Feature-first Architecture

Advantages

- Excellent modularity
- Easy ownership

Disadvantages

- More difficult while learning Spring Boot fundamentals

Decision

Influences our package structure but not selected as the primary architectural style.

---

# Decision Drivers

Maintainability

★★★★★

---

Readability

★★★★★

---

Testability

★★★★★

---

Learning Value

★★★★★

---

Scalability

★★★★☆

---

Framework Independence

★★★★☆

---

# Layer Responsibilities

## Controllers

Responsible for

✓ HTTP

✓ Validation

✓ DTO Mapping

Must Never

✗ Business Logic

✗ Redis

✗ Algorithms

---

## Services

Responsible for

✓ Workflow

✓ Coordination

✓ Module Interaction

Must Never

✗ Parse HTTP

✗ Build Responses

---

## Domain

Responsible for

✓ Algorithms

✓ Business Rules

✓ Gateway Logic

Must Never

✗ Spring MVC

✗ RedisTemplate

---

## Infrastructure

Responsible for

✓ External Systems

✓ Configuration

✓ Logging

✓ Metrics

Must Never

✗ Gateway Decisions

---

# Benefits

- Predictable project structure
- Easier onboarding
- Easier testing
- Smaller classes
- Better separation of concerns
- Independent module evolution
- Easier documentation

---

# Trade-offs

## Advantages

- Easy to understand
- Strong engineering discipline
- Production-friendly
- Easy code reviews

---

## Disadvantages

- More files
- Additional indirection
- Requires discipline
- Slightly more boilerplate

---

# Risks

Possible risks

- Service layer becoming too large
- Layer violations
- Utility class abuse
- Hidden coupling

Mitigation

- Engineering Review Checklist
- AI Guide
- ADR Reviews
- Package ownership rules

---

# Impact on the Repository

This decision affects

- Folder Structure
- Package Design
- Dependency Rules
- Testing Strategy
- Documentation
- Code Reviews
- Future ADRs

Every future implementation must respect these boundaries.

---

# Future Evolution

As the project grows

Presentation

↓

Application

↓

Domain

↓

Infrastructure

may later evolve into

Feature Modules

while preserving the same dependency direction.

This ADR does not prevent future migration.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- AI_Guide.md
- Engineering_review_checklist.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0002 — Spring Boot Framework
- ADR-0004 — Constructor Injection
- ADR-0014 — Package-by-Feature

---

# Success Criteria

This decision is successful if

- Every class has a clear layer.
- No layer violations exist.
- Business logic remains isolated.
- Infrastructure remains replaceable.
- Developers can understand the project structure quickly.
- Future features integrate without restructuring.

---

# Interview Questions

- Why Layered Architecture?
- Layered vs Clean Architecture?
- Layered vs Hexagonal Architecture?
- Why shouldn't Controllers contain business logic?
- What belongs inside the Domain Layer?
- Why isolate Infrastructure?
- What problems arise from layer violations?
- How would this architecture evolve into Microservices?
- What are the disadvantages of Layered Architecture?
- How do package boundaries enforce architecture?

---

# Final Decision

**Accepted**

The Distributed API Gateway will use a four-layer architecture consisting of Presentation, Application, Domain, and Infrastructure layers.

This structure provides strong separation of concerns, predictable dependencies, excellent maintainability, and a solid foundation for the project's incremental evolution into a production-inspired distributed system.

# ADR-0004 — Constructor Injection as the Dependency Injection Strategy

Status: Accepted

ADR ID: ADR-0004

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The Distributed API Gateway consists of many collaborating components:

- Controllers
- Services
- Security
- Routing
- Rate Limiting
- Redis
- Metrics
- Logging

These components depend on one another.

A consistent Dependency Injection strategy is required to:

- Keep components loosely coupled
- Improve testability
- Prevent hidden dependencies
- Encourage immutable objects
- Simplify unit testing
- Improve readability

Spring supports multiple Dependency Injection mechanisms.

The project must standardize on one approach.

---

# Problem Statement

Which Dependency Injection strategy should be used throughout the project?

The solution should:

- Be easy to understand
- Improve testing
- Encourage immutability
- Prevent hidden dependencies
- Scale well as the project grows
- Follow modern Spring Boot best practices

---

# Decision

The project will use **Constructor Injection exclusively**.

Every required dependency must be supplied through the constructor.

Field Injection and Setter Injection are prohibited except where required by external frameworks.

---

# Dependency Flow

```text
Controller

↓

Service

↓

Rate Limiter

↓

Redis Service

↓

Redis
```

Each dependency is explicitly visible in the constructor.

---

# Example

## Correct

```java
@Service
public class GatewayService {

    private final RouteResolver routeResolver;
    private final RouteForwarder routeForwarder;

    public GatewayService(RouteResolver routeResolver,
                          RouteForwarder routeForwarder) {
        this.routeResolver = routeResolver;
        this.routeForwarder = routeForwarder;
    }
}
```

---

## Incorrect

```java
@Service
public class GatewayService {

    @Autowired
    private RouteResolver routeResolver;

    @Autowired
    private RouteForwarder routeForwarder;
}
```

---

# Why Constructor Injection?

Constructor Injection makes dependencies explicit.

When reading a class, a developer immediately knows

- what the class requires
- how many collaborators it has
- whether the class has too many responsibilities

Large constructors often indicate a design problem.

---

# Alternatives Considered

## Alternative 1

Field Injection

Advantages

- Short code
- Less typing

Disadvantages

- Hidden dependencies
- Difficult unit testing
- Mutable dependencies
- Reflection-based injection
- Harder to understand

Decision

Rejected.

---

## Alternative 2

Setter Injection

Advantages

- Optional dependencies
- Runtime replacement

Disadvantages

- Mutable objects
- Partially initialized objects
- Harder to reason about

Decision

Rejected.

---

## Alternative 3

Manual Object Creation

Advantages

- No framework dependency
- Full control

Disadvantages

- Tight coupling
- Boilerplate
- Difficult lifecycle management

Decision

Rejected.

---

# Decision Drivers

## Readability

★★★★★

---

## Testability

★★★★★

---

## Immutability

★★★★★

---

## Maintainability

★★★★★

---

## Industry Practice

★★★★★

---

# Benefits

## Explicit Dependencies

Every dependency appears in the constructor.

Nothing is hidden.

---

## Immutable Objects

Dependencies become

```java
private final
```

making accidental reassignment impossible.

---

## Easier Unit Testing

Objects can be instantiated without Spring.

Example

```java
GatewayService service =
    new GatewayService(mockResolver, mockForwarder);
```

No container required.

---

## Better Design Feedback

Large constructors indicate that a class may be violating the Single Responsibility Principle.

Constructor Injection naturally encourages smaller classes.

---

# Architectural Rules

Every dependency should be

```
private final
```

Every required dependency belongs in the constructor.

No dependency should be injected later.

---

# Allowed Exceptions

Setter Injection may be used only when

- required by third-party frameworks
- configuration objects require it
- optional dependencies genuinely exist

These cases should be documented.

---

# Lombok Policy

The project may use

```java
@RequiredArgsConstructor
```

for reducing boilerplate.

Example

```java
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProvider jwtProvider;

}
```

This is acceptable because dependencies remain constructor-based.

---

# Dependency Visibility

Constructor Injection makes coupling measurable.

Example

```java
public GatewayService(

RouteResolver,

RouteForwarder,

MetricsService,

LoggerService,

RateLimiter,

AuthenticationService)
```

A constructor with many parameters signals that responsibilities should be reviewed.

---

# Testing Impact

Constructor Injection enables

✓ Pure Unit Tests

✓ Mockito Tests

✓ Manual Object Creation

✓ No Spring Context

Result

Faster tests.

---

# Risks

Potential risks

- Constructors become large
- Developers may inject unnecessary dependencies
- Overuse of services

Mitigation

- Engineering Review Checklist
- SOLID Principles
- Code Reviews

---

# Impact on the Repository

This decision affects

- Every Service
- Every Controller
- Every Configuration Class
- Every Strategy
- Every Factory

Every newly created component must follow this rule.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- AI_Guide.md
- Engineering_review_checklist.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0002 — Spring Boot Framework
- ADR-0003 — Layered Architecture

---

# Success Criteria

This decision is successful if

- No Field Injection exists.
- Dependencies are explicit.
- Classes remain immutable.
- Unit tests can instantiate components without Spring.
- Hidden dependencies are eliminated.

---

# Interview Questions

- Why Constructor Injection over Field Injection?
- Why is Field Injection discouraged?
- How does Constructor Injection improve testing?
- What are hidden dependencies?
- What does `private final` achieve?
- When is Setter Injection appropriate?
- Does Constructor Injection improve immutability?
- Can Constructor Injection expose design problems?
- Why does Spring recommend Constructor Injection?
- When would you avoid Constructor Injection?

---

# Final Decision

**Accepted**

The Distributed API Gateway will use **Constructor Injection exclusively** for dependency management.

This decision improves readability, testability, immutability, and long-term maintainability while aligning the project with modern Spring Boot engineering practices.

# ADR-0005 — JWT Authentication for Stateless Security

Status: Accepted

ADR ID: ADR-0005

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The API Gateway is the first entry point for every client request.

Before forwarding requests to backend services, the Gateway must verify:

- Who is making the request?
- Is the user authenticated?
- Is the user authorized?
- Can this request proceed?

Since the Gateway is designed for horizontal scaling, the authentication mechanism must remain stateless.

---

# Problem Statement

Which authentication mechanism should be used?

The authentication solution must satisfy the following requirements.

- Stateless
- Horizontally scalable
- Suitable for REST APIs
- Fast validation
- Easy integration with Spring Security
- Compatible with multiple Gateway instances
- Production-ready

---

# Decision

The project will use **JWT (JSON Web Token)** based authentication.

Authentication is performed once during login.

Every subsequent request carries a signed JWT inside the Authorization header.

The Gateway validates the token locally before forwarding the request.

No server-side session state is maintained.

---

# Authentication Flow

```text
Client

↓

Login

↓

Authentication Service

↓

JWT Generated

↓

Client Stores JWT

↓

Subsequent Requests

↓

Authorization Header

↓

Gateway Validation

↓

Backend
```

---

# JWT Structure

```
Header

↓

Payload

↓

Signature
```

---

# JWT Claims

Version 1

```
userId

email

roles

issuedAt

expiration
```

Future

```
tenantId

permissions

organization

sessionId
```

---

# Token Lifecycle

```text
Login

↓

Generate JWT

↓

Return Token

↓

Client Sends Token

↓

Gateway Validates

↓

Forward Request
```

---

# Alternatives Considered

## Alternative 1

HTTP Sessions

Advantages

- Simple
- Mature

Disadvantages

- Server-side session storage
- Sticky sessions
- Poor horizontal scalability

Decision

Rejected.

---

## Alternative 2

Opaque Tokens

Advantages

- Revocable

Disadvantages

- Database lookup required
- Additional latency

Decision

Rejected.

---

## Alternative 3

OAuth2

Advantages

- Industry standard
- Rich ecosystem

Disadvantages

- Higher complexity
- Outside Version 1 scope

Decision

Deferred.

Future enhancement.

---

## Alternative 4

API Keys

Advantages

- Simple

Disadvantages

- Not suitable for user authentication
- Limited authorization capabilities

Decision

Rejected.

---

# Decision Drivers

Scalability

★★★★★

---

Performance

★★★★★

---

REST Compatibility

★★★★★

---

Statelessness

★★★★★

---

Learning Value

★★★★★

---

# Why JWT?

JWT allows every Gateway instance to validate authentication locally.

No shared session storage is required.

This perfectly matches our stateless Gateway architecture.

---

# Security Architecture

```text
Client

↓

JWT Filter

↓

Token Validation

↓

Security Context

↓

Authorization

↓

Gateway

↓

Backend
```

---

# Responsibilities

Authentication

Determines

"Who are you?"

---

Authorization

Determines

"What can you access?"

These responsibilities remain separate.

---

# Token Validation Rules

Every request must validate

✓ Signature

✓ Expiration

✓ Token Structure

✓ Required Claims

✓ Algorithm

Reject immediately if validation fails.

---

# Password Policy

Passwords

Must

✓ Be BCrypt hashed

✓ Never logged

✓ Never returned

✓ Never stored in plaintext

---

# JWT Storage Policy

Clients store the token.

The Gateway stores nothing.

No sessions.

No authentication cache.

No server-side login state.

---

# Security Rules

Never include

- Password
- Secret
- Refresh Token
- Personal Information

inside JWT claims.

JWT should contain only information required for authorization.

---

# Token Expiration

Access Tokens

Short-lived

Future

Refresh Tokens

Long-lived

Refresh Tokens are intentionally excluded from Version 1.

---

# Failure Handling

Missing Token

↓

401 Unauthorized

---

Expired Token

↓

401 Unauthorized

---

Invalid Signature

↓

401 Unauthorized

---

Insufficient Role

↓

403 Forbidden

---

# Benefits

- Stateless authentication
- Easy horizontal scaling
- No server-side sessions
- Low validation latency
- Works across multiple Gateway instances

---

# Trade-offs

Advantages

- Fast validation
- Distributed friendly
- Simple deployment

Disadvantages

- Revocation is difficult
- Token size larger than session IDs
- Claims cannot easily change until token expiry

---

# Risks

Potential risks

- Token theft
- Weak signing secret
- Long token lifetime
- Sensitive claims

Mitigation

- Strong signing key
- HTTPS
- Short expiration
- Minimal claims

---

# Implementation Impact

This ADR directly affects

- Security Package
- JwtService
- JwtFilter
- AuthenticationService
- SecurityConfig
- AuthorizationFilter

---

# Future Considerations

Future improvements

- Refresh Tokens
- OAuth2
- OpenID Connect
- Key Rotation
- JWKS
- Token Blacklisting

None are required for Version 1.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- AI_Guide.md
- Engineering_spec.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0004 — Constructor Injection
- ADR-0010 — Stateless Gateway

---

# Success Criteria

This decision is successful if

- Authentication is stateless.
- Every Gateway validates JWT independently.
- No server-side session exists.
- Authentication integrates with Spring Security.
- Horizontal scaling requires no authentication changes.

---

# Interview Questions

- Why JWT over Sessions?
- Why is JWT suitable for API Gateways?
- What is inside a JWT?
- How is JWT validated?
- Why shouldn't passwords be stored inside JWT?
- What are JWT drawbacks?
- How do you invalidate JWTs?
- What is the difference between Authentication and Authorization?
- Why is BCrypt used?
- How does JWT support horizontal scaling?

---

# Final Decision

**Accepted**

The Distributed API Gateway will use **JWT-based stateless authentication** integrated with Spring Security.

This approach aligns with the project's stateless architecture, enables horizontal scalability, minimizes authentication latency, and provides a production-ready security model suitable for distributed systems.

# ADR-0006 — WebClient over RestTemplate for HTTP Communication

Status: Accepted

ADR ID: ADR-0006

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The Distributed API Gateway is responsible for forwarding incoming HTTP requests to backend services.

This forwarding operation is the core responsibility of the Gateway.

The HTTP client chosen for this responsibility directly affects:

- Performance
- Scalability
- Latency
- Thread Utilization
- Future asynchronous capabilities

Spring provides multiple HTTP client implementations.

The project must standardize on one.

---

# Problem Statement

Which HTTP client should be used for forwarding requests to backend services?

The solution should:

- Support production workloads
- Be actively maintained
- Integrate well with Spring Boot
- Support future asynchronous execution
- Be suitable for high-concurrency environments
- Scale as Gateway traffic increases

---

# Decision

The project will use **Spring WebClient** as the primary HTTP client.

RestTemplate will not be used.

Although the initial implementation will execute requests synchronously, the Gateway architecture will remain compatible with asynchronous and reactive execution in future versions.

---

# Request Flow

```text
Client

↓

Gateway

↓

Route Resolver

↓

WebClient

↓

Backend Service

↓

Gateway

↓

Client
```

---

# Why WebClient?

WebClient is Spring's modern HTTP client.

It is:

- Non-blocking capable
- Reactive-ready
- Actively maintained
- Recommended by Spring
- Better suited for future scalability

Although Version 1 does not use reactive programming, choosing WebClient avoids future migration.

---

# Alternatives Considered

## Alternative 1

RestTemplate

Advantages

- Simple
- Easy to understand
- Large number of examples

Disadvantages

- Legacy API
- Blocking
- Maintenance mode
- Limited future evolution

Decision

Rejected.

---

## Alternative 2

Apache HttpClient

Advantages

- Mature
- Highly configurable

Disadvantages

- Additional dependency
- More boilerplate
- Less integrated with Spring

Decision

Rejected.

---

## Alternative 3

Java HttpClient

Advantages

- Built into Java
- Lightweight

Disadvantages

- Less Spring integration
- Manual configuration
- Less convenient

Decision

Rejected.

---

## Alternative 4

Spring Cloud Gateway

Advantages

- Production-ready

Disadvantages

- Already implements forwarding
- Removes learning objective

Decision

Rejected.

---

# Decision Drivers

Future Scalability

★★★★★

---

Spring Integration

★★★★★

---

Industry Recommendation

★★★★★

---

Maintainability

★★★★★

---

Learning Value

★★★★★

---

# Why Not RestTemplate?

Spring officially recommends WebClient for new development.

RestTemplate remains functional but is effectively in maintenance mode.

Choosing WebClient prevents future migration costs.

---

# WebClient Lifecycle

A single WebClient bean should be created during application startup.

The bean is reused throughout the application.

Creating a new WebClient for every request is prohibited.

---

# Dependency Flow

```text
GatewayController

↓

GatewayService

↓

RouteForwarder

↓

WebClient
```

Controllers must never invoke WebClient directly.

---

# Configuration

WebClient should be configured centrally.

Example responsibilities

- Base configuration
- Timeouts
- Connection limits
- Default headers
- Logging filters

Configuration belongs inside

```
config/

WebClientConfiguration
```

---

# Timeout Strategy

Version 1

Reasonable request timeout.

Future

- Route-specific timeout
- Retry policy
- Circuit Breaker

---

# Error Handling

The Gateway should correctly propagate

- 2xx
- 3xx
- 4xx
- 5xx

Backend responses should not be silently modified.

Gateway-generated errors should remain distinguishable from backend errors.

---

# Logging Rules

Log

- Request URI
- HTTP Method
- Response Status
- Latency

Never log

- Authorization Header
- JWT
- Sensitive Payloads

---

# Performance Considerations

WebClient instances are thread-safe.

A singleton instance minimizes

- Object creation
- Connection overhead
- Memory allocation

---

# Risks

Potential risks

- Incorrect timeout configuration
- Connection exhaustion
- Improper exception handling

Mitigation

- Central configuration
- Connection pooling
- Integration tests
- Benchmarking

---

# Implementation Impact

This ADR directly affects

- RouteForwarder
- GatewayService
- WebClientConfiguration
- Integration Tests
- Performance Benchmarks

---

# Future Considerations

Future improvements

- Connection Pool tuning
- HTTP/2
- Reactive Gateway
- Retry policies
- Circuit Breaker
- Distributed tracing

These are outside Version 1.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- Engineering_spec.md
- AI_Guide.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0002 — Spring Boot Framework
- ADR-0010 — Stateless Gateway

---

# Success Criteria

This decision is successful if

- Every backend request is forwarded using WebClient.
- A singleton WebClient instance is reused.
- Request forwarding remains performant.
- Future asynchronous execution requires minimal architectural changes.
- No RestTemplate exists in the repository.

---

# Interview Questions

- Why WebClient over RestTemplate?
- Is WebClient always reactive?
- Why not use Apache HttpClient?
- Why should WebClient be a singleton?
- How does WebClient improve scalability?
- What happens if the backend service times out?
- How would you implement retries?
- How would you add a Circuit Breaker?
- How does WebClient fit into the Gateway architecture?
- What future enhancements become easier with WebClient?

---

# Final Decision

**Accepted**

The Distributed API Gateway will use **Spring WebClient** as its HTTP client for all backend communication.

This decision aligns the project with modern Spring Boot practices, improves long-term maintainability, prepares the architecture for future scalability, and avoids reliance on legacy HTTP client APIs.

# ADR-0007 — Strategy Pattern for Rate Limiting Algorithms

Status: Accepted

ADR ID: ADR-0007

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

One of the primary objectives of this project is to implement and compare multiple rate limiting algorithms.

Version 1 includes:

- Fixed Window
- Sliding Window Counter
- Sliding Window Log
- Token Bucket
- Leaky Bucket

Every algorithm solves the same problem but differs in:

- Accuracy
- Memory Consumption
- Throughput
- Burst Handling
- Complexity

The Gateway must support switching algorithms without changing the Gateway itself.

---

# Problem Statement

How should multiple rate limiting algorithms be implemented?

The solution should:

- Support multiple algorithms
- Allow runtime selection
- Keep the Gateway independent of implementations
- Be easy to test
- Allow future algorithms
- Follow SOLID principles

---

# Decision

The project will implement rate limiting using the **Strategy Pattern**.

Every algorithm implements a common interface.

The Gateway depends only on the abstraction.

Algorithms remain completely interchangeable.

---

# Architecture

```text
Gateway

↓

RateLimiter

↓

Strategy Factory

↓

RateLimitingStrategy

↓

┌──────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│              │              │              │              │
▼              ▼              ▼              ▼              ▼

Fixed      SlidingCounter   SlidingLog   TokenBucket   LeakyBucket
```

---

# Why Strategy Pattern?

Every algorithm performs the same responsibility.

```
Allow Request?
```

Only the implementation differs.

The Strategy Pattern allows:

- interchangeable algorithms
- cleaner architecture
- easier testing
- simpler benchmarking

without changing Gateway logic.

---

# Strategy Interface

Every algorithm implements

```java
public interface RateLimitingStrategy {

    RateLimitDecision allowRequest(
        ClientIdentifier client,
        RateLimitPolicy policy
    );

}
```

The Gateway never knows which algorithm is executing.

---

# Strategy Selection

The Gateway delegates algorithm selection to a factory.

```text
Gateway

↓

RateLimiter

↓

RateLimiterFactory

↓

Correct Strategy
```

The Gateway never performs

```java
if (algorithm == TOKEN_BUCKET)

else if (...)

else if (...)
```

This logic belongs inside the factory.

---

# Alternatives Considered

## Alternative 1

Large if-else block

Advantages

- Easy

Disadvantages

- Poor scalability
- Violates Open-Closed Principle
- Difficult testing

Decision

Rejected.

---

## Alternative 2

Switch Statement

Advantages

- Simple

Disadvantages

- Must modify code for every new algorithm

Decision

Rejected.

---

## Alternative 3

Inheritance

Advantages

- Reuse

Disadvantages

- Tight coupling
- Weak extensibility

Decision

Rejected.

---

## Alternative 4

Reflection

Advantages

- Dynamic

Disadvantages

- Unnecessary complexity
- Harder debugging
- Reduced readability

Decision

Rejected.

---

# Decision Drivers

Extensibility

★★★★★

---

Maintainability

★★★★★

---

Testability

★★★★★

---

SOLID Compliance

★★★★★

---

Benchmarking

★★★★★

---

# Open-Closed Principle

New algorithms should require

```
New Class

↓

Register Factory

↓

Done
```

Existing Gateway code should remain unchanged.

---

# Responsibilities

## Gateway

Responsible for

- Delegating requests

Must never

- Implement algorithms

---

## RateLimiter

Responsible for

- Calling the selected strategy

Must never

- Contain algorithm logic

---

## Strategy Factory

Responsible for

- Selecting implementation

Must never

- Execute rate limiting

---

## Strategy

Responsible for

- One algorithm only

Must never

- Know Gateway internals

---

# Benchmarking Benefits

Since every algorithm implements the same interface,

benchmarking becomes straightforward.

Example

```text
Token Bucket

↓

Measure

↓

Sliding Counter

↓

Measure

↓

Leaky Bucket

↓

Measure
```

The benchmark runner remains unchanged.

---

# Future Algorithms

Adding a new algorithm becomes

```
Create Strategy

↓

Register Factory

↓

Add Tests

↓

Benchmark
```

No Gateway changes.

---

# Testing Strategy

Every strategy receives

- identical inputs
- identical policies
- identical expected outputs

This allows objective comparison.

---

# Risks

Potential risks

- Factory becoming too large
- Strategy duplication
- Configuration errors

Mitigation

- Small strategies
- Policy abstraction
- Factory tests

---

# Implementation Impact

This ADR directly affects

- RateLimiter
- Strategy Interface
- Strategy Factory
- Policy Resolver
- Benchmarks
- Tests

---

# Future Considerations

Potential additions

- Adaptive Rate Limiting
- AI-driven Rate Limiting
- Dynamic Policy Switching
- Geo-aware Algorithms
- Hybrid Algorithms

None require Gateway changes.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- Engineering_spec.md
- AI_Guide.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0008 — Redis as Distributed State
- ADR-0009 — Lua Scripts

---

# Success Criteria

This decision is successful if

- Gateway remains algorithm-independent.
- Every algorithm implements one interface.
- New algorithms require no Gateway modifications.
- Benchmarks compare algorithms uniformly.
- Tests remain reusable across implementations.

---

# Interview Questions

- Why use the Strategy Pattern?
- Strategy Pattern vs Factory Pattern?
- How does Strategy support the Open-Closed Principle?
- Why not use if-else?
- How would you add a new algorithm?
- Why benchmark through a common interface?
- What responsibilities belong inside the Strategy?
- Can Strategy Pattern improve testing?
- Does Strategy increase maintainability?
- Where should algorithm selection occur?

---

# Final Decision

**Accepted**

The Distributed API Gateway will implement all rate limiting algorithms using the **Strategy Pattern**.

This decision ensures loose coupling, extensibility, uniform benchmarking, and adherence to SOLID principles while allowing the Gateway to remain completely independent of individual rate limiting implementations.

# ADR-0008 — Redis as the Distributed State Store

Status: Accepted

ADR ID: ADR-0008

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The Distributed API Gateway initially executes as a single application.

As the project evolves, multiple Gateway instances will run simultaneously behind a Load Balancer.

All Gateway instances must observe identical rate limiting information.

Without shared state:

- Users can bypass rate limits.
- Counters become inconsistent.
- Horizontal scaling fails.
- Traffic becomes unpredictable.

The Gateway therefore requires a centralized, high-performance distributed state store.

---

# Problem Statement

Where should distributed Gateway state be stored?

The solution must provide:

- Extremely low latency
- Atomic operations
- TTL support
- High throughput
- Horizontal scalability
- Production adoption
- Easy Spring integration

---

# Decision

The project will use **Redis** as the centralized distributed state store.

Redis will become the single source of truth for all temporary Gateway state.

Gateway instances remain completely stateless.

---

# Architecture

```text
                Client

                   │

                   ▼

           Load Balancer

      ┌────────┼────────┐

      ▼        ▼        ▼

 Gateway 1  Gateway 2  Gateway 3

      │        │        │

      └────────┼────────┘

               ▼

             Redis

               ▼

        Backend Services
```

---

# Why Redis?

Redis satisfies every requirement of the Gateway.

It provides

- In-memory performance
- Atomic operations
- TTL
- Distributed counters
- Excellent Java support
- Production maturity

Redis is already widely used by

- Cloudflare
- Kong
- NGINX API Gateway
- Envoy
- Netflix
- Stripe

for similar workloads.

---

# Responsibilities

Redis owns

- Rate Limit Counters
- Token Bucket State
- Sliding Window State
- Gateway Metrics
- Temporary Distributed Data

Redis does NOT own

- Business Data
- User Profiles
- Authentication Logic
- Configuration Files

---

# State Ownership

Gateway owns

- Request processing
- Authentication
- Routing
- Decision making

Redis owns

- Shared temporary state

This separation keeps the Gateway stateless.

---

# Alternatives Considered

## Alternative 1

ConcurrentHashMap

Advantages

- Simple
- Fast

Disadvantages

- Local memory only
- Not distributed
- Lost after restart
- Cannot support multiple Gateway instances

Decision

Rejected.

---

## Alternative 2

SQL Database

Advantages

- Persistent
- Familiar

Disadvantages

- Too slow
- Expensive writes
- High latency
- Poor fit for counters

Decision

Rejected.

---

## Alternative 3

Hazelcast

Advantages

- Distributed cache

Disadvantages

- More operational complexity
- Smaller ecosystem

Decision

Rejected.

---

## Alternative 4

Apache Ignite

Advantages

- Distributed memory

Disadvantages

- Higher learning curve
- Unnecessary complexity

Decision

Rejected.

---

# Decision Drivers

Latency

★★★★★

---

Scalability

★★★★★

---

Atomic Operations

★★★★★

---

TTL Support

★★★★★

---

Spring Integration

★★★★★

---

Learning Value

★★★★★

---

# Redis Data Model

Example keys

```
rate_limit:user:123

token_bucket:user:123

sliding_window:user:123

metrics:gateway

gateway:health
```

Future

```
cluster:leader

dashboard:cache

analytics:requests
```

---

# Key Design Rules

Keys must be

- Human-readable
- Predictable
- Environment aware
- Consistent

Example

```
prod:rate_limit:user:123

dev:gateway:metrics
```

---

# TTL Strategy

Every temporary key must expire automatically.

Examples

| Data | TTL |
|------|------|
| Fixed Window | Window Size |
| Sliding Counter | Window Size |
| Token Bucket | Configurable |
| Metrics Cache | Configurable |

TTL prevents stale state accumulation.

---

# Atomic Operations

The Gateway depends on Redis atomicity.

Preferred commands

```
INCR

DECR

SETNX

GETSET

EXPIRE
```

Complex operations should use Lua Scripts.

---

# Connection Strategy

A single shared connection pool will be used.

Gateway components must never create Redis connections directly.

Flow

```text
Gateway

↓

RedisService

↓

Lettuce

↓

Redis
```

---

# Failure Handling

Redis unavailable

↓

Gateway reports degraded health

↓

Requests fail gracefully

↓

Metrics updated

Redis failures should never crash the application.

---

# Benefits

- Shared distributed state
- High throughput
- Excellent latency
- Easy scaling
- Automatic expiration
- Industry-standard solution

---

# Trade-offs

Advantages

- Extremely fast
- Atomic
- Lightweight
- Easy integration

Disadvantages

- Additional infrastructure
- In-memory storage
- Operational dependency
- Network latency compared to local memory

---

# Risks

Potential risks

- Redis outage
- Poor key design
- Memory growth
- Connection exhaustion

Mitigation

- Health checks
- Connection pooling
- TTL
- Monitoring
- Key naming conventions

---

# Implementation Impact

This ADR directly affects

- RedisService
- RateLimiter
- TokenBucket
- Sliding Window
- Dashboard
- Metrics
- Docker Compose

---

# Future Considerations

Future enhancements

- Redis Cluster
- Sentinel
- Replication
- Multi-region Redis
- Redis Streams
- Pub/Sub

These are intentionally excluded from Version 1.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- Engineering_spec.md
- AI_Guide.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0007 — Strategy Pattern
- ADR-0009 — Lua Scripts
- ADR-0010 — Stateless Gateway

---

# Success Criteria

This decision is successful if

- Every Gateway instance observes identical rate limiting state.
- Local counters no longer exist.
- Redis operations remain atomic.
- Horizontal scaling requires no application changes.
- TTL prevents stale distributed state.

---

# Interview Questions

- Why Redis instead of ConcurrentHashMap?
- Why is Redis suitable for rate limiting?
- Why not use MySQL?
- What Redis data structures are used?
- What is TTL?
- Why are atomic operations important?
- What happens if Redis crashes?
- How would Redis Cluster improve this architecture?
- Why should Gateway instances remain stateless?
- How does Redis enable horizontal scaling?

---

# Final Decision

**Accepted**

The Distributed API Gateway will use **Redis as its centralized distributed state store**.

Redis provides the low latency, atomic operations, TTL support, and scalability required for production-grade rate limiting while enabling the Gateway to remain completely stateless and horizontally scalable.

# ADR-0009 — Lua Scripts for Atomic Redis Operations

Status: Accepted

ADR ID: ADR-0009

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The API Gateway relies on Redis for maintaining distributed rate limiting state.

Several rate limiting algorithms require multiple Redis operations to execute as a single logical unit.

Example

Token Bucket

```
Read Current Tokens

↓

Calculate Refill

↓

Update Bucket

↓

Store New State

↓

Return Decision
```

If these operations execute independently, concurrent Gateway instances may modify the same state simultaneously, leading to inconsistent results.

---

# Problem Statement

How should multiple Redis operations be executed atomically?

The solution must:

- Prevent race conditions
- Support multiple Gateway instances
- Minimize network latency
- Execute deterministically
- Maintain correctness under heavy concurrency

---

# Decision

The project will use **Redis Lua Scripts** for every multi-step Redis operation requiring atomic execution.

Simple Redis commands such as

```
INCR

SET

GET

EXPIRE
```

will continue using native Redis commands.

Complex workflows will execute through Lua.

---

# Why Lua Scripts?

Redis guarantees that a Lua script executes atomically.

While one script is executing,

no other client can modify Redis state.

This eliminates race conditions without requiring distributed locks.

---

# Execution Flow

```text
Gateway

↓

RedisService

↓

Lua Script

↓

Redis

↓

Atomic Result

↓

Gateway
```

---

# Example

Without Lua

```text
Gateway 1

Read Counter = 5

-------------------

Gateway 2

Read Counter = 5

-------------------

Gateway 1

Write 6

-------------------

Gateway 2

Write 6
```

Expected

```
7
```

Actual

```
6
```

---

With Lua

```text
Gateway 1

↓

Execute Script

↓

Redis Locked

↓

Update

↓

Return

↓

Gateway 2 Executes
```

No race condition occurs.

---

# Use Cases

Lua scripts will be used for

- Token Bucket
- Sliding Window Counter
- Sliding Window Log
- Multi-key Updates
- Counter + TTL Updates

Simple Fixed Window may continue using native Redis commands.

---

# Script Responsibilities

A Lua script should

✓ Read state

✓ Update state

✓ Return decision

within one atomic execution.

Scripts must never contain business rules unrelated to Redis state.

---

# Alternatives Considered

## Alternative 1

Separate Redis Commands

Advantages

- Easy implementation

Disadvantages

- Race conditions
- Multiple network round trips
- Inconsistent state

Decision

Rejected.

---

## Alternative 2

Distributed Locks

Advantages

- Strong consistency

Disadvantages

- Higher latency
- Lock management
- Operational complexity

Decision

Rejected.

---

## Alternative 3

Optimistic Transactions (WATCH/MULTI/EXEC)

Advantages

- Atomic

Disadvantages

- Retry complexity
- Additional network communication
- Lower throughput under contention

Decision

Rejected.

---

## Alternative 4

Application-Level Synchronization

Advantages

- Simple in a single JVM

Disadvantages

- Fails in distributed systems
- Multiple Gateway instances remain unsynchronized

Decision

Rejected.

---

# Decision Drivers

Correctness

★★★★★

---

Concurrency

★★★★★

---

Performance

★★★★★

---

Distributed Safety

★★★★★

---

Operational Simplicity

★★★★☆

---

# Lua Script Rules

Scripts should

- Execute quickly
- Avoid loops over large datasets
- Minimize memory usage
- Return deterministic results

Long-running scripts are prohibited.

---

# Script Organization

Project structure

```
redis/

scripts/

increment.lua

token_bucket.lua

sliding_counter.lua

sliding_log.lua
```

Each algorithm owns its own script.

---

# Redis Communication

```text
Gateway

↓

RateLimiter

↓

RedisService

↓

LuaExecutor

↓

Redis
```

Only the Redis layer interacts with Lua.

Controllers and Services remain unaware of scripting details.

---

# Performance Benefits

Compared with multiple Redis commands

Lua provides

- Single network round trip
- Atomic execution
- Lower latency
- Reduced race conditions

---

# Risks

Potential risks

- Long-running scripts
- Difficult debugging
- Complex script logic

Mitigation

- Keep scripts small
- One responsibility per script
- Unit tests
- Integration tests

---

# Failure Handling

If a Lua script fails

↓

Redis returns an error

↓

Gateway logs failure

↓

Controlled error response

The Gateway should never ignore Lua execution failures.

---

# Benchmark Requirements

Compare

Native Redis Commands

↓

Lua Scripts

Measure

- Latency
- Throughput
- Network Calls
- Consistency
- Concurrent Correctness

Document benchmark results.

---

# Implementation Impact

This ADR directly affects

- RedisService
- LuaExecutor
- TokenBucket
- SlidingWindowCounter
- SlidingWindowLog
- Benchmark Suite

---

# Future Considerations

Potential future improvements

- Redis Functions
- Redis Modules
- Script Caching
- Cluster-aware scripts

None are required for Version 1.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- Engineering_spec.md
- AI_Guide.md

---

# Related ADRs

- ADR-0007 — Strategy Pattern
- ADR-0008 — Redis as Distributed State
- ADR-0010 — Stateless Gateway

---

# Success Criteria

This decision is successful if

- Multi-step Redis operations execute atomically.
- Concurrent Gateway instances cannot corrupt shared state.
- Lua scripts remain small and maintainable.
- Benchmarks demonstrate improved correctness and reduced network overhead.

---

# Interview Questions

- Why use Lua Scripts in Redis?
- Why not use distributed locks?
- What problems do Lua scripts solve?
- How are Lua scripts executed inside Redis?
- What is the difference between `MULTI/EXEC` and Lua?
- Why are Lua scripts atomic?
- Which rate limiting algorithms require Lua?
- What happens if a Lua script fails?
- How do Lua scripts reduce latency?
- What are the limitations of Redis Lua scripts?

---

# Final Decision

**Accepted**

The Distributed API Gateway will use **Redis Lua Scripts** for all complex multi-step Redis operations that require atomic execution.

This decision guarantees correctness under concurrent distributed traffic while reducing network overhead and eliminating race conditions without introducing distributed locking complexity.

# ADR-0010 — Stateless Gateway Architecture

Status: Accepted

ADR ID: ADR-0010

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

One of the primary goals of this project is to build a horizontally scalable Distributed API Gateway.

As traffic grows, new Gateway instances should be deployable without modifying application logic.

For horizontal scaling to work, Gateway instances must behave identically.

No Gateway instance should contain information that another instance does not possess.

This requirement fundamentally influences the entire architecture.

---

# Problem Statement

Should the Gateway maintain application state locally, or should it remain completely stateless?

The architecture must satisfy:

- Horizontal Scalability
- High Availability
- Load Balancing
- Fault Tolerance
- Easy Deployment
- Simple Recovery
- Consistent Rate Limiting

---

# Decision

The Gateway will be implemented as a **Stateless Application**.

Every Gateway instance will process requests independently.

All shared state will be externalized to infrastructure services such as Redis.

No request should depend on the Gateway instance that receives it.

---

# Definition of Stateless

A stateless application does not permanently store client-specific or request-specific data in its own memory between requests.

Every request contains all information required for processing.

Gateway memory is treated as temporary execution space only.

---

# Architecture

```text
                Client

                   │

                   ▼

            Load Balancer

        ┌────────┼────────┐

        ▼        ▼        ▼

    Gateway1  Gateway2  Gateway3

        │        │        │

        └────────┼────────┘

                 ▼

               Redis

                 ▼

          Backend Services
```

---

# Request Lifecycle

```text
Client

↓

Gateway Instance

↓

JWT Validation

↓

Rate Limiter

↓

Redis

↓

Route Resolver

↓

Backend

↓

Gateway

↓

Client
```

The request may be processed by any Gateway instance.

Results remain identical.

---

# State Classification

## Allowed Local State

Gateway instances may temporarily hold

- Local Variables
- Request Objects
- Response Objects
- Thread-local Context
- Immutable Configuration
- Cached Read-only Metadata

---

## Forbidden Local State

Gateway instances must never store

- User Sessions
- Rate Limit Counters
- Authentication Sessions
- Shared Metrics
- User-specific Caches
- Request History

---

# Why Stateless?

Stateless applications provide predictable scaling.

Adding another Gateway node becomes

```text
Gateway

↓

Gateway

↓

Gateway

↓

Gateway
```

No synchronization between nodes is required.

---

# Alternatives Considered

## Alternative 1

Session-Based Gateway

Advantages

- Simple authentication

Disadvantages

- Sticky sessions
- Difficult scaling
- Session replication
- Failover complexity

Decision

Rejected.

---

## Alternative 2

Local Memory Cache

Advantages

- Extremely fast

Disadvantages

- Inconsistent state
- Cannot scale
- Lost after restart

Decision

Rejected.

---

## Alternative 3

Database-backed Sessions

Advantages

- Shared state

Disadvantages

- Increased latency
- Database dependency
- Poor performance

Decision

Rejected.

---

# Decision Drivers

Horizontal Scaling

★★★★★

---

High Availability

★★★★★

---

Deployment Simplicity

★★★★★

---

Maintainability

★★★★★

---

Industry Practice

★★★★★

---

# Shared Infrastructure

State belongs in

```
Redis

↓

Configuration

↓

Monitoring

↓

Logging
```

Not inside Gateway memory.

---

# Authentication

Authentication remains stateless.

JWT contains user identity.

Gateway validates JWT.

Gateway stores nothing.

---

# Rate Limiting

Rate limiting state belongs inside Redis.

Every Gateway observes identical counters.

---

# Load Balancer Compatibility

Since every Gateway behaves identically,

the Load Balancer may freely distribute requests.

Supported strategies

- Round Robin
- Least Connections
- Weighted
- Random

No sticky sessions required.

---

# Failure Recovery

Gateway crashes

↓

New Gateway starts

↓

Receives traffic immediately

↓

No session restoration required

↓

No data loss

Recovery becomes extremely simple.

---

# Deployment Benefits

New deployment

↓

Start Gateway

↓

Health Check

↓

Join Cluster

↓

Receive Traffic

Deployment requires no migration.

---

# Benefits

- Horizontal Scaling
- Easy Deployment
- High Availability
- Simplified Recovery
- Stateless Authentication
- Easier Load Balancing
- Better Cloud Compatibility

---

# Trade-offs

Advantages

- Excellent scalability
- Simple deployment
- Easy failover
- Cloud native

Disadvantages

- Additional Redis dependency
- More network communication
- Infrastructure complexity

---

# Risks

Potential risks

- Redis becomes unavailable
- Excessive remote lookups
- Poor cache design

Mitigation

- Health Checks
- Redis Monitoring
- Connection Pooling
- TTL
- Graceful Failure Handling

---

# Implementation Rules

Gateway classes

Must

✓ Process requests

✓ Validate JWT

✓ Route traffic

✓ Publish metrics

Must Never

✗ Store sessions

✗ Store counters

✗ Store authentication state

✗ Store distributed configuration

---

# Cloud Readiness

Stateless design enables future deployment on

- Kubernetes
- ECS
- Docker Swarm
- Azure Container Apps
- Google Cloud Run

without application redesign.

---

# Impact on Repository

This decision directly affects

- Authentication
- Rate Limiting
- Redis
- Docker
- Deployment
- Load Balancer
- Monitoring

Nearly every future phase depends upon this ADR.

---

# Future Considerations

Potential enhancements

- Redis Cluster
- Multi-region deployment
- Kubernetes auto-scaling
- Global Load Balancers
- Service Discovery

These require no change to the stateless design.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- Engineering_spec.md
- AI_Guide.md

---

# Related ADRs

- ADR-0005 — JWT Authentication
- ADR-0008 — Redis as Distributed State
- ADR-0011 — Docker Compose Deployment

---

# Success Criteria

This decision is successful if

- Any Gateway instance can process any request.
- No Gateway stores persistent client state.
- Horizontal scaling requires only infrastructure changes.
- Gateway failures do not result in session loss.
- Authentication and rate limiting continue working across multiple instances.

---

# Interview Questions

- What is a Stateless Application?
- Why are API Gateways typically stateless?
- Why are sticky sessions unnecessary?
- How does JWT support stateless authentication?
- What data belongs in Redis?
- What happens when a Gateway crashes?
- How does statelessness improve horizontal scaling?
- What are the disadvantages of stateless systems?
- Why is Redis essential in this architecture?
- How does Kubernetes benefit from stateless services?

---

# Final Decision

**Accepted**

The Distributed API Gateway will follow a **Stateless Architecture**.

All Gateway instances remain interchangeable, horizontally scalable, and independently deployable by externalizing shared state to Redis. This decision forms the foundation for high availability, load balancing, distributed rate limiting, and cloud-native deployment.

# ADR-0011 — Docker Compose for Local Distributed Deployment

Status: Accepted

ADR ID: ADR-0011

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The Distributed API Gateway is designed as a distributed system.

By Phase 6 the project consists of multiple components:

- Gateway Instance 1
- Gateway Instance 2
- Gateway Instance 3
- Redis
- Backend Service
- Dashboard

Running these services manually would require:

- Multiple terminals
- Manual networking
- Manual port mapping
- Manual startup order
- Manual shutdown

This process is error-prone and does not resemble production deployment.

A repeatable deployment mechanism is required.

---

# Problem Statement

How should multiple services be deployed for local development?

The solution should:

- Start every service with one command
- Create networking automatically
- Support multiple Gateway instances
- Be easy to reproduce
- Require minimal setup
- Prepare developers for production deployment

---

# Decision

The project will use **Docker Compose** as the primary local orchestration tool.

Every infrastructure component will run as an isolated container.

Docker Compose becomes the standard local deployment environment.

---

# Deployment Architecture

```text
                Docker Compose

                      │

      ┌───────────────┼────────────────┐

      ▼               ▼                ▼

 Gateway 1       Gateway 2       Gateway 3

              ▼

             Redis

              ▼

        Backend Service

              ▼

          Dashboard
```

---

# Why Docker Compose?

Docker Compose provides

- Multi-container deployment
- Automatic networking
- Service discovery
- Dependency ordering
- Reproducible environments

without requiring Kubernetes.

---

# Responsibilities

Docker Compose owns

- Service orchestration
- Networking
- Container lifecycle
- Environment variables
- Volumes

Application code owns

- Business logic
- Routing
- Authentication
- Rate Limiting

---

# Alternatives Considered

## Alternative 1

Manual Docker Commands

Advantages

- Simple

Disadvantages

- Difficult to manage
- Repetitive
- Error-prone
- Poor scalability

Decision

Rejected.

---

## Alternative 2

Kubernetes

Advantages

- Production standard
- Automatic scaling
- Self healing

Disadvantages

- Large learning curve
- Heavy setup
- Outside Version 1 scope

Decision

Deferred.

Future enhancement.

---

## Alternative 3

Run Everything Locally

Advantages

- Simple

Disadvantages

- No isolation
- Different developer environments
- Doesn't simulate production

Decision

Rejected.

---

# Decision Drivers

Developer Experience

★★★★★

---

Repeatability

★★★★★

---

Maintainability

★★★★★

---

Learning Value

★★★★★

---

Production Similarity

★★★★☆

---

# Service Definition

Compose will manage

```
gateway-1

gateway-2

gateway-3

redis

backend

dashboard
```

Future

```
prometheus

grafana
```

---

# Networking Strategy

Docker Compose automatically creates

```
gateway-network
```

Services communicate through

```
redis

backend

gateway-1
```

instead of IP addresses.

---

# Startup Order

```text
Redis

↓

Backend

↓

Gateway Cluster

↓

Dashboard
```

Health checks determine readiness.

---

# Environment Variables

Every service receives configuration through

```
.env

↓

docker-compose.yml

↓

application.yml
```

No secrets are committed.

---

# Volume Strategy

Persistent volumes are used only where required.

Examples

- Redis Data (optional)
- Logs (future)

Gateway containers remain disposable.

---

# Scaling

Gateway instances can be scaled by changing

```yaml
gateway:
  replicas: N
```

(or equivalent Compose configuration)

Application code remains unchanged.

---

# Health Checks

Each service exposes

```
/health
```

Docker Compose waits until services become healthy before dependent services start.

---

# Failure Handling

If

Gateway 2

fails

Docker Compose allows restart without affecting

Gateway 1

Gateway 3

Redis remains shared.

---

# Benefits

- One-command startup
- One-command shutdown
- Identical developer environments
- Automatic networking
- Production-like deployment
- Easier onboarding

---

# Trade-offs

Advantages

- Lightweight
- Easy setup
- Widely adopted
- Excellent for development

Disadvantages

- Not a production orchestrator
- Limited scheduling features
- No automatic scaling
- No rolling deployments

---

# Risks

Potential risks

- Port conflicts
- Resource consumption
- Docker daemon issues

Mitigation

- Configurable ports
- Environment variables
- Health checks
- Documentation

---

# Implementation Impact

This ADR directly affects

- Dockerfile
- docker-compose.yml
- Deployment Guide
- Development Playbook
- CI Pipeline

---

# Future Considerations

Future migration path

```
Docker Compose

↓

Docker Swarm (optional)

↓

Kubernetes

↓

Helm
```

The application architecture should require no code changes during migration.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- Deployment Guide
- AI_Guide.md

---

# Sprint 9 Redis Infrastructure Decision (Addendum)

**Status:** Accepted (Implemented in Sprint 9)  
**Decision Date:** 2026-08-22  

### Context & Justification
For Sprint 9 local development infrastructure:
- **Redis Image**: `redis:7.2-alpine` (Selected as the Sprint 9 local development Redis image after reviewing project requirements; the repository documentation does not prescribe an exact Redis image tag).
- **Alpine Base Rationale**: Small footprint (~30MB), low attack surface, rapid local container startup.
- **Topology**: Standalone single-node Redis container (`docker/docker-compose.yml`) mapped to `6379:6379`.
- **Health Check**: Native `redis-cli ping` execution (interval 5s, timeout 3s, retries 5).
- **Connection Pooling**: `commons-pool2` added to support Spring Data Redis Lettuce connection pool (`spring.data.redis.lettuce.pool.*`).
- **Scope Limit**: Development infrastructure setup only. Redis rate-limiting algorithms, repositories, and Lua scripts begin in Sprint 10.

---

# Related ADRs

- ADR-0008 — Redis as Distributed State
- ADR-0010 — Stateless Gateway
- ADR-0015 — Observability Strategy

---

# Success Criteria

This decision is successful if

- The complete system starts with one command.
- Every developer gets the same environment.
- Multiple Gateway instances communicate correctly.
- Redis networking works automatically.
- Developers never need manual container configuration.

---

# Interview Questions

- Why Docker Compose instead of manual Docker?
- Why not Kubernetes?
- What problems does Docker Compose solve?
- How do containers communicate?
- What is a Docker network?
- How are environment variables managed?
- Why should Gateway containers remain stateless?
- How would you migrate from Docker Compose to Kubernetes?
- How does Docker Compose improve developer productivity?
- What are the limitations of Docker Compose?

---

# Final Decision

**Accepted**

The Distributed API Gateway will use **Docker Compose** as its standard local deployment and orchestration solution.

This decision provides reproducible environments, automatic networking, simplified multi-service management, and a production-inspired development workflow while keeping operational complexity appropriate for Version 1.

# ADR-0012 — React for the Monitoring Dashboard

Status: Accepted

ADR ID: ADR-0012

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

By Phase 7, the API Gateway exposes operational data such as:

- Gateway Health
- Cluster Status
- Redis Health
- Request Metrics
- Rate Limiting Statistics
- Route Analytics
- JVM Metrics

While these metrics can be accessed through REST APIs, they are difficult to interpret without visualization.

A monitoring dashboard is required.

---

# Problem Statement

Which frontend technology should be used for building the Gateway Dashboard?

The solution should:

- Display real-time metrics
- Consume REST APIs
- Render charts efficiently
- Be lightweight
- Be easy to maintain
- Be widely used in industry
- Complement a Java backend

---

# Decision

The project will use **React** as the frontend framework for the monitoring dashboard.

The dashboard remains an independent frontend application communicating exclusively through REST APIs.

There will be no server-side rendering.

---

# Dashboard Architecture

```text
Administrator

↓

React Dashboard

↓

REST APIs

↓

Gateway

↓

Metrics

↓

Redis

↓

Backend Services
```

---

# Why React?

React provides

- Component-based architecture
- Fast rendering
- Mature ecosystem
- Excellent chart integration
- Large community
- High industry adoption

It also pairs naturally with Spring Boot REST APIs.

---

# Responsibilities

The Dashboard owns

- Visualization
- User Interface
- Charts
- Status Indicators
- API Consumption

The Gateway owns

- Metrics
- Analytics
- Business Logic
- Authentication
- Monitoring APIs

The Dashboard must never contain business logic.

---

# Alternatives Considered

## Alternative 1

Thymeleaf

Advantages

- Integrated with Spring
- Simple

Disadvantages

- Server-side rendering
- Poor interactivity
- Less modern

Decision

Rejected.

---

## Alternative 2

Angular

Advantages

- Enterprise framework
- Strong tooling

Disadvantages

- Steeper learning curve
- Larger framework
- More boilerplate

Decision

Rejected.

---

## Alternative 3

Vue.js

Advantages

- Lightweight
- Easy to learn

Disadvantages

- Smaller enterprise adoption compared to React

Decision

Rejected.

---

## Alternative 4

Plain HTML/CSS/JavaScript

Advantages

- No framework

Disadvantages

- Difficult state management
- Poor scalability
- Harder maintenance

Decision

Rejected.

---

# Decision Drivers

Industry Adoption

★★★★★

---

Maintainability

★★★★★

---

Developer Experience

★★★★★

---

Visualization Support

★★★★★

---

Learning Value

★★★★★

---

# Dashboard Modules

The React application consists of

```
Dashboard

↓

Overview

↓

Gateway Status

↓

Redis Status

↓

Cluster Status

↓

Traffic Analytics

↓

Rate Limiting Analytics

↓

JVM Metrics

↓

Route Analytics
```

Each module should remain independent.

---

# Component Structure

Example

```
src/

components/

pages/

services/

hooks/

charts/

layouts/

utils/
```

Components own presentation.

Services own API communication.

---

# API Communication

Dashboard

↓

Axios / Fetch

↓

REST API

↓

Gateway

The Dashboard communicates only with public monitoring APIs.

No direct Redis communication.

---

# State Management

Version 1

Use

- React Hooks
- Context API (if needed)

Global state libraries (Redux, Zustand, etc.) are intentionally excluded.

The dashboard complexity does not justify them.

---

# Visualization Strategy

Charts should display

- Requests Per Second
- Requests Per Minute
- Allowed Requests
- Blocked Requests
- Top Routes
- Response Latency
- JVM Memory
- CPU Usage

Visualization is read-only.

---

# Refresh Strategy

Version 1

Polling

Every

```
5 seconds
```

Future

- Server-Sent Events
- WebSockets

The architecture should support future upgrades without redesign.

---

# UI Principles

The dashboard should be

- Clean
- Minimal
- Responsive
- Fast
- Information-focused

Avoid unnecessary animations.

Operational visibility is the priority.

---

# Authentication

Future

The dashboard may require authentication.

Version 1

Dashboard assumes trusted local access.

Role-based dashboard access is intentionally postponed.

---

# Performance Considerations

Dashboard rendering should remain efficient.

Avoid

- unnecessary re-renders
- excessive polling
- large component trees

Prefer memoization where justified.

---

# Risks

Potential risks

- Excessive API polling
- Large component hierarchy
- Chart rendering overhead

Mitigation

- Polling interval
- Component separation
- Efficient state updates

---

# Implementation Impact

This ADR directly affects

- Dashboard UI
- Monitoring APIs
- Analytics APIs
- Metrics APIs
- Deployment
- Docker Compose

---

# Future Considerations

Future enhancements

- WebSockets
- Dark Mode
- User Authentication
- Prometheus Integration
- Grafana Integration
- Custom Dashboards
- Alerting

These remain outside Version 1.

---

# Related Documents

- Architecture.md
- Development_Playbook.md
- Engineering_spec.md
- AI_Guide.md

---

# Related ADRs

- ADR-0011 — Docker Compose
- ADR-0015 — Observability Strategy

---

# Success Criteria

This decision is successful if

- Dashboard consumes Gateway APIs only.
- Metrics update automatically.
- Components remain modular.
- No business logic exists in the frontend.
- Dashboard remains lightweight and maintainable.

---

# Interview Questions

- Why React instead of Angular?
- Why not use Thymeleaf?
- Why separate frontend from backend?
- Why use polling initially?
- When would WebSockets become necessary?
- How should dashboard state be managed?
- Why avoid Redux in this project?
- How does React fit into the overall architecture?
- What frontend responsibilities belong in the dashboard?
- How would you scale the dashboard?

---

# Final Decision

**Accepted**

The Distributed API Gateway will use **React** as the frontend framework for its monitoring dashboard.

This decision provides a modern, modular, and industry-standard user interface while maintaining a clear separation between presentation and backend business logic. The dashboard will consume REST APIs exclusively, allowing the backend and frontend to evolve independently.

# ADR-0013 — Comprehensive Testing Strategy

Status: Accepted

ADR ID: ADR-0013

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The Distributed API Gateway is a critical infrastructure component.

It is responsible for

- Authentication
- Authorization
- Routing
- Rate Limiting
- Distributed State
- Request Forwarding
- Monitoring

A defect in any one of these modules can affect every request passing through the Gateway.

Testing therefore becomes a first-class engineering activity rather than a final verification step.

---

# Problem Statement

What testing strategy should the project adopt?

The strategy must:

- Detect regressions early
- Validate correctness
- Verify distributed behavior
- Support refactoring
- Encourage engineering discipline
- Produce confidence before deployment

---

# Decision

The project will adopt a **multi-layered testing strategy**.

Testing is performed at multiple levels, with each level validating different aspects of the system.

Every feature is considered incomplete until all required tests pass.

---

# Testing Pyramid

```text
                End-to-End

              Integration Tests

               Component Tests

                 Unit Tests
```

The majority of tests should be unit tests.

Higher-level tests should be fewer but more comprehensive.

---

# Testing Layers

## Unit Tests

Purpose

Verify individual classes in isolation.

Dependencies

Mocked

Tools

- JUnit 5
- Mockito

Examples

- JwtService
- RouteResolver
- TokenBucketStrategy
- RedisKeyBuilder

---

## Component Tests

Purpose

Verify interactions within a module.

Examples

Authentication Module

↓

JWT

↓

Security Context

↓

Authorization

---

## Integration Tests

Purpose

Verify communication between multiple modules.

Examples

Gateway

↓

Redis

↓

Authentication

↓

Rate Limiter

↓

Routing

---

## End-to-End Tests

Purpose

Validate complete request flow.

Example

```text
Client

↓

Gateway

↓

Authentication

↓

Rate Limiting

↓

Routing

↓

Backend

↓

Response
```

---

## Stress Tests

Purpose

Evaluate behavior under heavy concurrent traffic.

Examples

- 500 Users
- 1000 Users
- Burst Requests
- Sustained Traffic

---

## Benchmark Tests

Purpose

Measure performance rather than correctness.

Examples

- Rate Limiting Latency
- Redis Performance
- Routing Performance

---

# Why Multiple Test Layers?

Each testing level answers a different question.

Unit Tests

```
Is this class correct?
```

---

Integration Tests

```
Do modules work together?
```

---

End-to-End Tests

```
Does the entire system work?
```

---

Stress Tests

```
Does the system survive heavy load?
```

---

Benchmarks

```
Is the system fast enough?
```

---

# Alternatives Considered

## Alternative 1

Unit Tests Only

Advantages

- Fast
- Simple

Disadvantages

- Integration bugs remain undetected

Decision

Rejected.

---

## Alternative 2

End-to-End Only

Advantages

- High confidence

Disadvantages

- Slow
- Difficult debugging
- Expensive

Decision

Rejected.

---

## Alternative 3

Manual Testing

Advantages

- Flexible

Disadvantages

- Not repeatable
- Error-prone
- Not scalable

Decision

Rejected.

---

# Decision Drivers

Reliability

★★★★★

---

Maintainability

★★★★★

---

Regression Detection

★★★★★

---

Developer Confidence

★★★★★

---

Production Readiness

★★★★★

---

# Testing Rules

Every feature requires

✓ Unit Tests

↓

Integration Tests

↓

Failure Tests

↓

Edge Case Tests

Performance tests where applicable.

---

# Mocking Policy

Mock

External systems

Examples

- Redis
- Backend APIs
- JWT Provider

Do NOT mock

Business logic.

---

# Coverage Policy

The goal is not 100% coverage.

The goal is meaningful coverage.

Priority

- Critical logic
- Failure paths
- Edge cases
- Distributed behavior

Coverage percentage should never replace engineering judgment.

---

# Failure Scenarios

Every module should be tested for failures.

Examples

Authentication

- Invalid JWT
- Expired JWT

Redis

- Timeout
- Connection Failure

Gateway

- Unknown Route
- Invalid Request

Rate Limiter

- Capacity Exhausted
- Invalid Policy

---

# Edge Cases

Every algorithm should document edge cases.

Examples

Token Bucket

- Empty bucket
- Full bucket
- Large refill interval

Sliding Window

- Window boundary
- Concurrent requests

---

# Performance Testing

Measure

- Request Latency
- Redis Latency
- JWT Validation
- Route Resolution
- Gateway Throughput

Benchmark reports become part of repository documentation.

---

# Test Organization

```
src/

main/

test/

unit/

integration/

stress/

benchmark/
```

Tests should mirror production package structure.

---

# CI Integration

Every Pull Request must execute

- Unit Tests
- Integration Tests

Future

- Stress Tests (scheduled)
- Benchmark Tests (manual)

Deployment should fail if required tests fail.

---

# Risks

Potential risks

- Slow test suite
- Excessive mocking
- Flaky integration tests

Mitigation

- Isolate unit tests
- Keep integration tests deterministic
- Use Testcontainers for infrastructure

---

# Implementation Impact

This ADR directly affects

- Every module
- GitHub Actions
- Development Playbook
- Engineering Review Checklist

Testing becomes mandatory for every future phase.

---

# Future Considerations

Future improvements

- Mutation Testing
- Contract Testing
- Chaos Testing
- Performance Regression Testing
- Load Testing with Gatling/k6

These are beyond Version 1.

---

# Related Documents

- Development_Playbook.md
- Engineering_review_checklist.md
- AI_Guide.md
- Engineering_spec.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0008 — Redis as Distributed State
- ADR-0015 — Observability Strategy

---

# Success Criteria

This decision is successful if

- Every feature includes meaningful tests.
- Critical workflows are covered by integration tests.
- Distributed behavior is verified.
- Benchmarks are reproducible.
- CI prevents regressions.

---

# Interview Questions

- Why are unit tests insufficient?
- What is the Testing Pyramid?
- What should be mocked?
- Why avoid 100% coverage as a goal?
- What are integration tests?
- How would you test Redis?
- How do you test concurrent code?
- Why use Testcontainers?
- What is the difference between benchmarking and testing?
- How should CI enforce testing?

---

# Final Decision

**Accepted**

The Distributed API Gateway will follow a **multi-layered testing strategy** combining Unit Tests, Component Tests, Integration Tests, End-to-End Tests, Stress Tests, and Benchmark Tests.

This strategy prioritizes correctness, maintainability, and production confidence while ensuring that every phase of the project is validated before it is considered complete.

# ADR-0014 — Package-by-Feature Architecture

Status: Accepted

ADR ID: ADR-0014

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

As the API Gateway evolves, the number of classes will increase significantly.

By the end of Version 1 the repository will contain approximately

- 120–180 Java Classes
- Multiple Algorithms
- Authentication
- Redis
- Monitoring
- Dashboard
- Deployment
- Testing

Without a clear package organization the repository will quickly become difficult to navigate.

The package organization must support long-term maintainability.

---

# Problem Statement

How should the source code be organized?

The package structure should

- Scale with new features
- Reduce coupling
- Improve discoverability
- Support ownership
- Encourage modularity
- Reduce merge conflicts

---

# Decision

The project will primarily follow a **Package-by-Feature** organization while maintaining the layered architecture defined in ADR-0003.

Each major feature owns its own package.

Within a feature, classes remain organized by responsibility.

---

# Package Structure

```text
gateway/

authentication/

authorization/

routing/

ratelimiter/

redis/

metrics/

logging/

dashboard/

config/

exception/

util/
```

Each package owns one business capability.

---

# Why Package-by-Feature?

Developers think in features.

Not layers.

Example

Instead of searching

```
controllers/

services/

repositories/
```

to understand authentication,

a developer simply opens

```
authentication/
```

Everything related to authentication is nearby.

---

# Architecture Relationship

This ADR does **not** replace Layered Architecture.

Instead,

Layered Architecture defines

**dependencies**

Package-by-Feature defines

**organization**

Example

```text
authentication/

AuthenticationController

AuthenticationService

JwtService

AuthenticationRequest

AuthenticationResponse
```

Everything belongs to the Authentication feature.

---

# Alternatives Considered

## Alternative 1

Package-by-Layer

Example

```
controllers/

services/

repositories/
```

Advantages

- Familiar

Disadvantages

- Feature code scattered
- Difficult navigation
- Large service package

Decision

Rejected.

---

## Alternative 2

Flat Package

Advantages

- Very simple

Disadvantages

- Does not scale
- Poor maintainability

Decision

Rejected.

---

## Alternative 3

Package-by-Domain

Advantages

- Strong DDD support

Disadvantages

- Overkill for current project

Decision

Rejected.

---

# Decision Drivers

Maintainability

★★★★★

---

Readability

★★★★★

---

Scalability

★★★★★

---

Developer Experience

★★★★★

---

Learning Value

★★★★★

---

# Package Ownership

Each package owns exactly one responsibility.

Example

```
authentication/

↓

Login

Registration

JWT
```

---

```
routing/

↓

Route Resolution

Route Forwarding

Registry
```

---

```
ratelimiter/

↓

Algorithms

Policies

Strategy

Metrics
```

Packages should never own unrelated concepts.

---

# Dependency Rules

Allowed

```text
Controller

↓

Service

↓

Domain

↓

Infrastructure
```

Forbidden

```text
authentication

↓

dashboard
```

unless absolutely required.

Cross-feature dependencies should remain minimal.

---

# Internal Organization

Large packages may contain subpackages.

Example

```
ratelimiter/

algorithms/

policy/

strategy/

metrics/
```

This improves readability without violating feature ownership.

---

# Benefits

- Easier navigation
- Better ownership
- Cleaner pull requests
- Smaller merge conflicts
- Better onboarding
- Feature isolation

---

# Trade-offs

Advantages

- Highly scalable
- Easier maintenance
- Better readability

Disadvantages

- Slight duplication between features
- More packages
- Requires discipline

---

# Risks

Potential risks

- Circular feature dependencies
- Utility package abuse
- Shared code duplication

Mitigation

- Engineering Review Checklist
- Architecture reviews
- ADR enforcement
- AI Guide

---

# Package Design Rules

Every package must answer

"What business capability do I own?"

If the answer contains

"everything"

the package should be split.

---

# Shared Code Policy

Shared code belongs only in

```
config/

exception/

util/

constants/
```

Business logic must never migrate into util packages.

---

# Implementation Impact

This ADR affects

- Folder Structure
- Development Playbook
- AI Guide
- Engineering Spec
- Architecture

Every future feature must respect package ownership.

---

# Future Considerations

Future versions may evolve into

```
authentication-module

routing-module

ratelimiter-module
```

using Gradle multi-module builds.

The package structure should make this migration straightforward.

---

# Related Documents

- Architecture.md
- Engineering_spec.md
- Development_Playbook.md
- AI_Guide.md

---

# Related ADRs

- ADR-0001 — Overall Project Architecture
- ADR-0003 — Layered Architecture
- ADR-0013 — Comprehensive Testing Strategy

---

# Success Criteria

This decision is successful if

- Developers can locate feature code quickly.
- Features remain isolated.
- Packages have clear ownership.
- New features integrate naturally.
- Package boundaries remain respected.

---

# Interview Questions

- Package-by-Feature vs Package-by-Layer?
- Why organize code by business capability?
- How does Package-by-Feature improve scalability?
- How do you prevent circular dependencies?
- What belongs in a shared util package?
- How would this evolve into a multi-module project?
- Why is Package-by-Feature popular in large Spring Boot applications?
- How does it improve code reviews?
- How does it reduce merge conflicts?
- Does Package-by-Feature replace Layered Architecture?

---

# Final Decision

**Accepted**

The Distributed API Gateway will organize source code using a **Package-by-Feature** structure while preserving a layered dependency model.

This approach improves discoverability, ownership, scalability, and long-term maintainability without compromising architectural discipline.

# ADR-0015 — Observability Strategy

Status: Accepted

ADR ID: ADR-0015

Version: 1.0

Date: YYYY-MM-DD

Decision Makers

- Repository Owner
- Engineering Team

---

# Context

The Distributed API Gateway is infrastructure software.

Infrastructure systems cannot rely on debugging alone.

When failures occur, engineers must be able to answer questions such as:

- Is the Gateway healthy?
- Is Redis responding?
- Which route is failing?
- Which user is being rate limited?
- What is the request latency?
- Which Gateway instance handled the request?
- Why was a request rejected?

Without observability, diagnosing production issues becomes slow and unreliable.

Therefore, observability is treated as a core architectural capability rather than an optional feature.

---

# Problem Statement

How should the Gateway expose operational visibility?

The observability solution must:

- Detect failures quickly
- Simplify debugging
- Measure performance
- Monitor infrastructure
- Support future distributed deployments
- Minimize runtime overhead

---

# Decision

The project will adopt a **three-pillars observability strategy**.

The Gateway will expose

- Structured Logs
- Metrics
- Health Checks

Distributed tracing is intentionally deferred to a future version.

---

# Observability Architecture

```text
                Client

                   │

                   ▼

                Gateway

        ┌──────────┼──────────┐

        ▼          ▼          ▼

      Logging    Metrics    Health

        ▼          ▼          ▼

     Dashboard   Dashboard  Dashboard

        ▼

Future

Prometheus

↓

Grafana

↓

Distributed Tracing
```

---

# Why Observability?

Observability allows engineers to understand

- System Health
- System Behaviour
- System Performance

without attaching a debugger.

Production systems should explain themselves.

---

# Three Pillars

## Logging

Records

"What happened?"

---

## Metrics

Measure

"How much happened?"

---

## Health Checks

Answer

"Is the system operational?"

---

# Pillar 1 — Structured Logging

Every important event should produce a structured log.

Examples

- Startup
- Shutdown
- Authentication Failure
- Authorization Failure
- Route Resolution
- Rate Limit Exceeded
- Redis Failure
- Unexpected Exception

---

# Logging Rules

Log

✓ Timestamp

✓ Log Level

✓ Correlation ID

✓ Request Path

✓ HTTP Method

✓ Response Status

✓ Processing Time

Never log

✗ Passwords

✗ JWT Tokens

✗ Secrets

✗ API Keys

✗ Sensitive Personal Data

---

# Correlation ID

Every incoming request receives a unique Correlation ID.

Flow

```text
Client

↓

Gateway

↓

Correlation ID

↓

Logs

↓

Metrics

↓

Response
```

This enables tracking a request across the entire Gateway.

---

# Log Levels

ERROR

Unexpected failures

---

WARN

Recoverable problems

---

INFO

Normal application events

---

DEBUG

Development diagnostics

DEBUG logging must never be enabled in production by default.

---

# Pillar 2 — Metrics

The Gateway exposes operational metrics.

Examples

Gateway

- Requests per Second
- Requests per Minute
- Success Rate
- Error Rate
- Average Latency
- Peak Latency

Authentication

- Login Attempts
- Failed Authentication
- Authorization Failures

Rate Limiter

- Allowed Requests
- Blocked Requests
- Algorithm Distribution

Redis

- Latency
- Connection Status
- Command Count

JVM

- Heap Memory
- CPU Usage
- Thread Count

---

# Metrics Collection

Version 1

Spring Boot Actuator

↓

Micrometer

↓

Dashboard

Future

Micrometer

↓

Prometheus

↓

Grafana

---

# Pillar 3 — Health Checks

Every deployable component exposes a health endpoint.

Examples

```
/actuator/health
```

Health includes

- Application
- Redis
- Memory
- Disk Space
- Dependencies

---

# Health States

UP

↓

DEGRADED

↓

DOWN

Health status should accurately represent service readiness.

---

# Dashboard Integration

The React Dashboard consumes

- Metrics APIs
- Health APIs

The Dashboard never accesses Redis directly.

---

# Alternatives Considered

## Alternative 1

Logging Only

Advantages

- Simple

Disadvantages

- Difficult performance analysis
- Poor operational visibility

Decision

Rejected.

---

## Alternative 2

Metrics Only

Advantages

- Quantitative insights

Disadvantages

- Difficult root-cause analysis

Decision

Rejected.

---

## Alternative 3

External Monitoring Only

Advantages

- Less application code

Disadvantages

- Poor learning value
- Reduced control
- Incomplete visibility

Decision

Rejected.

---

# Decision Drivers

Operational Visibility

★★★★★

---

Debuggability

★★★★★

---

Production Readiness

★★★★★

---

Maintainability

★★★★★

---

Learning Value

★★★★★

---

# Responsibilities

Gateway

Owns

- Log generation
- Metric publication
- Health reporting

Dashboard

Owns

- Visualization

Prometheus (Future)

Owns

- Metric storage

Grafana (Future)

Owns

- Dashboards

---

# Performance Considerations

Observability should introduce minimal overhead.

Guidelines

- Avoid excessive logging
- Prefer counters over expensive calculations
- Reuse metric objects
- Sample high-volume metrics if required

Observability must never become the primary bottleneck.

---

# Failure Handling

If metrics collection fails

↓

Gateway continues serving requests.

If logging fails

↓

Gateway continues serving requests.

Observability failures must not stop request processing.

---

# Security Considerations

Health endpoints intended for administrators should be protected in production.

Sensitive operational information should not be publicly exposed.

Development environments may expose additional diagnostics.

---

# Future Evolution

Future enhancements

- Prometheus
- Grafana
- OpenTelemetry
- Jaeger
- Zipkin
- Distributed Tracing
- Alerting
- SLA Dashboards
- SLO Monitoring

The architecture should accommodate these additions without redesign.

---

# Implementation Impact

This ADR directly affects

- Logging Module
- Metrics Module
- Dashboard
- Spring Boot Actuator
- Micrometer
- Deployment
- Docker Compose

---

# Related Documents

- Architecture.md
- Engineering_spec.md
- Development_Playbook.md
- AI_Guide.md
- Engineering_review_checklist.md

---

# Related ADRs

- ADR-0011 — Docker Compose
- ADR-0012 — React Dashboard
- ADR-0013 — Comprehensive Testing Strategy

---

# Success Criteria

This decision is successful if

- Every request can be traced through logs.
- Operational metrics are exposed.
- Health endpoints accurately represent service status.
- Dashboard visualizes system behaviour.
- Observability does not significantly impact performance.
- The architecture supports future Prometheus and Grafana integration.

---

# Interview Questions

- What is Observability?
- What are the three pillars of observability?
- Why are metrics insufficient without logs?
- Why use Correlation IDs?
- What should never be logged?
- What is Spring Boot Actuator?
- What is Micrometer?
- Why expose health endpoints?
- How would you integrate Prometheus?
- How would you implement distributed tracing?

---

# Final Decision

**Accepted**

The Distributed API Gateway will adopt a **three-pillar observability strategy** consisting of **Structured Logging, Metrics, and Health Checks**.

This approach provides production-grade operational visibility, improves debugging, enables performance analysis, and establishes a strong foundation for future integrations with Prometheus, Grafana, and distributed tracing systems while keeping Version 1 focused and maintainable.