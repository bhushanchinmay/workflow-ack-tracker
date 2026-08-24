# Architecture

The service tracks one asynchronous workflow event and the acknowledgement expected from each downstream service.

```mermaid
flowchart TD
    U[Upstream service] --> A[Workflow REST API]
    D[Downstream services] --> A
    A --> P[(PostgreSQL)]
    S[Overdue scheduler] --> P
    P --> O[Outbox publisher]
    O --> K[Kafka]
```

## State transitions

`PENDING` becomes `COMPLETED` when every expected service acknowledges. A pending workflow becomes `FAILED` after its acknowledgement deadline. Completed and failed workflows are terminal.

## Consistency

Acknowledgement and failure transitions run in a transaction and lock the workflow row. The acknowledgement table has a unique constraint for `(workflow_id, service_name)`, which is the final database safety net against duplicate records.

## Outbox delivery

Terminal workflow changes are written to the outbox in the same database transaction as the workflow update. The publisher claims a bounded batch with a row lock and lease, sends events to Kafka, and marks successful rows as published. Failed sends use exponential backoff and move to a dead-letter state after the configured attempt limit.

The delivery guarantee is at least once. If Kafka accepts an event and the application fails before marking the row as published, the event may be sent again. The outbox event ID is stable and consumers must make processing idempotent.

## Scope

The current version focuses on tracking correctness and reliable publication of terminal lifecycle events. Authentication and alert delivery are separate extensions documented in the roadmap.
