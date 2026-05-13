ALTER TABLE jobs
    ADD COLUMN idempotency_key TEXT;

CREATE UNIQUE INDEX idx_jobs_idempotency_key
    ON jobs (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

ALTER TABLE workflows
    ADD COLUMN idempotency_key TEXT;

CREATE UNIQUE INDEX idx_workflows_idempotency_key
    ON workflows (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
