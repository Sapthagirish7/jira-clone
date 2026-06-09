# ADR-005: Redis Cache-Aside for Board State

**Date:** 2026-06-09  
**Status:** Accepted

## Context

The board view (`GET /projects/{id}/board`) is the most frequently hit read endpoint. It joins `issues`, `workflow_statuses`, `users`, and `sprints` across potentially hundreds of rows. Under 100+ concurrent board viewers, this becomes a database bottleneck.

## Decision

Cache the board response in Redis using the **cache-aside pattern** via Spring's `@Cacheable` / `@CacheEvict` annotations.

```java
@Cacheable(value = "board", key = "#projectId")   // read: miss → DB, hit → Redis
public BoardResponse getBoard(UUID projectId) { ... }

@CacheEvict(value = "board", key = "#projectId")   // write: invalidate on mutation
public void evict(UUID projectId) { ... }
```

Cache TTL: **5 minutes** (configured in `RedisConfig`).

Cache eviction is triggered by domain events — `StatusChangedEvent`, `IssueCreatedEvent`, `IssueUpdatedEvent` all call `boardCacheService.evict(projectId)`.

Real-time accuracy is provided by WebSocket — connected clients see instant updates via STOMP regardless of cache staleness.

## Consequences

**Good:**
- Board reads under cache hit require zero DB queries.
- TTL provides a safety net — even if eviction misses, stale data self-heals in 5 minutes.
- WebSocket broadcasts keep the UI accurate regardless of cache staleness.
- Redis also serves as the rate-limiting store and (in a multi-node setup) the WebSocket presence store.

**Bad:**
- A brief window of stale data for users who load the board without an open WebSocket connection.
- Cache invalidation must be called on every write path — missing one call causes a stale board.
- Adds Redis as a required infrastructure dependency.

## Alternatives Considered

**Write-through cache:** Update Redis on every write. Expensive because the board is an aggregate of many rows — reconstructing it on every issue update is more costly than invalidating and lazily reloading.

**No cache / DB read replicas only:** Simpler, but under 100+ concurrent viewers a single read replica still struggles with the complex join query. Caching eliminates the query entirely for the common case.

**Materialised view in PostgreSQL:** The DB maintains a pre-computed board view, refreshed on change. Zero application-layer complexity, but `REFRESH MATERIALIZED VIEW` takes a table lock in PostgreSQL, causing brief read unavailability. Appropriate for analytics dashboards, not real-time collaboration boards.

## Multi-Node Consideration

In a multi-node deployment:
- The Redis cache is shared — all nodes read from and evict the same key. ✅
- The WebSocket in-memory STOMP broker is **not** shared — a message published on node A is not received by clients connected to node B. Fix: replace `enableSimpleBroker()` with a RabbitMQ or Redis broker relay in `WebSocketConfig`. This is documented as a known limitation and would be the first scaling change in production.
