package com.shaneduncan.orchestrator.domain.workflow;

import java.util.UUID;

public record WorkflowId(UUID value) {

    public WorkflowId {
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
    }

    public static WorkflowId newId() {
        return new WorkflowId(UUID.randomUUID());
    }
}
