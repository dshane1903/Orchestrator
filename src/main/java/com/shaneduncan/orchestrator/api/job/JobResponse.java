package com.shaneduncan.orchestrator.api.job;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobStatus;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(
    UUID id,
    String taskType,
    JobStatus status,
    int attemptCount,
    int maxAttempts,
    long assignmentVersion,
    String leasedBy,
    Instant leaseExpiresAt,
    Instant nextRunAt,
    String failureReason,
    Instant createdAt,
    Instant updatedAt
) {

    public static JobResponse from(Job job) {
        return new JobResponse(
            job.id().value(),
            job.taskType(),
            job.status(),
            job.attemptCount(),
            job.maxAttempts(),
            job.assignmentVersion(),
            job.leasedBy().map(WorkerId::value).orElse(null),
            job.leaseExpiresAt().orElse(null),
            job.nextRunAt().orElse(null),
            job.failureReason().orElse(null),
            job.createdAt(),
            job.updatedAt()
        );
    }
}

