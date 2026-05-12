package com.shaneduncan.orchestrator.api.job;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ClaimJobRequest(
    @NotBlank String workerId,
    @Min(1) long leaseSeconds
) {
}

