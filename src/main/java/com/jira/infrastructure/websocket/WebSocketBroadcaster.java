package com.jira.infrastructure.websocket;

import com.jira.domain.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * WebSocketBroadcaster listens to domain events and pushes them to connected
 * STOMP clients via per-project topic channels.
 *
 * Clients subscribe to /topic/projects/{projectId}/board and receive live updates
 * when any issue in that project changes. Topic-per-project ensures fan-out only
 * reaches viewers of the affected project.
 *
 * On reconnect, clients fetch the full board state via GET /api/v1/projects/{id}/board
 * and then resume receiving incremental WebSocket updates from that point.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    @Async
    public void onIssueCreated(IssueCreatedEvent event) {
        broadcast(event.getProjectId().toString(), Map.of(
                "type", "issue_created",
                "issueId", event.getIssueId(),
                "issueKey", event.getIssueKey()
        ));
    }

    @EventListener
    @Async
    public void onStatusChanged(StatusChangedEvent event) {
        broadcast(event.getProjectId().toString(), Map.of(
                "type", "issue_moved",
                "issueId", event.getIssueId(),
                "issueKey", event.getIssueKey(),
                "fromStatus", event.getFromStatus(),
                "toStatus", event.getToStatus()
        ));
    }

    @EventListener
    @Async
    public void onIssueUpdated(IssueUpdatedEvent event) {
        broadcast(event.getProjectId().toString(), Map.of(
                "type", "issue_updated",
                "issueId", event.getIssueId(),
                "field", event.getFieldChanged(),
                "newValue", String.valueOf(event.getNewValue())
        ));
    }

    @EventListener
    @Async
    public void onCommentAdded(CommentAddedEvent event) {
        broadcast(event.getProjectId().toString(), Map.of(
                "type", "comment_added",
                "issueId", event.getIssueId(),
                "commentId", event.getCommentId()
        ));
    }

    private void broadcast(String projectId, Map<String, Object> payload) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/projects/" + projectId + "/board", payload);
        } catch (Exception ex) {
            log.warn("WebSocket broadcast failed for project {}", projectId, ex);
        }
    }
}
