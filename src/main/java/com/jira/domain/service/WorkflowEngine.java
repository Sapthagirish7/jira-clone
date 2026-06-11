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
 * Each project defines a directed graph of statuses and allowed transitions stored
 * in the workflow_transitions table. This service validates that a requested
 * status change follows an allowed edge in that graph, throwing
 * WorkflowViolationException if the transition is not permitted.
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
