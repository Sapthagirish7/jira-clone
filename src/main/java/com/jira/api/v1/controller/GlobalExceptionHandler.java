package com.jira.api.v1.controller;

import com.jira.application.usecase.EntityNotFoundException;
import com.jira.domain.service.WorkflowViolationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

/**
 * Centralised error handling following RFC 9457 (Problem Details for HTTP APIs).
 *
 * Each exception type maps to a specific HTTP status and a machine-readable type URI.
 * A correlationId links every error response back to the corresponding server log entry.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        return problem(HttpStatus.NOT_FOUND, "not-found", ex.getMessage());
    }

    @ExceptionHandler(WorkflowViolationException.class)
    public ProblemDetail handleWorkflowViolation(WorkflowViolationException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "workflow-violation", ex.getMessage());
    }

    // Optimistic lock conflict — Scenario 1 in the assignment
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "optimistic-lock-conflict",
                "The resource was modified by another request. Fetch the latest version and retry.");
        pd.setProperty("hint", "Re-fetch the issue and merge your changes before retrying.");
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "validation-error", "Request validation failed");
        pd.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList());
        return pd;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String type, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("https://jira.local/errors/" + type));
        pd.setProperty("correlationId", MDC.get("correlationId"));
        return pd;
    }
}
