package com.shaneduncan.orchestrator.domain.workflow;

import java.time.Instant;
import java.util.Objects;

public record Workflow(
    WorkflowId id,
    String name,
    WorkflowStatus status,
    Instant createdAt,
    Instant updatedAt
) {

    public Workflow {
        Objects.requireNonNull(id, "id is required");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static Workflow create(WorkflowId id, String name, Instant now) {
        return new Workflow(id, name, WorkflowStatus.RUNNING, now, now);
    }
}
