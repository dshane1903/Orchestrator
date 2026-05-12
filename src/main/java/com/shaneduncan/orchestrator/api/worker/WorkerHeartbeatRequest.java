package com.shaneduncan.orchestrator.api.worker;

import java.util.UUID;

public record WorkerHeartbeatRequest(
    UUID currentJobId
) {
}
