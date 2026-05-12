# Forgeflow

Forgeflow is a durable workflow orchestration engine for long-running backend and ML infrastructure workloads.

The project is intentionally infrastructure-first: users submit workflows, workers claim tasks with leases, failed work is retried with backoff, abandoned work is reassigned after lease expiry, and operators can inspect system health through APIs and metrics.

## Project Goals

- Durable workflow and job state backed by Postgres.
- Lease-based task ownership so dead workers do not hold work forever.
- Idempotent submission and state transitions.
- Retry policies with exponential backoff and dead-letter handling.
- Worker registration, heartbeats, and failure detection.
- Metrics for queue depth, job duration, retries, failures, and worker health.
- ML-adjacent demo workloads such as document ingestion, embedding generation, batch inference, and evaluation.

## Initial Milestones

1. Runnable Spring Boot API with health checks.
2. Job and workflow state machines with unit tests.
3. Postgres-backed job persistence.
4. Lease-based task claiming and renewal.
5. Worker heartbeat tracking and abandoned-task recovery.
6. Retry, cancellation, and dead-letter semantics.
7. Prometheus metrics and Docker Compose environment.

## Development

This repository starts small on purpose. Each distributed-systems feature should land as a focused change with tests or a runnable demonstration.

### Test Suite

Run the full suite with:

```bash
mvn test
```

Repository integration tests use Testcontainers and require Docker Desktop or another compatible Docker daemon.
