# ADR-001: Hexagonal Architecture (Ports & Adapters)

**Date:** 2026-06-09  
**Status:** Accepted

## Context

The assignment requires "clean hexagonal / ports-and-adapters architecture with clear separation of domain logic, application services, and infrastructure." The main risk in a standard Spring layered architecture is that business logic leaks into JPA entities or REST controllers, making it hard to test or replace infrastructure components.

## Decision

Organise code into four packages with strict dependency rules:

```
domain/        ← no Spring, no JPA, no HTTP imports
application/   ← Spring @Service; calls domain + infrastructure interfaces
infrastructure/← Spring @Repository, JPA entities, Redis, WebSocket adapters
api/           ← Spring @RestController; DTOs, request/response mapping
```

Dependency rule: `api → application → domain`. Infrastructure implements interfaces defined by the application layer. Domain knows nothing about any framework.

## Consequences

**Good:**
- `WorkflowEngine` is a plain Java class — unit-testable with no Spring context.
- Swapping PostgreSQL for another DB only touches `infrastructure/persistence/`.
- Domain events in `domain/event/` have no framework dependency.

**Bad:**
- More packages and interfaces than a simple layered architecture.
- Slight overhead for small features that don't need the isolation.

## Alternatives Considered

**Standard Spring layered (Controller → Service → Repository):** Simpler but business logic tends to leak into service methods that import JPA directly, making the domain untestable in isolation. Rejected because the assignment explicitly asks for hexagonal.
