ALTER TABLE outbox_events
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN claim_token UUID,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

UPDATE outbox_events
SET status = 'PUBLISHED'
WHERE published_at IS NOT NULL;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_status
        CHECK (status IN ('PENDING', 'CLAIMED', 'PUBLISHED', 'DEAD_LETTER'));

CREATE INDEX idx_outbox_pending_attempt
    ON outbox_events(status, next_attempt_at, created_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_outbox_expired_claims
    ON outbox_events(status, claimed_at)
    WHERE status = 'CLAIMED';
