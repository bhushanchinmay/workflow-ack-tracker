package com.example.workflowtracker.service;

import com.example.workflowtracker.metrics.WorkflowMetrics;
import com.example.workflowtracker.outbox.OutboxClaim;
import com.example.workflowtracker.outbox.WorkflowLifecycleEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "workflow.outbox.publisher.enabled", havingValue = "true")
public class OutboxKafkaPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxKafkaPublisher.class);
    private static final int MAX_ERROR_LENGTH = 1000;

    private final OutboxClaimService claimService;
    private final OutboxDeliveryService deliveryService;
    private final KafkaTemplate<String, WorkflowLifecycleEvent> kafkaTemplate;
    private final WorkflowMetrics metrics;
    private final Clock clock;
    private final String topic;
    private final int maxAttempts;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;
    private final Duration sendTimeout;

    public OutboxKafkaPublisher(
            OutboxClaimService claimService,
            OutboxDeliveryService deliveryService,
            KafkaTemplate<String, WorkflowLifecycleEvent> kafkaTemplate,
            WorkflowMetrics metrics,
            Clock clock,
            @Value("${workflow.outbox.publisher.topic:workflow.lifecycle.v1}") String topic,
            @Value("${workflow.outbox.publisher.max-attempts:10}") int maxAttempts,
            @Value("${workflow.outbox.publisher.initial-retry-delay:PT5S}") Duration initialRetryDelay,
            @Value("${workflow.outbox.publisher.max-retry-delay:PT5M}") Duration maxRetryDelay,
            @Value("${workflow.outbox.publisher.send-timeout:PT10S}") Duration sendTimeout) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("outbox max attempts must be positive");
        }
        if (initialRetryDelay.isNegative() || initialRetryDelay.isZero()
                || maxRetryDelay.isNegative() || maxRetryDelay.isZero()
                || sendTimeout.isNegative() || sendTimeout.isZero()) {
            throw new IllegalArgumentException("outbox retry and send durations must be positive");
        }
        this.claimService = claimService;
        this.deliveryService = deliveryService;
        this.kafkaTemplate = kafkaTemplate;
        this.metrics = metrics;
        this.clock = clock;
        this.topic = topic;
        this.maxAttempts = maxAttempts;
        this.initialRetryDelay = initialRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
        this.sendTimeout = sendTimeout;
    }

    @Scheduled(fixedDelayString = "${workflow.outbox.publisher.fixed-delay-ms:1000}")
    public void publishAvailable() {
        List<OutboxClaim> claims = claimService.claimBatch();
        if (claims.isEmpty()) {
            return;
        }
        metrics.recordOutboxClaimed(claims.size());
        claims.forEach(this::publishOne);
    }

    private void publishOne(OutboxClaim claim) {
        WorkflowLifecycleEvent event = WorkflowLifecycleEvent.from(claim);
        try {
            SendResult<String, WorkflowLifecycleEvent> result = kafkaTemplate
                    .send(topic, claim.aggregateId().toString(), event)
                    .get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
            RecordMetadata metadata = result.getRecordMetadata();
            if (!deliveryService.markPublished(claim)) {
                log.warn("Outbox claim was no longer owned after Kafka publish: eventId={}", claim.id());
                return;
            }
            metrics.recordOutboxPublished();
            log.debug("Published workflow event: eventId={}, topic={}, partition={}, offset={}",
                    claim.id(), metadata.topic(), metadata.partition(), metadata.offset());
        } catch (Exception ex) {
            handleFailure(claim, ex);
        }
    }

    private void handleFailure(OutboxClaim claim, Exception exception) {
        String error = errorMessage(exception);
        metrics.recordOutboxPublishFailed();
        if (claim.attemptCount() >= maxAttempts) {
            if (deliveryService.markDeadLetter(claim, error)) {
                metrics.recordOutboxDeadLettered();
                log.error("Moved outbox event to dead letter state: eventId={}, attempts={}",
                        claim.id(), claim.attemptCount(), exception);
            }
            return;
        }

        Instant nextAttemptAt = clock.instant().plus(retryDelay(claim.attemptCount()));
        if (deliveryService.scheduleRetry(claim, nextAttemptAt, error)) {
            log.warn("Scheduled outbox retry: eventId={}, attempt={}, nextAttemptAt={}",
                    claim.id(), claim.attemptCount(), nextAttemptAt, exception);
        }
    }

    private Duration retryDelay(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 10);
        Duration calculated = initialRetryDelay.multipliedBy(multiplier);
        return calculated.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : calculated;
    }

    private String errorMessage(Exception exception) {
        Throwable failure = exception;
        while (failure.getCause() != null && failure.getCause() != failure) {
            failure = failure.getCause();
        }
        String message = failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
        return message.length() <= MAX_ERROR_LENGTH
                ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
