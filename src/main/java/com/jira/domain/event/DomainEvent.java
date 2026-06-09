package com.jira.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all domain events.
 *
 * Domain events are the backbone of event-driven architecture here.
 * When an issue changes status, we fire StatusChangedEvent. Separate
 * listeners handle: writing to activity log, sending WebSocket broadcast,
 * sending notifications. This keeps IssueService decoupled from all of those.
 */
public abstract class DomainEvent {
    private final UUID eventId = UUID.randomUUID();
    private final Instant occurredAt = Instant.now();
    private final String correlationId;

    protected DomainEvent(String correlationId) {
        this.correlationId = correlationId;
    }

    public UUID getEventId() { return eventId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getCorrelationId() { return correlationId; }
}
