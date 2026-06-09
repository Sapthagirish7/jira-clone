package com.jira.api.v1.controller;

import com.jira.api.v1.dto.request.CreateIssueRequest;
import com.jira.api.v1.dto.request.TransitionRequest;
import com.jira.api.v1.dto.request.UpdateIssueRequest;
import com.jira.api.v1.dto.response.BoardResponse;
import com.jira.api.v1.dto.response.IssueResponse;
import com.jira.application.usecase.IssueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Issues", description = "Issue tracking and board management")
public class IssueController {

    private final IssueService issueService;
    private final com.jira.infrastructure.persistence.repository.UserJpaRepository userRepo;

    @PostMapping("/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new issue in a project")
    public IssueResponse createIssue(@PathVariable UUID projectId,
                                     @Valid @RequestBody CreateIssueRequest req,
                                     @AuthenticationPrincipal UserDetails principal) {
        UUID actorId = resolveUserId(principal);
        return issueService.createIssue(projectId, req, actorId);
    }

    @GetMapping("/projects/{projectId}/board")
    @Operation(summary = "Get board state — all issues grouped by status column")
    public BoardResponse getBoard(@PathVariable UUID projectId) {
        return issueService.getBoard(projectId);
    }

    @GetMapping("/issues/{issueId}")
    @Operation(summary = "Get a single issue by ID")
    public IssueResponse getIssue(@PathVariable UUID issueId) {
        return issueService.getIssue(issueId);
    }

    @GetMapping("/issues/by-key/{issueKey}")
    @Operation(summary = "Get a single issue by key (e.g. PROJ-42)")
    public IssueResponse getIssueByKey(@PathVariable String issueKey) {
        return issueService.getIssueByKey(issueKey);
    }

    @PatchMapping("/issues/{issueId}")
    @Operation(summary = "Partially update issue fields")
    public IssueResponse updateIssue(@PathVariable UUID issueId,
                                     @RequestBody UpdateIssueRequest req,
                                     @AuthenticationPrincipal UserDetails principal) {
        UUID actorId = resolveUserId(principal);
        return issueService.updateIssue(issueId, req, actorId);
    }

    @PostMapping("/issues/{issueId}/transitions")
    @Operation(summary = "Transition issue to a new status — enforces workflow rules (422 on violation)")
    public IssueResponse transition(@PathVariable UUID issueId,
                                    @Valid @RequestBody TransitionRequest req,
                                    @AuthenticationPrincipal UserDetails principal) {
        UUID actorId = resolveUserId(principal);
        return issueService.transitionIssue(issueId, req, actorId);
    }

    private UUID resolveUserId(UserDetails principal) {
        return userRepo.findByUsername(principal.getUsername())
                .orElseThrow(() -> new com.jira.application.usecase.EntityNotFoundException(
                        "Authenticated user not in DB"))
                .getId();
    }
}
