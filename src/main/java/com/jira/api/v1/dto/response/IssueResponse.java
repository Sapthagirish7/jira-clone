package com.jira.api.v1.dto.response;

import com.jira.domain.model.IssueType;
import com.jira.domain.model.Priority;
import com.jira.infrastructure.persistence.entity.IssueEntity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class IssueResponse {

    private UUID id;
    private String issueKey;
    private UUID projectId;
    private IssueType issueType;
    private String title;
    private String description;
    private String status;
    private UUID statusId;
    private Priority priority;
    private AssigneeRef assignee;
    private AssigneeRef reporter;
    private UUID parentId;
    private UUID sprintId;
    private Integer storyPoints;
    private List<String> labels;
    private int version;
    private Instant createdAt;
    private Instant updatedAt;

    public static IssueResponse from(IssueEntity e) {
        return IssueResponse.builder()
                .id(e.getId())
                .issueKey(e.getIssueKey())
                .projectId(e.getProject().getId())
                .issueType(e.getIssueType())
                .title(e.getTitle())
                .description(e.getDescription())
                .status(e.getStatus().getName())
                .statusId(e.getStatus().getId())
                .priority(e.getPriority())
                .assignee(e.getAssignee() != null
                        ? new AssigneeRef(e.getAssignee().getId(), e.getAssignee().getDisplayName())
                        : null)
                .reporter(new AssigneeRef(e.getReporter().getId(), e.getReporter().getDisplayName()))
                .parentId(e.getParent() != null ? e.getParent().getId() : null)
                .sprintId(e.getSprint() != null ? e.getSprint().getId() : null)
                .storyPoints(e.getStoryPoints())
                .labels(e.getLabels())
                .version(e.getVersion())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    public record AssigneeRef(UUID id, String displayName) {}
}
