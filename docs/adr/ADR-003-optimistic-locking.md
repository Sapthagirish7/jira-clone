# ADR-003: Optimistic Locking for Issue Updates

**Date:** 2026-06-09  
**Status:** Accepted

## Context

Multiple users can edit the same issue concurrently (change assignee, priority, description). Without conflict detection, the last write silently overwrites all previous writes — a "lost update" problem. The assignment explicitly requires "optimistic locking for issue updates (version field + conflict detection)."

## Decision

Add a `version INT` column to the `issues` table managed by JPA's `@Version` annotation.

On every `UPDATE`:
```sql
UPDATE issues SET title=?, priority=?, ..., version=version+1
WHERE id=? AND version=?   -- version the client last read
```

If `WHERE` matches 0 rows (the version has already been incremented by another request), JPA throws `OptimisticLockingFailureException`. The `GlobalExceptionHandler` maps this to **HTTP 409 Conflict** with the body:

```json
{
  "type": "https://jira.local/errors/optimistic-lock-conflict",
  "status": 409,
  "detail": "The resource was modified by another request. Fetch the latest version and retry.",
  "hint": "Re-fetch the issue and merge your changes before retrying."
}
```

## Consequences

**Good:**
- No row-level locks held during the user's think time (time between reading and writing).
- Scales well: no lock contention under normal read-heavy load.
- Conflict detection is exact — the DB enforces it, not the application.
- Version number is visible in API responses — clients know exactly what version they have.

**Bad:**
- Requires client-side retry logic on 409.
- Under very high write contention (many users editing the same issue simultaneously), retry storms are possible. Mitigated by exponential backoff in clients.

## Alternatives Considered

**Pessimistic locking (`SELECT FOR UPDATE`):** Holds a row lock from read to write. Serialises all writers of a single issue, degrading throughput. Appropriate when conflicts are expected to be frequent — not the case for project management where most users edit different issues.

**Last-write-wins (no conflict detection):** Simple but silently loses user changes. Unacceptable for a collaboration tool.
