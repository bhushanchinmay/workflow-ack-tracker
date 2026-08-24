package com.example.workflowtracker.api;

import com.example.workflowtracker.exception.WorkflowExceptions.WorkflowConflictException;
import com.example.workflowtracker.exception.WorkflowExceptions.WorkflowNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.stream.Collectors;

import static com.example.workflowtracker.api.WorkflowDtos.ErrorResponse;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(WorkflowNotFoundException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> notFound(
            WorkflowNotFoundException ex, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "WORKFLOW_NOT_FOUND", ex.getMessage(), request);
    }

    @ExceptionHandler(WorkflowConflictException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> conflict(
            WorkflowConflictException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "INVALID_WORKFLOW_STATE", ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> validation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public org.springframework.http.ResponseEntity<ErrorResponse> badRequest(
            Exception ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Request body or path is invalid", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> typeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Path parameter has an invalid format", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> dataConflict(
            DataIntegrityViolationException ex, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "Request conflicts with existing data", request);
    }

    @ExceptionHandler(Exception.class)
    public org.springframework.http.ResponseEntity<ErrorResponse> unexpected(
            Exception ex, HttpServletRequest request) {
        log.error("Unexpected error while processing requestId={} path={}",
                requestId(request), request.getRequestURI(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "The server could not process the request", request);
    }

    private org.springframework.http.ResponseEntity<ErrorResponse> error(
            HttpStatus status, String error, String message, HttpServletRequest request) {
        return org.springframework.http.ResponseEntity.status(status)
                .body(new ErrorResponse(Instant.now(), status.value(), error, message,
                        request.getRequestURI(), requestId(request)));
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        return requestId == null ? null : requestId.toString();
    }
}
