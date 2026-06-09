package com.jira.domain.event;

import java.util.UUID;

public class IssueCreatedEvent extends DomainEvent {
    private final UUID issueId;
    private final UUID projectId;
    private final UUID actorId;
    private final String issueKey;

    public IssueCreatedEvent(UUID issueId, UUID projectId, UUID actorId,
                             String issueKey, String correlationId) {
        super(correlationId);
        this.issueId = issueId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.issueKey = issueKey;
    }

    public UUID getIssueId()    { return issueId; }
    public UUID getProjectId()  { return projectId; }
    public UUID getActorId()    { return actorId; }
    public String getIssueKey() { return issueKey; }
}
