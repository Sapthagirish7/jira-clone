package com.jira.api.v1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class AddCommentRequest {
    @NotBlank
    private String body;
    private UUID parentCommentId;
}
