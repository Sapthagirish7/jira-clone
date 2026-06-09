package com.jira.api.v1.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CreateSprintRequest {
    @NotBlank
    private String name;
    private String goal;
    private LocalDate startDate;
    private LocalDate endDate;
}
