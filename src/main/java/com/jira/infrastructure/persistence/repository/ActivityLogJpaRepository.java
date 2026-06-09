package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.ActivityLogEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ActivityLogJpaRepository extends JpaRepository<ActivityLogEntity, UUID> {

    Page<ActivityLogEntity> findByProjectIdOrderByOccurredAtDesc(UUID projectId, Pageable pageable);

    Page<ActivityLogEntity> findByIssueIdOrderByOccurredAtDesc(UUID issueId, Pageable pageable);
}
