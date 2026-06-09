package com.jira.api.v1.dto.request;

import com.jira.domain.model.Priority;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class UpdateIssueRequest {
    private String title;
    private String description;
    private Priority priority;
    private UUID assigneeId;
    private Integer storyPoints;
    private List<String> labels;
}
