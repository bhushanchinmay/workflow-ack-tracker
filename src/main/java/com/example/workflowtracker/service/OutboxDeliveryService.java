package com.example.workflowtracker.service;

import com.example.workflowtracker.outbox.OutboxClaim;
import com.example.workflowtracker.outbox.OutboxRepository;
import com.example.workflowtracker.outbox.OutboxStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class OutboxDeliveryService {

    private final OutboxRepository outboxRepository;
    private final Clock clock;

    public OutboxDeliveryService(OutboxRepository outboxRepository, Clock clock) {
        this.outboxRepository = outboxRepository;
        this.clock = clock;
    }

    @Transactional
    public boolean markPublished(OutboxClaim claim) {
        return outboxRepository.markPublished(
                claim.id(),
                claim.claimToken(),
                OutboxStatus.CLAIMED,
                OutboxStatus.PUBLISHED,
                clock.instant()) == 1;
    }

    @Transactional
    public boolean scheduleRetry(OutboxClaim claim, Instant nextAttemptAt, String error) {
        return outboxRepository.scheduleRetry(
                claim.id(),
                claim.claimToken(),
                OutboxStatus.CLAIMED,
                OutboxStatus.PENDING,
                nextAttemptAt,
                error) == 1;
    }

    @Transactional
    public boolean markDeadLetter(OutboxClaim claim, String error) {
        return outboxRepository.markDeadLetter(
                claim.id(),
                claim.claimToken(),
                OutboxStatus.CLAIMED,
                OutboxStatus.DEAD_LETTER,
                error) == 1;
    }
}
