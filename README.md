# Workflow Tracker

A Spring Boot REST service for tracking asynchronous workflow events and the acknowledgements expected from downstream services.

## Design

- Java 17 and Spring Boot 3.4.
- PostgreSQL for durable state.
- Spring Data JPA with Flyway migration `V1__create_workflow_tables.sql`.
- `PENDING`, `COMPLETED`, and `FAILED` workflow states.
- The acknowledgement timeout is configurable through `WORKFLOW_ACKNOWLEDGEMENT_TIMEOUT` and defaults to 15 minutes.
- A scheduled checker is enabled by default and scans every 60 seconds. It marks overdue pending workflows as failed.

Acknowledgements are stored as rows instead of a counter. This keeps the expected service list and the current acknowledgement state queryable. The acknowledgement and failure operations lock the workflow row in a transaction, so they cannot both change the same workflow at the same time. A unique database constraint also protects against duplicate acknowledgement rows if two requests race.

## Run locally

Requirements: Java 17+, Maven 3.9+, and PostgreSQL 14+.

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Run the application:

```bash
mvn spring-boot:run
```

The API is available at `http://localhost:8080`.

To run the complete stack with Docker:

```bash
docker compose up --build
```

Useful configuration values:

```bash
export WORKFLOW_ACKNOWLEDGEMENT_TIMEOUT=PT15M
export WORKFLOW_SCHEDULER_ENABLED=true
export WORKFLOW_SCHEDULER_FIXED_DELAY_MS=60000
```

## API

### Create a workflow

`POST /workflows`

```bash
curl -i -X POST http://localhost:8080/workflows \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "order-1001-created",
    "payload": {"orderId": "1001", "amount": 250},
    "targetServices": ["billing", "shipping"]
  }'
```

The response has HTTP `201 Created` and contains a generated `workflowId`, an acknowledgement deadline, and one acknowledgement record for each target service.

### Acknowledge a workflow

`POST /workflows/{workflowId}/acknowledge`

```bash
curl -i -X POST http://localhost:8080/workflows/<workflow-id>/acknowledge \
  -H 'Content-Type: application/json' \
  -d '{"serviceName":"billing"}'
```

The service must be one of the expected target services. A duplicate acknowledgement, unknown service, completed workflow, failed workflow, or late acknowledgement returns HTTP `409 Conflict`.

### Get workflow state

`GET /workflows/{workflowId}`

```bash
curl -i http://localhost:8080/workflows/<workflow-id>
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

`GET /workflows/pending`

```bash
curl -i http://localhost:8080/workflows/pending
```

This returns workflows with status `PENDING`, including their missing services. Overdue workflows remain visible until the scheduler or the failure endpoint marks them as failed.

### Mark an overdue workflow as failed

`POST /workflows/{workflowId}/failed`

```bash
curl -i -X POST http://localhost:8080/workflows/<workflow-id>/failed \
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
  "path": "/workflows/9b2c0a89-3d5c-49c6-9c35-1c2d1c98cb72/acknowledge"
}
```

Common status codes are `201` for creation, `200` for successful reads and state changes, `400` for invalid input, `404` for an unknown workflow, and `409` for duplicate data or invalid state transitions.

## Database model

`workflow_events` stores the event payload, state, timestamps, and deadline. `workflow_acknowledgements` stores the expected service names and their acknowledgement timestamps. The foreign key cascades acknowledgement rows when a workflow is deleted, although this API does not expose deletion.

## Tests

The integration test uses Testcontainers and requires Docker:

```bash
mvn test
```

Without Docker, the application can still be run against any PostgreSQL instance using the datasource environment variables, but the Testcontainers integration test will not start.

## Assumptions and possible extensions

- `eventId` is an upstream idempotency key and is globally unique.
- Each target service acknowledges at most once. Acknowledgement calls are not intended to carry a result payload in this version.
- The scheduler marks overdue workflows as failed; alert delivery can be added after the state update, preferably through an outbox table so an alert is not lost.
- For a high-volume deployment, `GET /workflows/pending` should be paginated and the overdue scan should process batches with a lease or `SKIP LOCKED` strategy.
- Authentication and authorization are intentionally outside this exercise. In production, upstream and downstream callers should use service identity and authorize which services may acknowledge which workflows.
