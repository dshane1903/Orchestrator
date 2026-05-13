package com.shaneduncan.orchestrator.observability;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobStatus;
import com.shaneduncan.orchestrator.persistence.job.JdbcJobRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class JobMetrics {

    private final Counter claimedJobs;
    private final Counter deadLetteredJobs;
    private final Counter leaseRecoveryRuns;
    private final Counter expiredLeases;

    public JobMetrics(MeterRegistry meterRegistry, JdbcJobRepository jobRepository) {
        this.claimedJobs = Counter.builder("forgeflow.jobs.claimed")
            .description("Total jobs successfully claimed by workers")
            .register(meterRegistry);
        this.deadLetteredJobs = Counter.builder("forgeflow.jobs.dead.lettered")
            .description("Total jobs moved to the dead-letter queue")
            .register(meterRegistry);
        this.leaseRecoveryRuns = Counter.builder("forgeflow.leases.recovery.runs")
            .description("Total lease recovery sweeps that recovered at least one job")
            .register(meterRegistry);
        this.expiredLeases = Counter.builder("forgeflow.leases.expired")
            .description("Total expired job leases recovered back to runnable state")
            .register(meterRegistry);

        for (JobStatus status : JobStatus.values()) {
            Gauge.builder(
                    "forgeflow.jobs.queue.depth",
                    jobRepository,
                    repository -> repository.countByStatus(status)
                )
                .description("Current number of jobs by status")
                .tag("status", status.name())
                .register(meterRegistry);
        }
    }

    public void recordClaim(Job job) {
        claimedJobs.increment();
    }

    public void recordRecoveredExpiredLeases(int recoveredCount) {
        if (recoveredCount < 1) {
            return;
        }
        leaseRecoveryRuns.increment();
        expiredLeases.increment(recoveredCount);
    }

    public void recordDeadLetteredJobs(int deadLetteredCount) {
        if (deadLetteredCount < 1) {
            return;
        }
        deadLetteredJobs.increment(deadLetteredCount);
    }
}
