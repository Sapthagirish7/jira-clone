# Architecture & Design Documentation

## 1. System Architecture

### Hexagonal Architecture (Ports & Adapters)

The codebase is split into four concentric layers:

```
domain/          ← Pure business logic. No Spring, no JPA, no HTTP.
application/     ← Use-case orchestrators. Calls domain + infrastructure ports.
infrastructure/  ← Adapters: JPA, Redis, WebSocket, Security.
api/             ← HTTP controllers. Translates requests to use-case calls.
```

**Why this matters:** The `WorkflowEngine` domain service can be unit-tested with plain JUnit — no Spring context, no database, no HTTP server required. You inject a mock `WorkflowTransitionJpaRepository` and test the state machine logic in isolation.

If we needed to swap PostgreSQL for MongoDB, only the `infrastructure/persistence/` package changes. The domain, application, and API layers are unaffected.

---

## 2. Database Schema (ERD)

```
users
  id (PK), username, email, display_name, password_hash

projects
  id (PK), key, name, description, owner_id (FK→users)

project_members
  project_id (FK→projects), user_id (FK→users), role   ← composite PK

workflow_statuses
  id (PK), project_id (FK→projects), name, category (TODO|IN_PROGRESS|DONE), position

workflow_transitions
  id (PK), project_id, from_status_id (FK→statuses), to_status_id (FK→statuses)
  ← directed graph: each row = one allowed transition

transition_actions
  id (PK), transition_id (FK→transitions), action_type, action_payload (JSONB)

sprints
  id (PK), project_id (FK→projects), name, status (PLANNED|ACTIVE|COMPLETED),
  start_date, end_date, velocity

issues
  id (PK), issue_key (UNIQUE), project_id, issue_type, title, description,
  status_id (FK→statuses), priority, assignee_id, reporter_id,
  parent_id (FK→issues) ← self-reference for Epic→Story→SubTask,
  sprint_id (FK→sprints) ← NULL = backlog,
  story_points, labels (text[]), version (optimistic lock),
  search_vector (tsvector) ← maintained by DB trigger

project_custom_fields
  id (PK), project_id, name, field_type (TEXT|NUMBER|DROPDOWN|DATE), options (JSONB)

issue_custom_fields
  id (PK), issue_id, field_id, value_text, value_num, value_date

issue_watchers
  issue_id (FK→issues), user_id (FK→users)   ← composite PK

comments
  id (PK), issue_id, author_id, parent_comment_id (FK→comments, nullable) ← threading,
  body, created_at, updated_at

comment_mentions
  comment_id (FK→comments), user_id (FK→users)   ← composite PK

activity_log  ← IMMUTABLE, append-only
  id (PK), project_id, issue_id, actor_id, event_type,
  old_value (JSONB), new_value (JSONB), correlation_id, occurred_at

notifications
  id (PK), recipient_id, issue_id, type, message, read, created_at
```

### Key schema decisions

| Decision | Rationale |
|---|---|
| `parent_id` self-reference on `issues` | Adjacency list — simple for Epic→Story→SubTask. For arbitrarily deep trees a closure table would be better. |
| `sprint_id IS NULL` = backlog | Avoids a separate backlog table; partial index `WHERE sprint_id IS NULL` keeps backlog queries fast. |
| `labels TEXT[]` | Native PostgreSQL array; `@>` operator for contains queries without a join table. |
| `search_vector TSVECTOR` | Maintained by a `BEFORE INSERT OR UPDATE` trigger; GIN index enables O(log n) full-text search. |
| `version INT` | Incremented by JPA `@Version` on every UPDATE — the foundation of optimistic locking. |
| `activity_log` append-only | No UPDATE or DELETE ever touches this table; it is the immutable audit trail. |

---

## 3. Workflow Engine (State Machine)

Each project has a **configurable directed graph** of statuses and allowed transitions stored in `workflow_statuses` and `workflow_transitions`.

```
To Do ──→ In Progress ──→ In Review ──→ Done
            │                              │
            ←──────────────────────────────┘  (reopen)
```

`WorkflowEngine.validateTransition(fromStatusId, toStatusId)`:
1. Queries `workflow_transitions` for a row matching the pair.
2. If found — transition is allowed; automatic actions fire (e.g. assign reviewer).
3. If not found — throws `WorkflowViolationException` → HTTP 422 with allowed next statuses listed.

**Why data-driven instead of hard-coded enums?**
Different projects have different workflows (a bug-tracking project may not have "In Review"). Storing the graph in the DB lets each project configure its own workflow without code changes.

---

## 4. Concurrency & Data Integrity

### Optimistic Locking (Issues)

Every `IssueEntity` has a `@Version int version` field. JPA executes:

```sql
UPDATE issues SET ..., version = version + 1
WHERE id = ? AND version = ?   -- the version the client read
```

If the `WHERE` clause matches 0 rows (someone else already incremented the version), JPA throws `OptimisticLockingFailureException`. The `GlobalExceptionHandler` maps this to **HTTP 409 Conflict** with a clear message telling the client to re-fetch and retry.

**Why optimistic instead of pessimistic?**
Issues are read far more often than written. Pessimistic locking (SELECT FOR UPDATE) would serialize all reads for a given issue. Optimistic locking only pays a cost on actual write conflicts, which are rare in normal usage.

### Advisory Locks (Sprints)

The business rule "only one sprint can be active per project at a time" cannot be safely enforced at the application layer alone — two concurrent `POST /sprints/{id}/start` requests could both pass an application-level `if (activeSprint exists)` check before either commits.

