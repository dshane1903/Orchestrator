package com.shaneduncan.orchestrator.api.workflow;

import com.shaneduncan.orchestrator.api.job.JobResponse;
import com.shaneduncan.orchestrator.application.workflow.WorkflowJobDetails;

public record WorkflowJobResponse(
    String nodeKey,
    JobResponse job
) {

    public static WorkflowJobResponse from(WorkflowJobDetails details) {
        return new WorkflowJobResponse(details.nodeKey(), JobResponse.from(details.job()));
    }
}
