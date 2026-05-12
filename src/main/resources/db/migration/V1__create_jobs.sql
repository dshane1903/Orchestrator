CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    task_type TEXT NOT NULL,
    status TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    max_attempts INTEGER NOT NULL CHECK (max_attempts >= 1),
    assignment_version BIGINT NOT NULL DEFAULT 0 CHECK (assignment_version >= 0),
    leased_by TEXT,
    lease_expires_at TIMESTAMPTZ,
    next_run_at TIMESTAMPTZ,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT jobs_status_check CHECK (
        status IN (
            'PENDING',
            'RUNNING',
            'RETRYING',
            'SUCCEEDED',
            'FAILED',
            'CANCELLED',
            'DEAD_LETTERED'
        )
    )
);

CREATE INDEX idx_jobs_runnable
    ON jobs (status, next_run_at, created_at)
    WHERE status IN ('PENDING', 'RETRYING');

CREATE INDEX idx_jobs_expired_leases
    ON jobs (lease_expires_at)
    WHERE status = 'RUNNING';

