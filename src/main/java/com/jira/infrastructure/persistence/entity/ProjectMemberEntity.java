package com.jira.infrastructure.persistence.entity;

import com.jira.domain.model.ProjectRole;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_members")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(ProjectMemberEntity.ProjectMemberId.class)
public class ProjectMemberEntity {

    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProjectRole role;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectMemberId implements Serializable {
        private UUID projectId;
        private UUID userId;
    }
}
