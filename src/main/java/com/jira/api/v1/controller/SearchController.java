package com.jira.api.v1.controller;

import com.jira.api.v1.dto.response.ActivityResponse;
import com.jira.api.v1.dto.response.IssueResponse;
import com.jira.api.v1.dto.response.PagedResponse;
import com.jira.infrastructure.persistence.repository.ActivityLogJpaRepository;
import com.jira.infrastructure.persistence.repository.IssueJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Search & Activity", description = "Search issues and project activity feed")
public class SearchController {

    private final IssueJpaRepository    issueRepo;
    private final ActivityLogJpaRepository activityRepo;

    /**
     * Full-text search using PostgreSQL tsvector + GIN index.
     * Results are ranked by ts_rank. Supports offset-based pagination.
     */
    @GetMapping("/search")
    @Operation(summary = "Full-text search across issues within a project")
    public PagedResponse<IssueResponse> search(
            @RequestParam UUID projectId,
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size);
        var result = issueRepo.fullTextSearch(projectId, q, pageable);

        return new PagedResponse<>(
                result.getContent().stream().map(IssueResponse::from).toList(),
                page, size,
                result.getTotalElements(),
                result.hasNext()
        );
    }

    @GetMapping("/projects/{projectId}/activity")
    @Operation(summary = "Paginated activity feed for a project")
    public PagedResponse<ActivityResponse> activityFeed(
            @PathVariable UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size);
        var result = activityRepo.findByProjectIdOrderByOccurredAtDesc(projectId, pageable);

        return new PagedResponse<>(
                result.getContent().stream().map(ActivityResponse::from).toList(),
                page, size,
                result.getTotalElements(),
                result.hasNext()
        );
    }
}
