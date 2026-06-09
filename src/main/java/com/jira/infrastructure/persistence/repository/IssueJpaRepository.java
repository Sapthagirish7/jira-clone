package com.jira.infrastructure.persistence.repository;

import com.jira.infrastructure.persistence.entity.IssueEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueJpaRepository extends JpaRepository<IssueEntity, UUID> {

    Optional<IssueEntity> findByIssueKey(String issueKey);

    // Board query: all issues for a project, grouped by the caller (service layer)
    // JOIN FETCH avoids N+1 when rendering the board (status + assignee loaded in one query)
    @Query("""
           SELECT i FROM IssueEntity i
           JOIN FETCH i.status s
           LEFT JOIN FETCH i.assignee
           WHERE i.project.id = :projectId
           ORDER BY s.position, i.createdAt
           """)
    List<IssueEntity> findBoardIssues(@Param("projectId") UUID projectId);

    // Backlog: issues not assigned to any sprint
    @Query("""
           SELECT i FROM IssueEntity i
           JOIN FETCH i.status
           LEFT JOIN FETCH i.assignee
           WHERE i.project.id = :projectId AND i.sprint IS NULL
           ORDER BY i.priority, i.createdAt
           """)
    List<IssueEntity> findBacklog(@Param("projectId") UUID projectId);

    // Sprint board
    @Query("""
           SELECT i FROM IssueEntity i
           JOIN FETCH i.status s
           LEFT JOIN FETCH i.assignee
           WHERE i.sprint.id = :sprintId
           ORDER BY s.position, i.createdAt
           """)
    List<IssueEntity> findBySprintId(@Param("sprintId") UUID sprintId);

    // Full-text search using PostgreSQL tsvector @@ tsquery
    @Query(value = """
           SELECT * FROM issues
           WHERE project_id = :projectId
             AND search_vector @@ plainto_tsquery('english', :query)
           ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC
           """, nativeQuery = true)
    Page<IssueEntity> fullTextSearch(@Param("projectId") UUID projectId,
                                     @Param("query") String query,
                                     Pageable pageable);

    // Children of a parent issue (Epic's stories, Story's subtasks)
    List<IssueEntity> findByParentId(UUID parentId);

    // Count incomplete issues in a sprint (for velocity + carry-over)
    @Query("""
           SELECT COUNT(i) FROM IssueEntity i
           JOIN i.status s
           WHERE i.sprint.id = :sprintId AND s.category <> 'DONE'
           """)
    long countIncompleteInSprint(@Param("sprintId") UUID sprintId);

    // Sum of story points for completed issues (sprint velocity calculation)
    @Query("""
           SELECT COALESCE(SUM(i.storyPoints), 0) FROM IssueEntity i
           JOIN i.status s
           WHERE i.sprint.id = :sprintId AND s.category = 'DONE'
           """)
    int sumCompletedStoryPoints(@Param("sprintId") UUID sprintId);

    boolean existsByIssueKey(String issueKey);

    long countByProjectId(UUID projectId);
}
