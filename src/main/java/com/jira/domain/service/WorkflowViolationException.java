package com.jira.domain.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class WorkflowViolationException extends RuntimeException {
    public WorkflowViolationException(String message) {
        super(message);
    }
}
