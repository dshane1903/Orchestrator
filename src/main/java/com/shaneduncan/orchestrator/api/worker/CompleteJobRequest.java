package com.shaneduncan.orchestrator.api.worker;

import jakarta.validation.constraints.Min;

public record CompleteJobRequest(
    @Min(0) long assignmentVersion
) {
}
