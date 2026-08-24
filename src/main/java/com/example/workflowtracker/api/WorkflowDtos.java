package com.example.workflowtracker.api;

import com.example.workflowtracker.domain.WorkflowStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class WorkflowDtos {
    private WorkflowDtos() { }

    public record CreateWorkflowRequest(
            @NotBlank @Size(max = 100) String eventId,
            JsonNode payload,
            @NotEmpty @Size(max = 100) List<@NotBlank @Size(max = 100) String> targetServices) { }

    public record AcknowledgeWorkflowRequest(
            @NotBlank @Size(max = 100) String serviceName) { }

    public record FailWorkflowRequest(@Size(max = 500) String reason) { }

    public record AcknowledgementResponse(
            String serviceName,
            boolean acknowledged,
            Instant acknowledgedAt) { }

    public record WorkflowResponse(
            UUID workflowId,
            String eventId,
            JsonNode payload,
            WorkflowStatus status,
            String failureReason,
            Instant createdAt,
            Instant updatedAt,
            Instant ackDeadline,
            List<AcknowledgementResponse> acknowledgements,
            List<String> acknowledgedServices,
            List<String> pendingServices) { }

    public record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            String requestId) { }
}
