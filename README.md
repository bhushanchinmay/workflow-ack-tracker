# Workflow Tracker

A Spring Boot REST service for tracking asynchronous workflow events and the acknowledgements expected from downstream services.

## Design

- Java 17 and Spring Boot 3.4.
- PostgreSQL for durable state.
- Spring Data JPA with Flyway migrations for workflow state and outbox claim state.
- `PENDING`, `COMPLETED`, and `FAILED` workflow states.
- The acknowledgement timeout is configurable through `WORKFLOW_ACKNOWLEDGEMENT_TIMEOUT` and defaults to 15 minutes.
- A scheduled checker is enabled by default and scans every 60 seconds. It marks overdue pending workflows as failed.
- Every response includes an `X-Request-Id` response header. Error responses also include the request ID, and unexpected server errors log it for troubleshooting.
- Workflow counters are exposed through the Prometheus endpoint: created, accepted acknowledgements, completed, failed, and rejected acknowledgements.
- Terminal workflow events are written to the transactional outbox and published to Kafka when the outbox publisher is enabled.

Acknowledgements are stored as rows instead of a counter. This keeps the expected service list and the current acknowledgement state queryable. The acknowledgement and failure operations lock the workflow row in a transaction, so they cannot both change the same workflow at the same time. A unique database constraint also protects against duplicate acknowledgement rows if two requests race.

## Run locally

Requirements: Java 17+, Maven 3.9+ for host-side commands, Docker Desktop, and Docker Compose.

Start the complete local stack:

```bash
docker compose up --build
```

The application is available at `http://localhost:8080`. PostgreSQL is available on port `5432`, and Kafka is available to host processes on `localhost:9094`. The application container uses `kafka:9092` internally.

To run the application locally against PostgreSQL without Kafka publishing:

```bash
docker compose up -d postgres
WORKFLOW_OUTBOX_PUBLISHER_ENABLED=false mvn spring-boot:run
```

To stop the stack:

```bash
docker compose down
```

The application container runs as a non-root user. PostgreSQL and Kafka data are stored in named volumes and survive container restarts. Remove those volumes only when you intentionally want to delete local data.

Useful configuration values:

```bash
export WORKFLOW_ACKNOWLEDGEMENT_TIMEOUT=PT15M
export WORKFLOW_SCHEDULER_ENABLED=true
export WORKFLOW_SCHEDULER_FIXED_DELAY_MS=60000
export WORKFLOW_OUTBOX_PUBLISHER_ENABLED=true
export KAFKA_BOOTSTRAP_SERVERS=localhost:9094
```

## API

### Create a workflow

`POST /api/v1/workflows`

```bash
curl -i -X POST http://localhost:8080/api/v1/workflows \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "order-1001-created",
    "payload": {"orderId": "1001", "amount": 250},
    "targetServices": ["billing", "shipping"]
  }'
```

The response has HTTP `201 Created` and contains a generated `workflowId`, an acknowledgement deadline, and one acknowledgement record for each target service.

### Acknowledge a workflow

`POST /api/v1/workflows/{workflowId}/acknowledge`

```bash
curl -i -X POST http://localhost:8080/api/v1/workflows/<workflow-id>/acknowledge \
  -H 'Content-Type: application/json' \
  -d '{"serviceName":"billing"}'
```

The service must be one of the expected target services. A duplicate acknowledgement, unknown service, completed workflow, failed workflow, or late acknowledgement returns HTTP `409 Conflict`.

### Get workflow state

`GET /api/v1/workflows/{workflowId}`

```bash
curl -i http://localhost:8080/api/v1/workflows/<workflow-id>
```

The response includes `acknowledgements`, `acknowledgedServices`, and `pendingServices`.

Example response shape:

```json
{
  "workflowId": "9b2c0a89-3d5c-49c6-9c35-1c2d1c98cb72",
  "eventId": "order-1001-created",
  "payload": {"orderId": "1001", "amount": 250},
  "status": "PENDING",
  "failureReason": null,
  "createdAt": "2026-08-24T10:00:00Z",
  "updatedAt": "2026-08-24T10:01:00Z",
  "ackDeadline": "2026-08-24T10:15:00Z",
  "acknowledgements": [
    {"serviceName":"billing", "acknowledged":true, "acknowledgedAt":"2026-08-24T10:01:00Z"},
    {"serviceName":"shipping", "acknowledged":false, "acknowledgedAt":null}
  ],
  "acknowledgedServices": ["billing"],
  "pendingServices": ["shipping"]
}
```

