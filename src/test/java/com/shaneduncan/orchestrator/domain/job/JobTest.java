package com.shaneduncan.orchestrator.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JobTest {

    private static final Instant NOW = Instant.parse("2026-05-12T18:00:00Z");
    private static final JobId JOB_ID = new JobId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
    private static final WorkerId WORKER_ONE = new WorkerId("worker-1");
    private static final WorkerId WORKER_TWO = new WorkerId("worker-2");

    @Test
    void createsPendingJob() {
        Job job = Job.create(JOB_ID, "batch-inference", 3, NOW);

        assertThat(job.status()).isEqualTo(JobStatus.PENDING);
        assertThat(job.attemptCount()).isZero();
        assertThat(job.assignmentVersion()).isZero();
        assertThat(job.nextRunAt()).contains(NOW);
    }

    @Test
    void claimAssignsLeaseAndFencingVersion() {
        Job claimed = Job.create(JOB_ID, "embedding-generation", 3, NOW)
            .claim(WORKER_ONE, NOW.plusSeconds(30), NOW);

        assertThat(claimed.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(claimed.attemptCount()).isEqualTo(1);
        assertThat(claimed.assignmentVersion()).isEqualTo(1);
        assertThat(claimed.leasedBy()).contains(WORKER_ONE);
        assertThat(claimed.leaseExpiresAt()).contains(NOW.plusSeconds(30));
    }

    @Test
    void completionRequiresCurrentAssignmentVersion() {
        Job claimed = Job.create(JOB_ID, "embedding-generation", 3, NOW)
            .claim(WORKER_ONE, NOW.plusSeconds(30), NOW);

        Job completed = claimed.complete(claimed.assignmentVersion(), NOW.plusSeconds(3));

        assertThat(completed.status()).isEqualTo(JobStatus.SUCCEEDED);
        assertThat(completed.leasedBy()).isEmpty();
        assertThat(completed.leaseExpiresAt()).isEmpty();
    }

    @Test
    void staleWorkerCannotCompleteAfterReassignment() {
        Job firstAssignment = Job.create(JOB_ID, "embedding-generation", 3, NOW)
            .claim(WORKER_ONE, NOW.plusSeconds(30), NOW);

        Job secondAssignment = firstAssignment
            .expireLease(NOW.plusSeconds(31))
            .claim(WORKER_TWO, NOW.plusSeconds(70), NOW.plusSeconds(40));

        assertThatThrownBy(() -> secondAssignment.complete(firstAssignment.assignmentVersion(), NOW.plusSeconds(45)))
            .isInstanceOf(StaleJobAssignmentException.class)
            .hasMessageContaining("observed version 1")
            .hasMessageContaining("current version is 2");
    }

    @Test
    void retryableFailureClearsLeaseAndSchedulesNextRun() {
        Job claimed = Job.create(JOB_ID, "embedding-generation", 3, NOW)
            .claim(WORKER_ONE, NOW.plusSeconds(30), NOW);

        Job retrying = claimed.failForRetry(
            claimed.assignmentVersion(),
            "temporary model server error",
            NOW.plusSeconds(60),
            NOW.plusSeconds(2)
        );

        assertThat(retrying.status()).isEqualTo(JobStatus.RETRYING);
        assertThat(retrying.leasedBy()).isEmpty();
        assertThat(retrying.nextRunAt()).contains(NOW.plusSeconds(60));
        assertThat(retrying.failureReason()).contains("temporary model server error");
    }

    @Test
    void rejectsClaimsWithExpiredLeaseDeadline() {
        Job job = Job.create(JOB_ID, "batch-inference", 3, NOW);

        assertThatThrownBy(() -> job.claim(WORKER_ONE, NOW, NOW))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("leaseExpiresAt");
    }
}

