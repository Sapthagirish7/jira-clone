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
 * INTERVIEW TALKING POINT — Cache-aside pattern:
 * 1. On cache miss: query PostgreSQL, store result in Redis with a TTL.
 * 2. On cache hit: return from Redis directly — no DB query.
 * 3. On any write (issue created/updated/transitioned): evict the cache for
 *    that project so the next board read gets fresh data.
 *
 * Why not write-through? Because board state is an aggregate of many rows.
 * Reconstructing it on every write is expensive; invalidating and lazily
 * reloading on next read is cheaper for read-heavy boards.
 *
 * INTERVIEW TALKING POINT — TTL:
 * Set to 5 minutes in RedisConfig. A stale board for up to 5 minutes is
 * acceptable for most team views. Real-time updates come via WebSocket anyway;
 * the cache is for users who open a board after an idle period.
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
