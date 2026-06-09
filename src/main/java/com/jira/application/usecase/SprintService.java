package com.jira.application.usecase;

import com.jira.api.v1.dto.request.CompleteSprintRequest;
import com.jira.api.v1.dto.request.CreateSprintRequest;
import com.jira.api.v1.dto.response.SprintResponse;
import com.jira.domain.model.SprintStatus;
import com.jira.infrastructure.persistence.entity.IssueEntity;
import com.jira.infrastructure.persistence.entity.SprintEntity;
import com.jira.infrastructure.persistence.repository.IssueJpaRepository;
import com.jira.infrastructure.persistence.repository.ProjectJpaRepository;
import com.jira.infrastructure.persistence.repository.SprintJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * SprintService manages sprint lifecycle.
 *
 * INTERVIEW TALKING POINT — Advisory Locks:
 * "Only one sprint can be ACTIVE per project at a time" is a business invariant.
 * Checking this in application code is NOT sufficient — two concurrent requests
 * could both pass the check before either commits.
 *
 * Solution: PostgreSQL pg_try_advisory_xact_lock(lockKey).
 * - The lockKey is derived from the projectId (converted to a long).
 * - The lock is held for the duration of the transaction and released on commit/rollback.
 * - If another request holds the lock, pg_try_advisory_xact_lock returns false immediately
 *   (non-blocking) rather than blocking indefinitely — we fail fast with 409.
 *
 * This is different from row-level locking (SELECT FOR UPDATE) because there is no
 * existing "active sprint" row to lock when starting the first sprint.
 *
 * INTERVIEW TALKING POINT — Sprint completion carry-over:
 * On completion we sum story_points of DONE issues → velocity.
 * Incomplete issues can be selectively moved to a target sprint or left in backlog (sprint_id = NULL).
 * The audit trail records this as a sprint-level event.
 */
@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintJpaRepository    sprintRepo;
    private final ProjectJpaRepository   projectRepo;
    private final IssueJpaRepository     issueRepo;

    @Transactional(readOnly = true)
    public List<SprintResponse> listSprints(UUID projectId) {
        return sprintRepo.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream().map(SprintResponse::from).toList();
    }

    @Transactional
    public SprintResponse createSprint(UUID projectId, CreateSprintRequest req) {
        var project = projectRepo.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found: " + projectId));

        SprintEntity sprint = SprintEntity.builder()
                .project(project)
                .name(req.getName())
                .goal(req.getGoal())
                .status(SprintStatus.PLANNED)
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .velocity(0)
                .build();

        return SprintResponse.from(sprintRepo.save(sprint));
    }

    @Transactional
    public SprintResponse startSprint(UUID sprintId, UUID projectId) {
        acquireAdvisoryLockOrThrow(projectId);

        sprintRepo.findByProjectIdAndStatus(projectId, SprintStatus.ACTIVE)
                .ifPresent(active -> {
                    throw new IllegalStateException(
                            "Sprint '" + active.getName() + "' is already active. Complete it first.");
                });

        SprintEntity sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new EntityNotFoundException("Sprint not found: " + sprintId));

        if (sprint.getStatus() != SprintStatus.PLANNED) {
            throw new IllegalStateException("Only PLANNED sprints can be started.");
        }

        sprint.setStatus(SprintStatus.ACTIVE);
        return SprintResponse.from(sprintRepo.save(sprint));
    }

    @Transactional
    public SprintResponse completeSprint(UUID sprintId, UUID projectId, CompleteSprintRequest req) {
        acquireAdvisoryLockOrThrow(projectId);

        SprintEntity sprint = sprintRepo.findById(sprintId)
                .orElseThrow(() -> new EntityNotFoundException("Sprint not found: " + sprintId));

        if (sprint.getStatus() != SprintStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE sprints can be completed.");
        }

        // Calculate velocity: sum of story points for completed issues
        int velocity = issueRepo.sumCompletedStoryPoints(sprintId);
        sprint.setVelocity(velocity);
        sprint.setStatus(SprintStatus.COMPLETED);

        // Handle carry-over of incomplete issues
        List<IssueEntity> incomplete = issueRepo.findBySprintId(sprintId).stream()
                .filter(i -> !"DONE".equals(i.getStatus().getCategory().name()))
                .toList();

        SprintEntity targetSprint = req.getCarryOverToSprintId() != null
                ? sprintRepo.findById(req.getCarryOverToSprintId())
                    .orElseThrow(() -> new EntityNotFoundException("Carry-over sprint not found"))
                : null;

        List<UUID> carryOverIds = req.getIssueIdsToCarryOver() != null
                ? req.getIssueIdsToCarryOver()
                : List.of();

        for (IssueEntity issue : incomplete) {
            if (carryOverIds.contains(issue.getId())) {
                issue.setSprint(targetSprint);  // null = backlog if no target sprint given
            } else {
                issue.setSprint(null);           // unscheduled incomplete issues go to backlog
            }
            issueRepo.save(issue);
        }

        return SprintResponse.from(sprintRepo.save(sprint));
    }

    @Transactional(readOnly = true)
    public List<IssueEntity> getIncompleteIssues(UUID sprintId) {
        return issueRepo.findBySprintId(sprintId).stream()
                .filter(i -> !"DONE".equals(i.getStatus().getCategory().name()))
                .toList();
    }

    private void acquireAdvisoryLockOrThrow(UUID projectId) {
        long lockKey = Math.abs(projectId.getMostSignificantBits());
        boolean acquired = sprintRepo.tryAcquireAdvisoryLock(lockKey);
        if (!acquired) {
            throw new IllegalStateException(
                    "Another sprint operation is in progress for this project. Try again shortly.");
        }
    }
}
