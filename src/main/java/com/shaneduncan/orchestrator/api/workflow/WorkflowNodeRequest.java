package com.shaneduncan.orchestrator.api.workflow;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record WorkflowNodeRequest(
    @NotBlank String key,
    @NotBlank String taskType,
    @Min(1) int maxAttempts,
    List<String> dependsOn
) {
}
