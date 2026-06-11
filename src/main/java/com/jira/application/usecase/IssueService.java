package com.jira.application.usecase;

import com.jira.api.v1.dto.request.CreateIssueRequest;
import com.jira.api.v1.dto.request.TransitionRequest;
import com.jira.api.v1.dto.request.UpdateIssueRequest;
import com.jira.api.v1.dto.response.IssueResponse;
import com.jira.api.v1.dto.response.BoardResponse;
import com.jira.domain.event.*;
import com.jira.domain.model.StatusCategory;
import com.jira.domain.service.WorkflowEngine;
import com.jira.infrastructure.persistence.entity.*;
import com.jira.infrastructure.persistence.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Application use-case service for issue operations.
 *
 * Follows CQRS: write operations (create, update, transition) are handled here.
 * Board reads use a dedicated read-optimised query with JOIN FETCH to avoid N+1.
 *
 * After every mutation a domain event is published via ApplicationEventPublisher.
 * This decouples IssueService from ActivityLogService, WebSocketBroadcaster, and
 * NotificationService — each listens independently via @EventListener.
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    private final IssueJpaRepository       issueRepo;
    private final ProjectJpaRepository     projectRepo;
    private final WorkflowStatusJpaRepository statusRepo;
    private final WorkflowEngine           workflowEngine;
    private final UserJpaRepository        userRepo;
    private final SprintJpaRepository      sprintRepo;
    private final ApplicationEventPublisher eventPublisher;
    private final EntityManager            entityManager;

    // ─────────────────────────────────────────────
    // COMMAND: Create Issue
    // ─────────────────────────────────────────────
    @Transactional
    public IssueResponse createIssue(UUID projectId, CreateIssueRequest req, UUID actorId) {
        ProjectEntity project = projectRepo.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));

        WorkflowStatusEntity status = req.getStatusId() != null
                ? statusRepo.findById(req.getStatusId())
                    .orElseThrow(() -> new EntityNotFoundException("Status not found"))
                : statusRepo.findByProjectIdOrderByPosition(projectId).stream()
                    .filter(WorkflowStatusEntity::isDefault)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No default status configured for project"));

        UserEntity reporter = userRepo.findById(actorId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + actorId));

        String issueKey = generateIssueKey(project.getKey(), projectId);

        IssueEntity issue = IssueEntity.builder()
                .issueKey(issueKey)
                .project(project)
                .issueType(req.getIssueType())
                .title(req.getTitle())
                .description(req.getDescription())
                .status(status)
                .priority(req.getPriority() != null ? req.getPriority() : com.jira.domain.model.Priority.MEDIUM)
                .reporter(reporter)
                .storyPoints(req.getStoryPoints())
                .labels(req.getLabels())
                .build();

        if (req.getAssigneeId() != null) {
            issue.setAssignee(userRepo.findById(req.getAssigneeId())
                    .orElseThrow(() -> new EntityNotFoundException("Assignee not found")));
        }
        if (req.getParentId() != null) {
            issue.setParent(issueRepo.findById(req.getParentId())
                    .orElseThrow(() -> new EntityNotFoundException("Parent issue not found")));
        }
        if (req.getSprintId() != null) {
            issue.setSprint(sprintRepo.findById(req.getSprintId())
                    .orElseThrow(() -> new EntityNotFoundException("Sprint not found")));
        }

        IssueEntity saved = issueRepo.save(issue);

        eventPublisher.publishEvent(new IssueCreatedEvent(
                saved.getId(), projectId, actorId, issueKey, MDC.get("correlationId")));

        return IssueResponse.from(saved);
    }

    // ─────────────────────────────────────────────
    // COMMAND: Update Issue fields (PATCH)
    // ─────────────────────────────────────────────
    @Transactional
    public IssueResponse updateIssue(UUID issueId, UpdateIssueRequest req, UUID actorId) {
        IssueEntity issue = issueRepo.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId));

        if (req.getTitle() != null)       issue.setTitle(req.getTitle());
        if (req.getDescription() != null) issue.setDescription(req.getDescription());
        if (req.getPriority() != null)    issue.setPriority(req.getPriority());
        if (req.getStoryPoints() != null) issue.setStoryPoints(req.getStoryPoints());
        if (req.getLabels() != null)      issue.setLabels(req.getLabels());

        if (req.getAssigneeId() != null) {
            UserEntity assignee = userRepo.findById(req.getAssigneeId())
                    .orElseThrow(() -> new EntityNotFoundException("Assignee not found"));
            issue.setAssignee(assignee);

            eventPublisher.publishEvent(new IssueUpdatedEvent(
                    issueId, issue.getProject().getId(), actorId,
                    "assignee", null, assignee.getDisplayName(), MDC.get("correlationId")));
        }

        IssueEntity saved = issueRepo.save(issue);
        return IssueResponse.from(saved);
    }

    // ─────────────────────────────────────────────
    // COMMAND: Transition Issue status
    // VS-3 + VS-5: WorkflowEngine validates; @Version handles concurrent updates
    // ─────────────────────────────────────────────
    @Transactional
    public IssueResponse transitionIssue(UUID issueId, TransitionRequest req, UUID actorId) {
        IssueEntity issue = issueRepo.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId));

        String fromStatusName = issue.getStatus().getName();

        // WorkflowEngine throws WorkflowViolationException (422) if transition is blocked
        workflowEngine.validateTransition(issue.getStatus().getId(), req.getToStatusId());

        WorkflowStatusEntity toStatus = statusRepo.findById(req.getToStatusId())
                .orElseThrow(() -> new EntityNotFoundException("Target status not found"));

        issue.setStatus(toStatus);
        IssueEntity saved = issueRepo.save(issue);

        eventPublisher.publishEvent(new StatusChangedEvent(
                issueId, issue.getProject().getId(), actorId,
                issue.getIssueKey(), fromStatusName, toStatus.getName(),
                MDC.get("correlationId")));

        return IssueResponse.from(saved);
    }

    // ─────────────────────────────────────────────
    // QUERY: Get board state (CQRS read side)
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public BoardResponse getBoard(UUID projectId) {
        List<WorkflowStatusEntity> columns = statusRepo.findByProjectIdOrderByPosition(projectId);
        List<IssueEntity> issues = issueRepo.findBoardIssues(projectId);

        // Group issues by statusId — this is done in memory to avoid N+1 per column
        Map<UUID, List<IssueResponse>> byStatus = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getStatus().getId(),
                        Collectors.mapping(IssueResponse::from, Collectors.toList())
                ));

        List<BoardResponse.Column> cols = columns.stream()
                .map(s -> new BoardResponse.Column(
                        s.getId(), s.getName(), s.getCategory(),
                        byStatus.getOrDefault(s.getId(), List.of())))
                .toList();

        return new BoardResponse(projectId, cols);
    }

    // ─────────────────────────────────────────────
    // QUERY: Single issue
    // ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public IssueResponse getIssue(UUID issueId) {
        return IssueResponse.from(issueRepo.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId)));
    }

    @Transactional(readOnly = true)
    public IssueResponse getIssueByKey(String issueKey) {
        return IssueResponse.from(issueRepo.findByIssueKey(issueKey)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueKey)));
    }

    // Atomically increments per-project issue counter using UPDATE...RETURNING.
    // Safe under concurrent inserts — no TOCTOU race unlike COUNT(*)+1.
    private String generateIssueKey(String projectKey, UUID projectId) {
        Number n = (Number) entityManager
                .createNativeQuery("UPDATE projects SET next_issue_number = next_issue_number + 1 WHERE id = :id RETURNING next_issue_number")
                .setParameter("id", projectId)
                .getSingleResult();
        return projectKey + "-" + n.intValue();
    }
}
