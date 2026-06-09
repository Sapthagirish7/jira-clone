package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.SprintEntity;
import com.jira.domain.model.SprintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SprintJpaRepository extends JpaRepository<SprintEntity, UUID> {

    List<SprintEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<SprintEntity> findByProjectIdAndStatus(UUID projectId, SprintStatus status);

    // Advisory lock query — acquires a PostgreSQL session-level advisory lock
    // on the project to prevent concurrent sprint start/complete operations.
    // The lock is released automatically when the transaction ends.
    @Query(value = "SELECT pg_try_advisory_xact_lock(:lockKey)", nativeQuery = true)
    boolean tryAcquireAdvisoryLock(@Param("lockKey") long lockKey);
}
