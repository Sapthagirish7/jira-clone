package com.jira.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jira.domain.event.*;
import com.jira.infrastructure.persistence.entity.ActivityLogEntity;
import com.jira.infrastructure.persistence.repository.ActivityLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Listens to domain events and writes immutable rows to activity_log.
 *
 * INTERVIEW TALKING POINT — Why @EventListener instead of direct calls?
 * IssueService doesn't import ActivityLogService. This means:
 * 1. No circular dependencies.
 * 2. Adding a new side effect (e.g. Slack notification) = add a new @EventListener.
 *    Zero changes to IssueService.
 * 3. Can be made async (@Async) so the activity write doesn't block the HTTP response.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityLogListener {

    private final ActivityLogJpaRepository activityLogRepo;
    private final ObjectMapper objectMapper;

    @EventListener
    @Async
    public void onIssueCreated(IssueCreatedEvent event) {
        save(event.getProjectId(), event.getIssueId(), event.getActorId(),
             "ISSUE_CREATED",
             null,
             Map.of("issueKey", event.getIssueKey()),
             event.getCorrelationId());
    }

    @EventListener
    @Async
    public void onStatusChanged(StatusChangedEvent event) {
        save(event.getProjectId(), event.getIssueId(), event.getActorId(),
             "STATUS_CHANGED",
             Map.of("status", event.getFromStatus()),
             Map.of("status", event.getToStatus()),
             event.getCorrelationId());
    }

    @EventListener
    @Async
    public void onIssueUpdated(IssueUpdatedEvent event) {
        save(event.getProjectId(), event.getIssueId(), event.getActorId(),
             "ISSUE_UPDATED",
             Map.of(event.getFieldChanged(), String.valueOf(event.getOldValue())),
             Map.of(event.getFieldChanged(), String.valueOf(event.getNewValue())),
             event.getCorrelationId());
    }

    @EventListener
    @Async
    public void onCommentAdded(CommentAddedEvent event) {
        save(event.getProjectId(), event.getIssueId(), event.getActorId(),
             "COMMENT_ADDED",
             null,
             Map.of("commentId", event.getCommentId().toString()),
             event.getCorrelationId());
    }

    private void save(java.util.UUID projectId, java.util.UUID issueId, java.util.UUID actorId,
                      String eventType, Object oldVal, Object newVal, String correlationId) {
        try {
            ActivityLogEntity entry = ActivityLogEntity.builder()
                    .projectId(projectId)
                    .issueId(issueId)
                    .actorId(actorId)
                    .eventType(eventType)
                    .oldValue(oldVal != null ? objectMapper.writeValueAsString(oldVal) : null)
                    .newValue(newVal != null ? objectMapper.writeValueAsString(newVal) : null)
                    .correlationId(correlationId)
                    .build();
            activityLogRepo.save(entry);
        } catch (Exception ex) {
            log.error("Failed to write activity log for event {}", eventType, ex);
        }
    }
}
