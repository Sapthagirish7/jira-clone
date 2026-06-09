# ADR-004: Domain Events for Decoupled Side Effects

**Date:** 2026-06-09  
**Status:** Accepted

## Context

Every issue mutation must trigger multiple side effects: write an activity log entry, broadcast a WebSocket message, send notifications to watchers. The naive approach — `IssueService` directly calling `ActivityLogService`, `WebSocketBroadcaster`, and `NotificationService` — creates tight coupling and a fragile call chain where a notification failure can roll back an issue save.

## Decision

Use Spring's `ApplicationEventPublisher` to publish domain events after each mutation. Independent `@EventListener` beans react to these events asynchronously.

```
IssueService publishes StatusChangedEvent
    ├── ActivityLogListener  @EventListener @Async → writes activity_log
    ├── WebSocketBroadcaster @EventListener @Async → STOMP broadcast
    └── NotificationDispatcher @EventListener @Async @CircuitBreaker → saves notification
```

Domain events are defined in `domain/event/` with no Spring imports. `ApplicationEventPublisher` is injected only into the application layer.

## Consequences

**Good:**
- `IssueService` has zero imports from `ActivityLogService`, `WebSocketBroadcaster`, or `NotificationDispatcher`.
- Adding a new side effect (e.g. Slack integration) = one new `@EventListener` class. Zero changes to `IssueService`.
- `@Async` listeners don't block the HTTP response thread.
- `NotificationDispatcher` circuit breaker means notification failures never roll back issue saves.

**Bad:**
- Debugging requires tracing through event listeners rather than a linear call stack.
- `@Async` means side effects run after the HTTP response is already sent — eventual consistency for the activity log and notifications.
- Transactional boundary: if the `@Async` listener throws after the main transaction commits, the event is not retried (no outbox pattern). For production, use a transactional outbox to guarantee delivery.

## Alternatives Considered

**Direct method calls:** Simple to follow, but tightly couples `IssueService` to all side-effect services. A failure in notification delivery would require try-catch in the issue save path. Adding a new integration requires modifying `IssueService`.

**Kafka/message broker:** Guarantees delivery and enables microservice extraction. Adds significant infrastructure complexity. Appropriate at larger scale; overkill for this prototype. The current design makes it easy to migrate to Kafka later — replace `ApplicationEventPublisher` with a Kafka producer, listeners become consumers.