### List pending workflows

`GET /api/v1/workflows/pending`

```bash
curl -i http://localhost:8080/api/v1/workflows/pending
```

This returns workflows with status `PENDING`, including their missing services. Overdue workflows remain visible until the scheduler or the failure endpoint marks them as failed.

### Mark an overdue workflow as failed

`POST /api/v1/workflows/{workflowId}/failed`

```bash
curl -i -X POST http://localhost:8080/api/v1/workflows/<workflow-id>/failed \
  -H 'Content-Type: application/json' \
  -d '{"reason":"shipping acknowledgement did not arrive"}'
```

The endpoint succeeds only after the acknowledgement deadline and only while the workflow is still pending. Calling it before the deadline, after completion, or after a previous failure returns HTTP `409 Conflict`.

## Error responses

Errors use a consistent JSON shape:

```json
{
  "timestamp": "2026-08-24T10:05:00Z",
  "status": 409,
  "error": "INVALID_WORKFLOW_STATE",
  "message": "duplicate acknowledgement from service: billing",
  "path": "/api/v1/workflows/9b2c0a89-3d5c-49c6-9c35-1c2d1c98cb72/acknowledge",
  "requestId": "f8d1f6e8-6a29-4e41-b5ac-bc32d42d2c66"
}
```

Common status codes are `201` for creation, `200` for successful reads and state changes, `400` for invalid input, `404` for an unknown workflow, and `409` for duplicate data or invalid state transitions.

The `X-Request-Id` header can be supplied by an upstream caller when it matches the allowed `[A-Za-z0-9._-]` format and is at most 100 characters. Otherwise, the service generates a new UUID. The value is also placed in the application logging context.

## Database model

`workflow_events` stores the event payload, state, timestamps, and deadline. `workflow_acknowledgements` stores the expected service names and their acknowledgement timestamps. The foreign key cascades acknowledgement rows when a workflow is deleted, although this API does not expose deletion.

## Current implementation status

The local implementation includes the required workflow APIs, PostgreSQL persistence, row-locked acknowledgement transitions, an overdue scheduler, Flyway migrations, a transactional outbox, a lease-based Kafka publisher with retry and dead-letter handling, Actuator health and Prometheus endpoint configuration, Docker Compose, PostgreSQL and Kafka Testcontainers integration tests, request correlation IDs, and workflow metrics. JWT authentication and RFC 9457 error responses remain follow-up portfolio milestones.

## Tests

Fast unit tests run without Docker:

```bash
mvn test
```

Infrastructure integration tests use PostgreSQL and Kafka Testcontainers and require Docker:

```bash
mvn verify -Pintegration
```

The integration profile is intentionally separate so a developer can run fast domain tests without starting infrastructure.

## Assumptions and possible extensions

- `eventId` is an upstream idempotency key and is globally unique.
- Each target service acknowledges at most once. Acknowledgement calls are not intended to carry a result payload in this version.
- The scheduler marks overdue workflows as failed; alert delivery can be added after the state update, preferably through the existing outbox so an alert is not lost.
- Kafka delivery is at least once. A publisher crash after Kafka accepts a message but before the database update can cause a duplicate. Consumers must use the stable outbox event ID for idempotency.
- The publisher claims bounded batches using database row locks and a lease. An expired lease allows another publisher instance to recover a crashed claim.
- The scheduler processes at most `WORKFLOW_SCHEDULER_BATCH_SIZE` workflows per run, ordered by deadline and workflow ID. A larger deployment should add `SKIP LOCKED` or a lease so multiple scheduler instances can share work safely.
- For a high-volume deployment, `GET /api/v1/workflows/pending` should be paginated and the overdue scan should process bounded batches with a lease or `SKIP LOCKED` strategy.
- Authentication and authorization are intentionally outside this exercise. In production, upstream and downstream callers should use service identity and authorize which services may acknowledge which workflows.
