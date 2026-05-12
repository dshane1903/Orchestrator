package com.shaneduncan.orchestrator.api.worker;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PollWorkerRequest(
    @Min(1) long leaseSeconds,
    @Min(0) @Max(30) long waitSeconds
) {
}
