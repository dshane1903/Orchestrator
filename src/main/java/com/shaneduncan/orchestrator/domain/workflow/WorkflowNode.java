package com.shaneduncan.orchestrator.domain.workflow;

import java.util.List;

public record WorkflowNode(
    String key,
    String taskType,
    int maxAttempts,
    List<String> dependsOn
) {

    public WorkflowNode {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
        if (taskType == null || taskType.isBlank()) {
            throw new IllegalArgumentException("taskType is required");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }
}
