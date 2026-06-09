package com.jira.infrastructure.persistence.entity;

import com.jira.domain.model.IssueType;
import com.jira.domain.model.Priority;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the issues table.
 *
 * KEY DESIGN DECISIONS to explain in interviews:
 *
 * 1. version field: used for optimistic locking (@Version).
 *    When two users update the same issue concurrently, JPA checks
 *    UPDATE issues SET ... WHERE id=? AND version=?
 *    If the version has changed (someone else saved first), 0 rows are affected
 *    and JPA throws OptimisticLockException -> we map this to HTTP 409.
 *
 * 2. parent_id self-reference: models Epic->Story->SubTask hierarchy in one table
 *    (adjacency list pattern). Simple to query for direct children; for deep trees
 *    a closure table or LTREE extension would be better.
 *
 * 3. sprint_id nullable: NULL = backlog. This avoids a separate backlog table.
 *
 * 4. labels as TEXT[]: PostgreSQL native array. Fast for @> (contains) queries
 *    without a join table for simple string tags.
 */
@Entity
@Table(name = "issues")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IssueEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "issue_key", nullable = false, unique = true)
    private String issueKey;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_type", nullable = false)
    private IssueType issueType;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_id", nullable = false)
    private WorkflowStatusEntity status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private UserEntity assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private UserEntity reporter;

    // Self-referencing FK for Epic->Story->SubTask adjacency list
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private IssueEntity parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private SprintEntity sprint;

    @Column(name = "story_points")
    private Integer storyPoints;

    @Type(ListArrayType.class)
    @Column(name = "labels", columnDefinition = "text[]")
    private List<String> labels;

    // Optimistic locking — JPA increments this on every UPDATE
    @Version
    @Column(nullable = false)
    private int version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
