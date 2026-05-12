package com.shaneduncan.orchestrator.api.workflow;

import com.shaneduncan.orchestrator.application.workflow.WorkflowDetails;
import com.shaneduncan.orchestrator.domain.workflow.WorkflowStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
    UUID id,
    String name,
    WorkflowStatus status,
    List<WorkflowJobResponse> jobs,
    Instant createdAt,
    Instant updatedAt
) {

    public static WorkflowResponse from(WorkflowDetails details) {
        return new WorkflowResponse(
            details.workflow().id().value(),
            details.workflow().name(),
            details.workflow().status(),
            details.jobs().stream()
                .map(WorkflowJobResponse::from)
                .toList(),
            details.workflow().createdAt(),
            details.workflow().updatedAt()
        );
    }
}
