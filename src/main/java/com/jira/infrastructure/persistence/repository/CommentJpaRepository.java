package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.CommentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CommentJpaRepository extends JpaRepository<CommentEntity, UUID> {
    List<CommentEntity> findByIssueIdOrderByCreatedAtAsc(UUID issueId);
}
