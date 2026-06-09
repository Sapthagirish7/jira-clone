package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.ProjectMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberJpaRepository
        extends JpaRepository<ProjectMemberEntity, ProjectMemberEntity.ProjectMemberId> {

    Optional<ProjectMemberEntity> findByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);
}
