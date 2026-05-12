CREATE TABLE workers (
    id TEXT PRIMARY KEY,
    current_job_id UUID REFERENCES jobs(id) ON DELETE SET NULL,
    last_heartbeat_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_workers_last_heartbeat_at
    ON workers (last_heartbeat_at);
