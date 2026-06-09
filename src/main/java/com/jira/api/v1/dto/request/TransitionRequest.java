package com.jira.api.v1.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TransitionRequest {
    @NotNull
    private UUID toStatusId;
}
