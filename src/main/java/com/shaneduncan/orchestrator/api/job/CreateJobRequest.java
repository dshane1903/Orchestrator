package com.shaneduncan.orchestrator.api.job;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateJobRequest(
    @NotBlank String taskType,
    @Min(1) @Max(100) int maxAttempts
) {
}

