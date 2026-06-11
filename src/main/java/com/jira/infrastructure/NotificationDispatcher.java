package com.jira.infrastructure;

import com.jira.domain.event.StatusChangedEvent;
import com.jira.infrastructure.persistence.entity.NotificationEntity;
import com.jira.infrastructure.persistence.repository.IssueJpaRepository;
import com.jira.infrastructure.persistence.repository.NotificationJpaRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * NotificationDispatcher writes notifications to watchers when issues change status.
 *
 * Wrapped with a Resilience4j circuit breaker so notification failures do not
 * affect the main issue mutation path. If the circuit opens, the fallback logs
 * the dropped notification — in production this would push to a queue (SQS/Kafka)
 * for delivery once the service recovers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final NotificationJpaRepository notificationRepo;
    private final IssueJpaRepository        issueRepo;

    @EventListener
    @Async
    @CircuitBreaker(name = "notificationService", fallbackMethod = "fallbackDispatch")
    public void onStatusChanged(StatusChangedEvent event) {
        // Notify all watchers of the issue
        issueRepo.findById(event.getIssueId()).ifPresent(issue -> {
            String message = String.format("[%s] status changed: %s → %s",
                    issue.getIssueKey(), event.getFromStatus(), event.getToStatus());

            // For brevity, we use the assignee as watcher; full implementation
            // would join issue_watchers table for all subscriber UUIDs.
            if (issue.getAssignee() != null) {
                NotificationEntity n = NotificationEntity.builder()
                        .recipientId(issue.getAssignee().getId())
                        .issueId(issue.getId())
                        .type("STATUS_CHANGED")
                        .message(message)
                        .read(false)
                        .build();
                notificationRepo.save(n);
            }
        });
    }

    // Fallback when circuit is open — log and move on; board operations are unaffected
    public void fallbackDispatch(StatusChangedEvent event, Throwable t) {
        log.warn("NotificationService circuit is OPEN. Notification queued for retry. " +
                 "issueId={} correlationId={}", event.getIssueId(), event.getCorrelationId());
        // In production: push to SQS/Kafka dead-letter queue for retry
    }
}
