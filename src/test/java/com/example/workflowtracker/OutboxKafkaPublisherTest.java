package com.example.workflowtracker;

import com.example.workflowtracker.metrics.WorkflowMetrics;
import com.example.workflowtracker.outbox.OutboxClaim;
import com.example.workflowtracker.outbox.WorkflowLifecycleEvent;
import com.example.workflowtracker.service.OutboxClaimService;
import com.example.workflowtracker.service.OutboxDeliveryService;
import com.example.workflowtracker.service.OutboxKafkaPublisher;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxKafkaPublisherTest {

    private static final String TOPIC = "workflow.lifecycle.v1";
    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");

    @Test
    void marksEventPublishedAfterKafkaConfirmsTheSend() throws Exception {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxDeliveryService deliveryService = mock(OutboxDeliveryService.class);
        KafkaTemplate<String, WorkflowLifecycleEvent> kafkaTemplate = mock(KafkaTemplate.class);
        WorkflowMetrics metrics = mock(WorkflowMetrics.class);
        OutboxClaim claim = claim(1);
        SendResult<String, WorkflowLifecycleEvent> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);

        when(claimService.claimBatch()).thenReturn(List.of(claim));
        when(kafkaTemplate.send(eq(TOPIC), eq(claim.aggregateId().toString()), any()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn(TOPIC);
        when(deliveryService.markPublished(claim)).thenReturn(true);

        publisher(claimService, deliveryService, kafkaTemplate, metrics, 10).publishAvailable();

        verify(deliveryService).markPublished(claim);
        verify(metrics).recordOutboxPublished();
    }

    @Test
    void schedulesRetryWhenKafkaRejectsTheSend() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxDeliveryService deliveryService = mock(OutboxDeliveryService.class);
        KafkaTemplate<String, WorkflowLifecycleEvent> kafkaTemplate = mock(KafkaTemplate.class);
        WorkflowMetrics metrics = mock(WorkflowMetrics.class);
        OutboxClaim claim = claim(1);

        when(claimService.claimBatch()).thenReturn(List.of(claim));
        when(kafkaTemplate.send(eq(TOPIC), eq(claim.aggregateId().toString()), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));
        when(deliveryService.scheduleRetry(eq(claim), any(), eq("Kafka unavailable"))).thenReturn(true);

        publisher(claimService, deliveryService, kafkaTemplate, metrics, 10).publishAvailable();

        verify(deliveryService).scheduleRetry(eq(claim), any(), eq("Kafka unavailable"));
        verify(metrics).recordOutboxPublishFailed();
    }

    @Test
    void movesEventToDeadLetterAfterTheConfiguredAttemptLimit() {
        OutboxClaimService claimService = mock(OutboxClaimService.class);
        OutboxDeliveryService deliveryService = mock(OutboxDeliveryService.class);
        KafkaTemplate<String, WorkflowLifecycleEvent> kafkaTemplate = mock(KafkaTemplate.class);
        WorkflowMetrics metrics = mock(WorkflowMetrics.class);
        OutboxClaim claim = claim(3);

        when(claimService.claimBatch()).thenReturn(List.of(claim));
        when(kafkaTemplate.send(eq(TOPIC), eq(claim.aggregateId().toString()), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));
        when(deliveryService.markDeadLetter(claim, "Kafka unavailable")).thenReturn(true);

        publisher(claimService, deliveryService, kafkaTemplate, metrics, 3).publishAvailable();

        verify(deliveryService).markDeadLetter(claim, "Kafka unavailable");
        verify(metrics).recordOutboxDeadLettered();
    }

    private OutboxKafkaPublisher publisher(
            OutboxClaimService claimService,
            OutboxDeliveryService deliveryService,
            KafkaTemplate<String, WorkflowLifecycleEvent> kafkaTemplate,
            WorkflowMetrics metrics,
            int maxAttempts) {
        return new OutboxKafkaPublisher(
                claimService,
                deliveryService,
                kafkaTemplate,
                metrics,
                Clock.fixed(NOW, ZoneOffset.UTC),
                TOPIC,
                maxAttempts,
                Duration.ofSeconds(5),
                Duration.ofMinutes(5),
                Duration.ofSeconds(5));
    }

    private OutboxClaim claim(int attemptCount) {
        return new OutboxClaim(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "WORKFLOW_COMPLETED",
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode(),
                NOW,
                attemptCount,
                UUID.randomUUID());
    }
}
