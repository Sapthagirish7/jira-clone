package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.WorkflowTransitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkflowTransitionJpaRepository extends JpaRepository<WorkflowTransitionEntity, UUID> {

    // Check if a specific transition is allowed
    Optional<WorkflowTransitionEntity> findByFromStatusIdAndToStatusId(UUID fromStatusId, UUID toStatusId);

    // All transitions OUT of a given status (shown in UI as allowed next steps)
    @Query("""
           SELECT t FROM WorkflowTransitionEntity t
           JOIN FETCH t.toStatus
           WHERE t.fromStatus.id = :fromStatusId
           """)
    List<WorkflowTransitionEntity> findAllowedTransitions(@Param("fromStatusId") UUID fromStatusId);

    List<WorkflowTransitionEntity> findByProjectId(UUID projectId);
}
