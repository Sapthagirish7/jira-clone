package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    Page<NotificationEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    @Modifying
    @Query("UPDATE NotificationEntity n SET n.read = true WHERE n.recipientId = :userId AND n.read = false")
    int markAllRead(@Param("userId") UUID userId);
}
