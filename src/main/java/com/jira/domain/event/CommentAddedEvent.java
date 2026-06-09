package com.jira.domain.event;

import java.util.UUID;

public class CommentAddedEvent extends DomainEvent {
    private final UUID commentId;
    private final UUID issueId;
    private final UUID projectId;
    private final UUID actorId;

    public CommentAddedEvent(UUID commentId, UUID issueId, UUID projectId,
                             UUID actorId, String correlationId) {
        super(correlationId);
        this.commentId = commentId;
        this.issueId = issueId;
        this.projectId = projectId;
        this.actorId = actorId;
    }

    public UUID getCommentId()  { return commentId; }
    public UUID getIssueId()    { return issueId; }
    public UUID getProjectId()  { return projectId; }
    public UUID getActorId()    { return actorId; }
}
