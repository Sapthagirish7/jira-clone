package com.jira.domain.service;

import com.jira.infrastructure.persistence.entity.WorkflowTransitionEntity;
import com.jira.infrastructure.persistence.repository.WorkflowTransitionJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * WorkflowEngine enforces the configured transition graph for each project.
 *
 * INTERVIEW TALKING POINT — State Machine pattern:
 * Each project has a directed graph of statuses and allowed transitions.
 * "To Do -> In Progress -> In Review -> Done" is one valid path.
 * Jumping directly from "To Do" to "Done" is blocked unless that transition
 * exists in workflow_transitions. This is the State Machine pattern applied
 * to a configurable, data-driven workflow instead of hard-coded states.
 *
 * This service is the single place in the system that knows transition rules.
 * IssueService calls it; it returns either the valid transition or throws.
 */
@Service
@RequiredArgsConstructor
public class WorkflowEngine {

    private final WorkflowTransitionJpaRepository transitionRepo;

    /**
     * Validates that the transition from fromStatusId to toStatusId is allowed.
     * @throws WorkflowViolationException (422) if the transition is not configured.
     */
    public WorkflowTransitionEntity validateTransition(UUID fromStatusId, UUID toStatusId) {
        return transitionRepo.findByFromStatusIdAndToStatusId(fromStatusId, toStatusId)
                .orElseThrow(() -> {
                    List<String> allowed = getAllowedTargetNames(fromStatusId);
                    return new WorkflowViolationException(
                            "Transition not allowed. Allowed next statuses: " + allowed);
                });
    }

    public List<WorkflowTransitionEntity> getAllowedTransitions(UUID fromStatusId) {
        return transitionRepo.findAllowedTransitions(fromStatusId);
    }

    private List<String> getAllowedTargetNames(UUID fromStatusId) {
        return transitionRepo.findAllowedTransitions(fromStatusId)
                .stream()
                .map(t -> t.getToStatus().getName())
                .toList();
    }
}
