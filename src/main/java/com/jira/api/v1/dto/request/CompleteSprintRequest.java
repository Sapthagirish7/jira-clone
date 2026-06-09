package com.jira.api.v1.dto.request;

import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CompleteSprintRequest {
    // Issues to carry over to a specific sprint; if null, they go to backlog
    private UUID carryOverToSprintId;
    private List<UUID> issueIdsToCarryOver;
}