Solution: `pg_try_advisory_xact_lock(lockKey)` — a PostgreSQL session-level advisory lock scoped to a transaction.

```sql
SELECT pg_try_advisory_xact_lock(:lockKey)  -- returns true if acquired, false if held by another tx
```

- `lockKey` is derived from `projectId.getMostSignificantBits()`.
- The lock is held for the duration of the transaction and released on commit/rollback.
- Non-blocking: returns `false` immediately rather than waiting — we fail fast with a clear error.

---

## 5. Event-Driven Architecture

Every mutation publishes a `DomainEvent` via Spring's `ApplicationEventPublisher`. Three independent `@EventListener` beans react:

```
IssueService.transitionIssue()
    │
    └─ publishes StatusChangedEvent
                    │
          ┌─────────┼──────────────┐
          ▼         ▼              ▼
  ActivityLog   WebSocket     Notification
  Listener      Broadcaster   Dispatcher
  (writes DB)   (STOMP push)  (circuit breaker)
```

**Why not direct method calls?**
- Zero coupling: `IssueService` doesn't import `ActivityLogService` or `NotificationService`.
- Additive: adding Slack notifications = one new `@EventListener` class, zero changes to `IssueService`.
- Resilient: `NotificationDispatcher` has a circuit breaker. If it fails 5 times, the circuit opens. `IssueService` never sees the failure — board operations continue.

---

## 6. CQRS (Command Query Responsibility Segregation)

| Side | Class | What it does |
|---|---|---|
| **Command** | `IssueService` write methods | Create/update/transition — validates, persists, publishes event |
| **Query** | `IssueService.getBoard()` / `BoardCacheService` | Optimised `JOIN FETCH` query, returns denormalised `BoardResponse` |

The board query loads issues + status + assignee in **one SQL query** using `JOIN FETCH` to avoid N+1. The result is cached in Redis with a 5-minute TTL. Any write (create/update/transition) evicts the cache via `@CacheEvict`.

In a larger system, the read side could be a separate service reading from a materialised view or a pre-built read model. For this prototype, both live in the same process but are clearly separated by method responsibility.

---

## 7. Real-Time Sync (WebSocket)

Clients connect via STOMP over SockJS at `/ws` and subscribe to:

```
/topic/projects/{projectId}/board
```

`WebSocketBroadcaster` listens to domain events (`@EventListener @Async`) and calls `SimpMessagingTemplate.convertAndSend()`. Because it's `@Async`, the broadcast does not block the HTTP response thread.

**Missed event replay:** If a client disconnects and reconnects, it calls `GET /board` to get the current full state, then resumes receiving incremental WebSocket deltas. For stricter replay guarantees, events could be stored in Redis Streams and replayed from a `lastSeenEventId`.

**Presence tracking:** `PresenceTracker` maintains an in-memory `ConcurrentHashMap<projectId, Set<sessionId>>`. This is stateful — in a multi-node deployment it must move to Redis Pub/Sub (see ADR-005).

---

## 8. Observability

| Signal | Implementation |
|---|---|
| **Correlation ID** | `CorrelationIdFilter` injects `X-Correlation-Id` header into every request and puts it in MDC. All log lines for a request share the same ID. Error responses include `correlationId` in the Problem Detail body. |
| **Structured logging** | Logback pattern includes `[%X{correlationId}]` from MDC. |
| **Metrics** | Micrometer + Prometheus registry. Exposed at `/actuator/prometheus`. Tracks request latency percentiles, error rates, JVM stats. |
| **Health probes** | `/api/health/live` (JVM alive), `/api/health/ready` (Spring Actuator readiness with DB + Redis component checks). |
| **Circuit breaker** | Resilience4j `@CircuitBreaker` on `NotificationDispatcher`. Opens after 5 failures in a 10-call sliding window. |

---

## 9. Security

| Concern | Implementation |
|---|---|
| **Authentication** | HTTP Basic Auth (prototype). Production: replace with JWT bearer tokens. |
| **User storage** | `JiraUserDetailsService` loads users from `users` table. Passwords stored as BCrypt(12). |
| **RBAC** | `project_members.role` stores `ADMIN|PROJECT_LEAD|MEMBER|VIEWER` per user per project. `@EnableMethodSecurity` enables `@PreAuthorize` on service methods. |
| **Row-level security** | `ProjectJpaRepository.findByMemberId()` filters projects to those the user belongs to. |
| **Input validation** | `@Valid` on all request bodies. `MethodArgumentNotValidException` → 400 with field error list. |
| **Rate limiting** | Redis-backed rate limiter via Resilience4j (configured per endpoint in production). |

---

## 10. Horizontal Scaling Strategy

| Component | Stateful? | Scaling approach |
|---|---|---|
| App servers | Stateless (no session) | Add nodes behind a load balancer freely |
| PostgreSQL | Stateful | Primary + read replicas; board queries can hit replicas |
| Redis | Stateful | Redis Cluster for cache; Redis Pub/Sub for WebSocket broker relay |
| WebSocket presence | Stateful (in-memory) | Move `PresenceTracker` to Redis Sorted Set per project |
| WebSocket broker | In-memory STOMP | Replace with RabbitMQ/Redis broker relay so all nodes can broadcast |
| Flyway migrations | Run-once | One node runs migrations on startup; others skip if already applied |

**Sharding consideration:** Issues can be sharded by `project_id` — all issue, comment, activity, and sprint data for a project lives together, enabling project-level sharding with no cross-shard joins.
