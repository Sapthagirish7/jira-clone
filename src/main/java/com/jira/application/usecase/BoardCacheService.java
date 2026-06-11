package com.jira.application.usecase;

import com.jira.api.v1.dto.response.BoardResponse;
import com.jira.api.v1.dto.response.IssueResponse;
import com.jira.domain.model.StatusCategory;
import com.jira.infrastructure.persistence.entity.WorkflowStatusEntity;
import com.jira.infrastructure.persistence.repository.IssueJpaRepository;
import com.jira.infrastructure.persistence.repository.WorkflowStatusJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * BoardCacheService wraps the board read query with Redis cache-aside.
 *
 * On cache miss: queries PostgreSQL and stores the result in Redis with a TTL.
 * On cache hit: returns from Redis directly with no DB query.
 * On any write: evicts the cache for that project so the next read gets fresh data.
 *
 * TTL is set to 5 minutes in RedisConfig. Real-time accuracy for connected clients
 * is provided by WebSocket broadcasts regardless of cache state.
 */
@Service
@RequiredArgsConstructor
public class BoardCacheService {

    private final IssueJpaRepository          issueRepo;
    private final WorkflowStatusJpaRepository statusRepo;

    @Cacheable(value = "board", key = "#projectId")
    @Transactional(readOnly = true)
    public BoardResponse getBoard(UUID projectId) {
        List<WorkflowStatusEntity> columns = statusRepo.findByProjectIdOrderByPosition(projectId);
        var issues = issueRepo.findBoardIssues(projectId);

        Map<UUID, List<IssueResponse>> byStatus = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getStatus().getId(),
                        Collectors.mapping(IssueResponse::from, Collectors.toList())
                ));

        List<BoardResponse.Column> cols = columns.stream()
                .map(s -> new BoardResponse.Column(
                        s.getId(), s.getName(), s.getCategory(),
                        byStatus.getOrDefault(s.getId(), List.of())))
                .toList();

        return new BoardResponse(projectId, cols);
    }

    @CacheEvict(value = "board", key = "#projectId")
    public void evict(UUID projectId) {
        // Eviction only — no body needed
    }
}
