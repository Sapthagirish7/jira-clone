package com.jira.api.v1.dto.response;

import com.jira.domain.model.SprintStatus;
import com.jira.infrastructure.persistence.entity.SprintEntity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
public class SprintResponse {
    private UUID id;
    private UUID projectId;
    private String name;
    private String goal;
    private SprintStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private int velocity;
    private Instant createdAt;

    public static SprintResponse from(SprintEntity e) {
        return SprintResponse.builder()
                .id(e.getId())
                .projectId(e.getProject().getId())
                .name(e.getName())
                .goal(e.getGoal())
                .status(e.getStatus())
                .startDate(e.getStartDate())
                .endDate(e.getEndDate())
                .velocity(e.getVelocity())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
