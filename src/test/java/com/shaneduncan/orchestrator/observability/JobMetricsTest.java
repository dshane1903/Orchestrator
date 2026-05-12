package com.shaneduncan.orchestrator.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.shaneduncan.orchestrator.domain.job.JobStatus;
import com.shaneduncan.orchestrator.persistence.job.JdbcJobRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class JobMetricsTest {

    @Test
    void recordsLeaseRecoveryAndQueueDepthMetrics() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        JdbcJobRepository jobRepository = org.mockito.Mockito.mock(JdbcJobRepository.class);
        when(jobRepository.countByStatus(JobStatus.PENDING)).thenReturn(7L);
        JobMetrics metrics = new JobMetrics(meterRegistry, jobRepository);

        metrics.recordRecoveredExpiredLeases(3);

        assertThat(meterRegistry.counter("forgeflow.leases.recovery.runs").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("forgeflow.leases.expired").count()).isEqualTo(3.0);
        assertThat(meterRegistry.get("forgeflow.jobs.queue.depth")
            .tag("status", JobStatus.PENDING.name())
            .gauge()
            .value()).isEqualTo(7.0);
    }
}
