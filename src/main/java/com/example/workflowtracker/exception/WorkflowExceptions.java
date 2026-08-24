package com.example.workflowtracker.exception;

public final class WorkflowExceptions {
    private WorkflowExceptions() { }

    public static class WorkflowNotFoundException extends RuntimeException {
        public WorkflowNotFoundException(String message) { super(message); }
    }

    public static class WorkflowConflictException extends RuntimeException {
        public WorkflowConflictException(String message) { super(message); }
    }
}
