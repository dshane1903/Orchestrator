package com.shaneduncan.orchestrator.application.workflow;

import com.shaneduncan.orchestrator.domain.workflow.Workflow;
import java.util.List;

public record WorkflowDetails(
    Workflow workflow,
    List<WorkflowJobDetails> jobs
) {

    public WorkflowDetails {
        jobs = List.copyOf(jobs);
    }
}
