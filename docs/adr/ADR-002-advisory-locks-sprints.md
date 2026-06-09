# ADR-002: PostgreSQL Advisory Locks for Sprint Operations

**Date:** 2026-06-09  
**Status:** Accepted

## Context

The business rule "only one sprint can be ACTIVE per project at a time" must be enforced under concurrent requests. Two simultaneous `POST /sprints/{id}/start` calls could both pass an application-level `if (activeSprint != null) throw` check before either transaction commits, resulting in two active sprints.

## Decision

Use PostgreSQL **transaction-level advisory locks** (`pg_try_advisory_xact_lock`) to serialise sprint state transitions per project.

```sql
SELECT pg_try_advisory_xact_lock(:lockKey)
```

- `lockKey` = `Math.abs(projectId.getMostSignificantBits())` — a stable 64-bit key per project.
- The lock is acquired at the start of the transaction and released automatically on commit or rollback.
- `pg_try_advisory_xact_lock` is **non-blocking**: returns `false` immediately if another transaction holds the lock, rather than waiting. We fail fast with a 409 error.

## Consequences

**Good:**
- DB-level serialisation — no race condition possible.
- Non-blocking: concurrent request gets an immediate failure, not a hang.
- Zero extra infrastructure — uses PostgreSQL's built-in lock manager.
- Lock is automatically released on transaction end — no manual cleanup.

**Bad:**
- Ties the locking strategy to PostgreSQL. If we switch to a different DB, we need a different mechanism (e.g., Redis `SETNX` distributed lock).
- Lock key collisions theoretically possible (different projects could hash to the same long value), though negligible in practice.

## Alternatives Considered

**`SELECT FOR UPDATE` on the sprint row:** Only works when a row to lock already exists. Doesn't work for the "start first sprint" case where there is no active sprint row yet.

**Application-level `synchronized` block:** Only works on a single JVM. Fails immediately in a multi-node deployment.

**Redis `SETNX` distributed lock:** Works across nodes, but adds Redis as a dependency for a correctness concern (not just a performance concern). Advisory locks are simpler because they reuse the existing DB connection.
