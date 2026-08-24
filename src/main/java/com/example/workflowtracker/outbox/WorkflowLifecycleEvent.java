package com.example.workflowtracker.outbox;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record WorkflowLifecycleEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        UUID workflowId,
        String source,
        Instant occurredAt,
        JsonNode payload) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String SOURCE = "workflow-tracker";

    public static WorkflowLifecycleEvent from(OutboxClaim claim) {
        return new WorkflowLifecycleEvent(
                claim.id(),
                claim.eventType(),
                CURRENT_SCHEMA_VERSION,
                claim.aggregateId(),
                SOURCE,
                claim.createdAt(),
                claim.payload());
    }

}
