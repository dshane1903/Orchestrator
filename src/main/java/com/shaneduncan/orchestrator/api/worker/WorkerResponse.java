package com.shaneduncan.orchestrator.api.worker;

import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.worker.WorkerNode;
import java.time.Instant;
import java.util.UUID;

public record WorkerResponse(
    String id,
    UUID currentJobId,
    Instant lastHeartbeatAt,
    Instant createdAt,
    Instant updatedAt
) {

    public static WorkerResponse from(WorkerNode worker) {
        return new WorkerResponse(
            worker.id().value(),
            worker.currentJobId().map(JobId::value).orElse(null),
            worker.lastHeartbeatAt(),
            worker.createdAt(),
            worker.updatedAt()
        );
    }
}
