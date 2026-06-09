package com.jira.api.v1.controller;

import com.jira.api.v1.dto.request.CompleteSprintRequest;
import com.jira.api.v1.dto.request.CreateSprintRequest;
import com.jira.api.v1.dto.response.IssueResponse;
import com.jira.api.v1.dto.response.SprintResponse;
import com.jira.application.usecase.SprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Sprints", description = "Sprint lifecycle management")
public class SprintController {

    private final SprintService sprintService;

    @GetMapping("/projects/{projectId}/sprints")
    @Operation(summary = "List all sprints for a project")
    public List<SprintResponse> listSprints(@PathVariable UUID projectId) {
        return sprintService.listSprints(projectId);
    }

    @PostMapping("/projects/{projectId}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new sprint")
    public SprintResponse createSprint(@PathVariable UUID projectId,
                                       @Valid @RequestBody CreateSprintRequest req) {
        return sprintService.createSprint(projectId, req);
    }

    @PostMapping("/sprints/{sprintId}/start")
    @Operation(summary = "Start a sprint — acquires advisory lock; fails if another sprint is active")
    public SprintResponse startSprint(@PathVariable UUID sprintId,
                                      @RequestParam UUID projectId) {
        return sprintService.startSprint(sprintId, projectId);
    }

    @PostMapping("/sprints/{sprintId}/complete")
    @Operation(summary = "Complete a sprint with selective carry-over of incomplete issues")
    public SprintResponse completeSprint(@PathVariable UUID sprintId,
                                         @RequestParam UUID projectId,
                                         @RequestBody CompleteSprintRequest req) {
        return sprintService.completeSprint(sprintId, projectId, req);
    }

    @GetMapping("/sprints/{sprintId}/incomplete-issues")
    @Operation(summary = "Preview incomplete issues before completing a sprint")
    public List<IssueResponse> previewIncomplete(@PathVariable UUID sprintId) {
        return sprintService.getIncompleteIssues(sprintId)
                .stream().map(IssueResponse::from).toList();
    }
}
