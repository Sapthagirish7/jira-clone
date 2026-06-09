package com.jira.infrastructure.persistence.entity;

import com.jira.domain.model.ProjectRole;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "workflow_transitions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowTransitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_status_id", nullable = false)
    private WorkflowStatusEntity fromStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_status_id", nullable = false)
    private WorkflowStatusEntity toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "requires_role")
    private ProjectRole requiresRole;
}
