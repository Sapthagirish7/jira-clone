package com.jira.api.v1.controller;

import com.jira.api.v1.dto.request.AddCommentRequest;
import com.jira.api.v1.dto.response.CommentResponse;
import com.jira.application.usecase.CommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/issues/{issueId}/comments")
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Threaded comment management")
public class CommentController {

    private final CommentService commentService;
    private final com.jira.infrastructure.persistence.repository.UserJpaRepository userRepo;

    @GetMapping
    @Operation(summary = "List all comments for an issue (threaded)")
    public List<CommentResponse> listComments(@PathVariable UUID issueId) {
        return commentService.listComments(issueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a comment; supports @mentions and threaded replies")
    public CommentResponse addComment(@PathVariable UUID issueId,
                                      @Valid @RequestBody AddCommentRequest req,
                                      @AuthenticationPrincipal UserDetails principal) {
        UUID actorId = userRepo.findByUsername(principal.getUsername()).get().getId();
        return commentService.addComment(issueId, req.getBody(), req.getParentCommentId(), actorId);
    }
}
