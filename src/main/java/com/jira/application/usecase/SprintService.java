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
 * SprintService manages sprint lifecycle: create, start, complete.
 *
 * Enforces the invariant that only one sprint can be ACTIVE per project at a time.
 * A PostgreSQL advisory lock (pg_try_advisory_xact_lock) serialises concurrent
 * start/complete requests per project — application-level checks alone are not
 * sufficient under concurrent requests.
 *
 * On sprint completion, incomplete issues are optionally moved to a target sprint
 * or returned to the backlog (sprint_id = NULL). Story points of DONE issues
 * are summed to calculate velocity.
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
