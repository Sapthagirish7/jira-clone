# Jira-like Project Management Platform

A production-grade backend for a collaborative project management tool — built for the SDE-2 Backend Engineer take-home assignment.

---

## Table of Contents

- [Architecture Overview](#architecture-overview)
- [Tech Stack](#tech-stack)
- [Quick Start](#quick-start)
- [API Reference](#api-reference)
- [Sample Scenarios](#sample-scenarios)
- [Design Decisions](#design-decisions)
- [Load Testing](#load-testing)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────┐
│                      api/v1/                             │
│   Controllers · DTOs · GlobalExceptionHandler            │
│   (HTTP in, HTTP out — knows nothing about DB)           │
└────────────────────┬─────────────────────────────────────┘
                     │ calls
┌────────────────────▼─────────────────────────────────────┐
│                  application/usecase/                    │
│   IssueService · SprintService · CommentService          │
│   BoardCacheService                                      │
│   (orchestrates domain + infrastructure)                 │
└──────────┬─────────────────────────┬─────────────────────┘
           │ uses                    │ publishes
┌──────────▼──────────┐   ┌──────────▼──────────────────────┐
│   domain/           │   │  Domain Events                  │
│   WorkflowEngine    │   │  IssueCreatedEvent              │
│   DomainEvent(s)    │   │  StatusChangedEvent             │
│   Enums / Models    │   │  CommentAddedEvent              │
│   (pure Java)       │   └──────────┬──────────────────────┘
└─────────────────────┘              │ @EventListener
                          ┌──────────▼──────────────────────┐
                          │  infrastructure/                │
                          │  JPA Entities + Repositories    │
                          │  ActivityLogListener            │
                          │  WebSocketBroadcaster           │
                          │  NotificationDispatcher         │
                          │  Redis config · Security        │
                          └─────────────────────────────────┘
```

**Pattern:** Hexagonal (Ports & Adapters)
- `domain/` has zero Spring imports — fully unit-testable
- `infrastructure/` adapts JPA, Redis, WebSocket to the domain
- `api/` translates HTTP ↔ application use-cases

**CQRS:** Board read (`GET /board`) uses a dedicated JOIN FETCH query to avoid N+1. All writes go through command methods that publish domain events.

**Event-Driven:** Every mutation publishes a `DomainEvent`. Three independent listeners react: activity log, WebSocket broadcast, notification dispatch — all decoupled via Spring `ApplicationEventPublisher`.

---

## Tech Stack

| Component | Choice | Reason |
|---|---|---|
| Language | Java 21 | Virtual threads, records, modern syntax |
| Framework | Spring Boot 3.2 | Full ecosystem: Security, WebSocket, Actuator |
| Database | PostgreSQL 16 | Advisory locks, `tsvector` full-text, JSONB |
| Cache | Redis 7 | Board state cache, rate limiting, presence |
| Migrations | Flyway | Schema versioned in code, runs on startup |
| WebSocket | Spring STOMP | Built-in broker, easy topic fan-out |
| Circuit Breaker | Resilience4j | `@CircuitBreaker` on notification dispatch |
| Docs | SpringDoc OpenAPI 3 | Auto-generates Swagger UI from annotations |
| Containerisation | Docker Compose | Single command to run full stack |

---

## Quick Start

### Prerequisites
- Docker Desktop (running)
- Java 21
- Maven 3.9+

### Run

```bash
# 1. Start PostgreSQL + Redis
docker-compose up -d postgres redis

# 2. Build the app
mvn package -DskipTests

# 3. Start the app
java -jar target/jira-clone-0.0.1-SNAPSHOT.jar
```

App starts on **http://localhost:8080**

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/api/health/live
- Metrics: http://localhost:8080/actuator/prometheus

### Default credentials
```
username: admin
password: admin123
```

### Run full stack via Docker

```bash
mvn package -DskipTests
docker-compose up --build
```

---

## API Reference

All endpoints require HTTP Basic Auth (`admin:admin123`) except health/swagger.

### Issues

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/projects/{projectId}/issues` | Create issue |
| `GET` | `/api/v1/projects/{projectId}/board` | Board state grouped by status column |
| `GET` | `/api/v1/issues/{issueId}` | Get issue by ID |
| `GET` | `/api/v1/issues/by-key/{issueKey}` | Get issue by key (e.g. `DEMO-1`) |
| `PATCH` | `/api/v1/issues/{issueId}` | Update issue fields |
| `POST` | `/api/v1/issues/{issueId}/transitions` | Transition status (enforces workflow rules) |

### Sprints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/projects/{projectId}/sprints` | List sprints |
| `POST` | `/api/v1/projects/{projectId}/sprints` | Create sprint |
| `POST` | `/api/v1/sprints/{sprintId}/start` | Start sprint (advisory lock) |
| `POST` | `/api/v1/sprints/{sprintId}/complete` | Complete with carry-over |
| `GET` | `/api/v1/sprints/{sprintId}/incomplete-issues` | Preview before completion |

### Comments & Activity

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/issues/{issueId}/comments` | List threaded comments |
| `POST` | `/api/v1/issues/{issueId}/comments` | Add comment (supports `@mentions`) |
| `GET` | `/api/v1/projects/{projectId}/activity` | Paginated activity feed |

### Search

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/v1/search?projectId=...&q=...` | Full-text search (PostgreSQL tsvector) |

### Observability

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/health/live` | Liveness probe |
| `GET` | `/api/health/ready` | Readiness probe |
| `GET` | `/actuator/prometheus` | Prometheus metrics |
| `GET` | `/actuator/health` | Spring health with component details |

### WebSocket

Connect via STOMP over SockJS at `ws://localhost:8080/ws`.

Subscribe to board updates:
```
SUBSCRIBE /topic/projects/{projectId}/board
```

Event payload shape:
```json
{ "type": "issue_moved", "issueId": "...", "fromStatus": "To Do", "toStatus": "In Progress" }
```

Event types: `issue_created`, `issue_updated`, `issue_moved`, `comment_added`, `sprint_updated`

---

## Sample Scenarios

### Scenario 1 — Concurrent Issue Updates (Optimistic Locking)

```bash
# Two users update the same issue simultaneously
curl -u admin:admin123 -X PATCH -H "Content-Type: application/json" \
  -d '{"priority":"CRITICAL"}' http://localhost:8080/api/v1/issues/{id} &

curl -u admin:admin123 -X PATCH -H "Content-Type: application/json" \
  -d '{"priority":"LOW"}' http://localhost:8080/api/v1/issues/{id} &
```

One succeeds (200), one gets **409 Conflict**:
```json
{
  "type": "https://jira.local/errors/optimistic-lock-conflict",
  "status": 409,
  "detail": "The resource was modified by another request. Fetch the latest version and retry."
}
```

### Scenario 2 — Sprint Completion with Carry-Over

```bash
# Preview incomplete issues
GET /api/v1/sprints/{sprintId}/incomplete-issues

# Complete sprint, carry DEMO-1 to Sprint 2, leave DEMO-3 in backlog
POST /api/v1/sprints/{sprintId}/complete?projectId={projectId}
{
  "carryOverToSprintId": "{sprint2Id}",
  "issueIdsToCarryOver": ["{demo1Id}"]
}
# Returns: status=COMPLETED, velocity=sum of story points for DONE issues
```

### Scenario 3 — Workflow Violation

```bash
# Attempt To Do -> Done directly (no such transition configured)
POST /api/v1/issues/{issueId}/transitions
{ "toStatusId": "{doneStatusId}" }
```

Returns **422 Unprocessable Entity**:
```json
{
  "type": "https://jira.local/errors/workflow-violation",
  "status": 422,
  "detail": "Transition not allowed. Allowed next statuses: [In Progress]"
}
```

### Scenario 4 — Cascading Failure Resilience

`NotificationDispatcher` is wrapped with `@CircuitBreaker(name = "notificationService")`.
After 5 consecutive failures the circuit opens. Board operations continue unaffected.
`fallbackDispatch()` logs the failure and would push to a retry queue in production.

---

## Design Decisions

See [`docs/architecture.md`](docs/architecture.md) for full rationale.

Key decisions:
- **Hexagonal architecture** — domain is framework-agnostic, testable in isolation
- **PostgreSQL advisory locks** for sprint start/complete — prevents race conditions that application-level checks cannot
- **`@Version` optimistic locking** on issues — detects concurrent edits without row locks
- **Domain events** decouple mutations from side effects (audit log, WebSocket, notifications)
- **Redis cache-aside** on board reads — 5-minute TTL, evicted on any mutation
- **PostgreSQL `tsvector`** for full-text search — sufficient for MVP, Elasticsearch for scale

---

## Load Testing

```bash
# Install k6: https://k6.io/docs/get-started/installation/
k6 run load-test.js --env BASE_URL=http://localhost:8080 --env PROJECT_ID=10000000-0000-0000-0000-000000000001
```

Tests 100 concurrent WebSocket board viewers + 20 concurrent REST mutators for 60 seconds.

Thresholds:
- p95 response time < 500ms
- Error rate < 1%
- Optimistic lock conflict rate < 10%
