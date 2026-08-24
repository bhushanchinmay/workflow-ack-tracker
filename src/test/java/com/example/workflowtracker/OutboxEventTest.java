package com.example.workflowtracker;

import com.example.workflowtracker.outbox.OutboxEvent;
import com.example.workflowtracker.outbox.OutboxStatus;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEventTest {

    @Test
    void claimsPendingEventWithAnAttemptAndLeaseToken() {
        Instant createdAt = Instant.parse("2026-08-24T10:00:00Z");
        OutboxEvent event = new OutboxEvent(
                UUID.randomUUID(),
                "WORKFLOW_COMPLETED",
                JsonNodeFactory.instance.objectNode(),
                createdAt);
        UUID claimToken = UUID.randomUUID();

        event.claim(claimToken, createdAt.plusSeconds(5));

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.CLAIMED);
        assertThat(event.getClaimToken()).isEqualTo(claimToken);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getClaimedAt()).isEqualTo(createdAt.plusSeconds(5));
    }
}
