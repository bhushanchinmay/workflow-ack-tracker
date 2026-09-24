# Workflow Tracker

A Spring Boot service that tracks asynchronous workflow events and acknowledgements from downstream services.

## What this project demonstrates

- PostgreSQL persistence with Flyway migrations.
- Transaction-safe acknowledgement updates.
- Overdue workflow detection and scheduled failure handling.
- Transactional outbox with Kafka publication.
- At-least-once delivery with retries and dead-letter handling.
- Docker Compose and Testcontainers integration tests.
- Request IDs, application metrics, and a versioned OpenAPI contract.

## Start here

- [Architecture](docs/architecture.md)
- [Project roadmap](docs/roadmap.md)
- [OpenAPI contract](openapi/workflow-tracker-v1.yaml)

## Technology

Java 17, Spring Boot 3.4, Spring Data JPA, PostgreSQL, Flyway, Kafka, Docker, and Testcontainers.

## Run the full stack

Requirements: Java 17+, Maven 3.9+, Docker Desktop, and Docker Compose.

```bash
docker compose up --build
```

The API runs at `http://localhost:8080`.

The local services use these ports:

| Service | Port |
|---|---:|
| API | 8080 |
| PostgreSQL | 5432 |
| Kafka | 9094 |

Stop the stack with:

```bash
docker compose down
```

PostgreSQL and Kafka data are stored in named Docker volumes. Remove the volumes only when you want to reset local data.

## Run without Kafka

This starts PostgreSQL and runs the application from Maven. The outbox publisher stays disabled.

```bash
docker compose up -d postgres
WORKFLOW_OUTBOX_PUBLISHER_ENABLED=false mvn spring-boot:run
```

## Try the API

Create a workflow:

```bash
curl -X POST http://localhost:8080/api/v1/workflows \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "order-1001-created",
    "payload": {"orderId": "1001", "amount": 250},
    "targetServices": ["billing", "shipping"]
  }'
```

Copy the returned `workflowId`, then acknowledge it:

```bash
curl -X POST http://localhost:8080/api/v1/workflows/<workflow-id>/acknowledge \
  -H 'Content-Type: application/json' \
  -d '{"serviceName":"billing"}'
```

Read the current state:

```bash
curl http://localhost:8080/api/v1/workflows/<workflow-id>
```

The response shows the workflow status, acknowledged services, and pending services.

## API endpoints

| Method | Endpoint | Purpose |
|---|---|---|
| `POST` | `/api/v1/workflows` | Create a workflow |
| `POST` | `/api/v1/workflows/{id}/acknowledge` | Record a downstream acknowledgement |
| `GET` | `/api/v1/workflows/{id}` | Read workflow state |
| `GET` | `/api/v1/workflows/pending` | List pending workflows |
| `POST` | `/api/v1/workflows/{id}/failed` | Fail an overdue workflow |

## Key design choices

Each expected service has its own acknowledgement row. This makes missing services visible and prevents duplicate acknowledgement records with a database constraint.

Acknowledgement and failure operations lock the workflow row inside a transaction. This prevents a late acknowledgement and the overdue scheduler from changing the same workflow at the same time.

Terminal workflow changes create an outbox row in the same transaction as the workflow update. A publisher claims rows with a database lease, sends them to Kafka, and marks them published after Kafka confirms the send.

Kafka delivery is at least once. If the application stops after Kafka accepts a message but before the database update, the message can be sent again. Consumers must use the stable outbox event ID for idempotency.

## Tests

Fast unit tests run without Docker:

```bash
mvn test
```

PostgreSQL and Kafka integration tests use Testcontainers:

```bash
mvn verify -Pintegration
```

GitHub Actions runs both suites on every pull request and on pushes to `master` (see `.github/workflows/ci.yml`).

## Observability

- Health: `GET /actuator/health`
- Prometheus metrics: `GET /actuator/prometheus`
- Request correlation: `X-Request-Id`
- Outbox metrics: claim, publish, retry, and dead-letter counters

## Known limits

- JWT authentication is not implemented.
- Consumers are outside this repository.
- Dead-letter replay is not exposed through an API.
