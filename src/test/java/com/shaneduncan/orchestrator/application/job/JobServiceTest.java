package com.shaneduncan.orchestrator.application.job;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import com.shaneduncan.orchestrator.observability.JobMetrics;
import com.shaneduncan.orchestrator.persistence.job.JdbcJobRepository;
import com.shaneduncan.orchestrator.persistence.workflow.JdbcWorkflowRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    private static final Instant NOW = Instant.parse("2026-05-13T04:00:00Z");

    @Mock
    private JdbcJobRepository jobRepository;

    @Mock
    private JdbcWorkflowRepository workflowRepository;

    @Mock
    private JobMetrics jobMetrics;

    @Test
    void recoversExpiredLeasesUsingCurrentClockTime() {
        JobService jobService = new JobService(
            jobRepository,
            workflowRepository,
            jobMetrics,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(jobRepository.recoverExpiredLeases(NOW, 25)).thenReturn(List.of());

        jobService.recoverExpiredLeases(25);

        verify(jobRepository).recoverExpiredLeases(NOW, 25);
    }

    @Test
    void recordsMetricWhenJobIsClaimed() {
        JobService jobService = new JobService(
            jobRepository,
            workflowRepository,
            jobMetrics,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        Job claimedJob = Job.create(
                new JobId(UUID.fromString("f23e4567-e89b-12d3-a456-426614174000")),
                "embedding-generation",
                3,
                NOW
            )
            .claim(new WorkerId("worker-1"), NOW.plusSeconds(30), NOW);
        when(jobRepository.claimNextRunnable(
            new WorkerId("worker-1"),
            NOW,
            NOW.plusSeconds(30)
        )).thenReturn(Optional.of(claimedJob));

        jobService.claimNextRunnable("worker-1", Duration.ofSeconds(30));

        verify(jobMetrics).recordClaim(claimedJob);
    }
}
