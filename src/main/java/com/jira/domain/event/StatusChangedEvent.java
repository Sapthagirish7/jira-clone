package com.jira.domain.event;

import java.util.UUID;

public class StatusChangedEvent extends DomainEvent {
    private final UUID issueId;
    private final UUID projectId;
    private final UUID actorId;
    private final String issueKey;
    private final String fromStatus;
    private final String toStatus;

    public StatusChangedEvent(UUID issueId, UUID projectId, UUID actorId,
                              String issueKey, String fromStatus, String toStatus,
                              String correlationId) {
        super(correlationId);
        this.issueId = issueId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.issueKey = issueKey;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public UUID getIssueId()    { return issueId; }
    public UUID getProjectId()  { return projectId; }
    public UUID getActorId()    { return actorId; }
    public String getIssueKey() { return issueKey; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus()   { return toStatus; }
}
