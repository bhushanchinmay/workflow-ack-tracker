package com.example.workflowtracker.outbox;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record OutboxClaim(
        UUID id,
        UUID aggregateId,
        String eventType,
        JsonNode payload,
        Instant createdAt,
        int attemptCount,
        UUID claimToken) {

    public static OutboxClaim from(OutboxEvent event) {
        return new OutboxClaim(
                event.getId(),
                event.getAggregateId(),
                event.getEventType(),
                event.getPayload(),
                event.getCreatedAt(),
                event.getAttemptCount(),
                event.getClaimToken());
    }
}
