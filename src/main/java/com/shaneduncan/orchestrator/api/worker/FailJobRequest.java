package com.shaneduncan.orchestrator.api.worker;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record FailJobRequest(
    @Min(0) long assignmentVersion,
    @NotBlank String reason,
    boolean retryable,
    @Min(0) long retryDelaySeconds
) {
}
