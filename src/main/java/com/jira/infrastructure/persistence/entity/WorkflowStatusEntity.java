package com.jira.infrastructure.persistence.entity;

import com.jira.domain.model.StatusCategory;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "workflow_statuses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowStatusEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusCategory category;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
