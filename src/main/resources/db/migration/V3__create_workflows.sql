ALTER TABLE jobs
    DROP CONSTRAINT jobs_status_check;

ALTER TABLE jobs
    ADD CONSTRAINT jobs_status_check CHECK (
        status IN (
            'BLOCKED',
            'PENDING',
            'RUNNING',
            'RETRYING',
            'SUCCEEDED',
            'FAILED',
            'CANCELLED',
            'DEAD_LETTERED'
        )
    );

CREATE TABLE workflows (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT workflows_status_check CHECK (
        status IN (
            'RUNNING',
            'SUCCEEDED',
            'FAILED',
            'CANCELLED'
        )
    )
);

CREATE TABLE workflow_jobs (
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    node_key TEXT NOT NULL,
    PRIMARY KEY (workflow_id, job_id),
    UNIQUE (workflow_id, node_key),
    UNIQUE (job_id)
);

CREATE TABLE job_dependencies (
    workflow_id UUID NOT NULL REFERENCES workflows(id) ON DELETE CASCADE,
    upstream_job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    downstream_job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    PRIMARY KEY (workflow_id, upstream_job_id, downstream_job_id)
);

CREATE INDEX idx_job_dependencies_upstream
    ON job_dependencies (upstream_job_id);

CREATE INDEX idx_job_dependencies_downstream
    ON job_dependencies (downstream_job_id);

CREATE INDEX idx_workflow_jobs_job_id
    ON workflow_jobs (job_id);
