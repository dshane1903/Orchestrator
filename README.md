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

### Local Stack

Start the full demo stack with:

```bash
docker compose up --build
```

Services:

- Forgeflow API: `http://localhost:8080`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000` with `admin` / `admin`
- Postgres: `localhost:55432`

Grafana provisions the Forgeflow overview dashboard automatically. Stop the stack with:

```bash
docker compose down
```

### Test Suite

Run the full suite with:

```bash
mvn test
```

Repository integration tests use Testcontainers and require Docker Desktop or another compatible Docker daemon.

### API Preview

Submit a job:

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H 'Content-Type: application/json' \
  -d '{"taskType":"embedding-generation","maxAttempts":3,"idempotencyKey":"embed-docs-001"}'
```

Submit a workflow DAG:

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H 'Content-Type: application/json' \
  -d '{
    "name":"document-indexing",
    "nodes":[
      {"key":"ingest","taskType":"document-ingestion","maxAttempts":3,"dependsOn":[]},
      {"key":"embed","taskType":"embedding-generation","maxAttempts":3,"dependsOn":["ingest"]}
    ],
    "idempotencyKey":"index-run-001"
  }'
```

Claim the next runnable job for a worker:

```bash
curl -X POST http://localhost:8080/api/jobs/claim \
  -H 'Content-Type: application/json' \
  -d '{"workerId":"worker-1","leaseSeconds":30}'
```

Long-poll for work from the worker API:

```bash
curl -X POST http://localhost:8080/api/workers/worker-1/poll \
  -H 'Content-Type: application/json' \
  -d '{"leaseSeconds":30,"waitSeconds":10}'
```

Renew the lease before it expires:

```bash
curl -X POST http://localhost:8080/api/workers/worker-1/jobs/<job-id>/renew-lease \
  -H 'Content-Type: application/json' \
  -d '{"assignmentVersion":1,"leaseSeconds":30}'
```

Complete a job with its fencing token:

```bash
curl -X POST http://localhost:8080/api/workers/worker-1/jobs/<job-id>/complete \
  -H 'Content-Type: application/json' \
  -d '{"assignmentVersion":1}'
```

Run a demo worker:

```bash
python3 tools/demo_worker.py --worker-id demo-worker-1 --max-jobs 5
```

Inject retryable failures from the demo worker:

```bash
python3 tools/demo_worker.py --worker-id flaky-worker-1 --failure-rate 0.25
```

Run the k6 submission/claim load test:

```bash
k6 run load/k6/jobs.js
```

Scrape Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Key Forgeflow metrics include claim count, dead-letter count, lease recovery runs, expired lease count, and queue depth by job status.
