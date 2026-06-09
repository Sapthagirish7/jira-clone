package com.jira.api.v1.dto.response;

import com.jira.infrastructure.persistence.entity.ActivityLogEntity;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ActivityResponse {
    private UUID id;
    private UUID projectId;
    private UUID issueId;
    private UUID actorId;
    private String eventType;
    private String oldValue;
    private String newValue;
    private String correlationId;
    private Instant occurredAt;

    public static ActivityResponse from(ActivityLogEntity e) {
        return ActivityResponse.builder()
                .id(e.getId())
                .projectId(e.getProjectId())
                .issueId(e.getIssueId())
                .actorId(e.getActorId())
                .eventType(e.getEventType())
                .oldValue(e.getOldValue())
                .newValue(e.getNewValue())
                .correlationId(e.getCorrelationId())
                .occurredAt(e.getOccurredAt())
                .build();
    }
}
