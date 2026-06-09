package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.ProjectEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectJpaRepository extends JpaRepository<ProjectEntity, UUID> {

    Optional<ProjectEntity> findByKey(String key);

    // Row-level security: only return projects where the user is a member
    @Query("""
           SELECT p FROM ProjectEntity p
           JOIN ProjectMemberEntity m ON m.projectId = p.id
           WHERE m.userId = :userId
           """)
    List<ProjectEntity> findByMemberId(@Param("userId") UUID userId);

    boolean existsByKey(String key);
}
