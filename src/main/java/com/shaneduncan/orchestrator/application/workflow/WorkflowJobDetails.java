package com.shaneduncan.orchestrator.application.workflow;

import com.shaneduncan.orchestrator.domain.job.Job;

public record WorkflowJobDetails(
    String nodeKey,
    Job job
) {

    public WorkflowJobDetails {
        if (nodeKey == null || nodeKey.isBlank()) {
            throw new IllegalArgumentException("nodeKey is required");
        }
    }
}
