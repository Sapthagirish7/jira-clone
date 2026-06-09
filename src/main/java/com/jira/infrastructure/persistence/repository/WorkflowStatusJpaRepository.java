package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.WorkflowStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkflowStatusJpaRepository extends JpaRepository<WorkflowStatusEntity, UUID> {

    List<WorkflowStatusEntity> findByProjectIdOrderByPosition(UUID projectId);

    boolean existsByProjectIdAndName(UUID projectId, String name);
}
