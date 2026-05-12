package com.shaneduncan.orchestrator.application.job;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shaneduncan.orchestrator.persistence.job.JdbcJobRepository;
import com.shaneduncan.orchestrator.persistence.workflow.JdbcWorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

    @Test
    void recoversExpiredLeasesUsingCurrentClockTime() {
        JobService jobService = new JobService(
            jobRepository,
            workflowRepository,
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
        when(jobRepository.recoverExpiredLeases(NOW, 25)).thenReturn(List.of());

        jobService.recoverExpiredLeases(25);

        verify(jobRepository).recoverExpiredLeases(NOW, 25);
    }
}
