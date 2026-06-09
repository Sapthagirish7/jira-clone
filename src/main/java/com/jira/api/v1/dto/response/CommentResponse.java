package com.jira.api.v1.dto.response;

import com.jira.infrastructure.persistence.entity.CommentEntity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CommentResponse {
    private UUID id;
    private UUID issueId;
    private UUID authorId;
    private String authorName;
    private UUID parentCommentId;
    private String body;
    private Instant createdAt;
    private Instant updatedAt;

    public static CommentResponse from(CommentEntity e) {
        return CommentResponse.builder()
                .id(e.getId())
                .issueId(e.getIssueId())
                .authorId(e.getAuthor().getId())
                .authorName(e.getAuthor().getDisplayName())
                .parentCommentId(e.getParentCommentId())
                .body(e.getBody())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
