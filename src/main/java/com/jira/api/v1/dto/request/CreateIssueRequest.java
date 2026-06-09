package com.jira.api.v1.dto.request;

import com.jira.domain.model.IssueType;
import com.jira.domain.model.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateIssueRequest {

    @NotNull
    private IssueType issueType;

    @NotBlank
    private String title;

    private String description;
    private Priority priority;
    private UUID assigneeId;
    private UUID parentId;
    private UUID sprintId;
    private UUID statusId;
    private Integer storyPoints;
    private List<String> labels;
}
