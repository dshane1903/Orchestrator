package com.shaneduncan.orchestrator.api.workflow;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateWorkflowRequest(
    @NotBlank String name,
    @NotEmpty List<@Valid WorkflowNodeRequest> nodes
) {
}
