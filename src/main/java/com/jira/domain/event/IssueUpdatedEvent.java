package com.jira.domain.event;

import java.util.UUID;

public class IssueUpdatedEvent extends DomainEvent {
    private final UUID issueId;
    private final UUID projectId;
    private final UUID actorId;
    private final String fieldChanged;
    private final String oldValue;
    private final String newValue;

    public IssueUpdatedEvent(UUID issueId, UUID projectId, UUID actorId,
                             String fieldChanged, String oldValue, String newValue,
                             String correlationId) {
        super(correlationId);
        this.issueId = issueId;
        this.projectId = projectId;
        this.actorId = actorId;
        this.fieldChanged = fieldChanged;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getIssueId()      { return issueId; }
    public UUID getProjectId()    { return projectId; }
    public UUID getActorId()      { return actorId; }
    public String getFieldChanged() { return fieldChanged; }
    public String getOldValue()   { return oldValue; }
    public String getNewValue()   { return newValue; }
}
