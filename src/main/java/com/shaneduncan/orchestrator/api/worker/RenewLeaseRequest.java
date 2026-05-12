package com.shaneduncan.orchestrator.api.worker;

import jakarta.validation.constraints.Min;

public record RenewLeaseRequest(
    @Min(0) long assignmentVersion,
    @Min(1) long leaseSeconds
) {
}
