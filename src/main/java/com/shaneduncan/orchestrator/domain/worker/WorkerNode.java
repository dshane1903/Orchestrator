package com.shaneduncan.orchestrator.domain.worker;

import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import java.time.Instant;
import java.util.Optional;

public record WorkerNode(
    WorkerId id,
    JobId currentJob,
    Instant lastHeartbeatAt,
    Instant createdAt,
    Instant updatedAt
) {

    public Optional<JobId> currentJobId() {
        return Optional.ofNullable(currentJob);
    }
}
