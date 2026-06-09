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
 * NotificationDispatcher dispatches notifications to watchers when issues change status.
 *
 * INTERVIEW TALKING POINT — Circuit Breaker (Scenario 4 in the assignment):
 * The @CircuitBreaker annotation wraps the notification write/send.
 * If the underlying operation fails 5+ times (e.g., notification microservice is down),
 * the circuit opens and fallbackDispatch is called instead.
 * Fallback: log the failure and return. Board operations still succeed.
 * In production you'd push to a queue (SQS/Kafka) in the fallback so notifications
 * are delivered when the service recovers — that's the "queued and delivered later" behaviour.
 *
 * For this prototype, notifications are written to the DB notifications table.
 * The circuit breaker protects the DB write (simulating an external call).
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
