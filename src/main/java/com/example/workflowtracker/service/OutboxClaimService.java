package com.example.workflowtracker.service;

import com.example.workflowtracker.outbox.OutboxClaim;
import com.example.workflowtracker.outbox.OutboxEvent;
import com.example.workflowtracker.outbox.OutboxRepository;
import com.example.workflowtracker.outbox.OutboxStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxClaimService {

    private final OutboxRepository outboxRepository;
    private final Clock clock;
    private final int batchSize;
    private final Duration claimLease;

    public OutboxClaimService(OutboxRepository outboxRepository,
                              Clock clock,
                              @Value("${workflow.outbox.publisher.batch-size:50}") int batchSize,
                              @Value("${workflow.outbox.publisher.claim-lease:PT2M}") Duration claimLease) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("outbox publisher batch size must be positive");
        }
        if (claimLease.isZero() || claimLease.isNegative()) {
            throw new IllegalArgumentException("outbox claim lease must be positive");
        }
        this.outboxRepository = outboxRepository;
        this.clock = clock;
        this.batchSize = batchSize;
        this.claimLease = claimLease;
    }

    @Transactional
    public List<OutboxClaim> claimBatch() {
        Instant now = clock.instant();
        List<OutboxEvent> events = outboxRepository.findClaimableForUpdate(
                OutboxStatus.PENDING,
                OutboxStatus.CLAIMED,
                now,
                now.minus(claimLease),
                PageRequest.of(0, batchSize));
        UUID claimToken = UUID.randomUUID();
        events.forEach(event -> event.claim(claimToken, now));
        return events.stream().map(OutboxClaim::from).toList();
    }
}
