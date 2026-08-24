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

}
