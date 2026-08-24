CREATE TABLE workflow_events (
    id UUID PRIMARY KEY,
    event_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    ack_deadline TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_workflow_event_id UNIQUE (event_id),
    CONSTRAINT ck_workflow_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE workflow_acknowledgements (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflow_events(id) ON DELETE CASCADE,
    service_name VARCHAR(100) NOT NULL,
    acknowledged_at TIMESTAMPTZ,
    CONSTRAINT uk_workflow_service UNIQUE (workflow_id, service_name)
);

CREATE INDEX idx_workflow_status_created ON workflow_events(status, created_at);
CREATE INDEX idx_workflow_overdue ON workflow_events(status, ack_deadline);
CREATE INDEX idx_ack_workflow ON workflow_acknowledgements(workflow_id);
