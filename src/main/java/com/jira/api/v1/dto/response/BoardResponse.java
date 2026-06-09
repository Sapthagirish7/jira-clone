package com.jira.api.v1.dto.response;

import com.jira.domain.model.StatusCategory;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class BoardResponse {
    private UUID projectId;
    private List<Column> columns;

    @Data
    @AllArgsConstructor
    public static class Column {
        private UUID statusId;
        private String name;
        private StatusCategory category;
        private List<IssueResponse> issues;
    }
}
