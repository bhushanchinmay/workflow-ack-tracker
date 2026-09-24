package com.example.workflowtracker;

import com.example.workflowtracker.domain.Workflow;
import com.example.workflowtracker.domain.WorkflowStatus;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowDomainTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void completesOnlyAfterEveryExpectedServiceAcknowledges() {
        Workflow workflow = new Workflow(
                "order-1001",
                JsonNodeFactory.instance.objectNode().put("orderId", "1001"),
                CREATED_AT,
                CREATED_AT.plusSeconds(900),
                List.of("billing", "shipping"));

        workflow.markAcknowledged("billing", CREATED_AT.plusSeconds(10));
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.PENDING);

        workflow.markAcknowledged("shipping", CREATED_AT.plusSeconds(20));
        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(workflow.getAcknowledgements())
                .allMatch(acknowledgement -> acknowledgement.getAcknowledgedAt() != null);
    }

    @Test
    void marksAnUnresolvedWorkflowAsFailed() {
        Workflow workflow = new Workflow(
                "order-1002",
                JsonNodeFactory.instance.objectNode(),
                CREATED_AT,
                CREATED_AT.plusSeconds(900),
                List.of("billing"));

        workflow.markFailed("deadline exceeded", CREATED_AT.plusSeconds(901));

        assertThat(workflow.getStatus()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(workflow.getFailureReason()).isEqualTo("deadline exceeded");
    }
}
