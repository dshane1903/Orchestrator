package com.shaneduncan.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shaneduncan.orchestrator.application.job.JobService;
import com.shaneduncan.orchestrator.config.LeaseRecoverySchedulerProperties;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class LeaseRecoverySchedulerTest {

    @Mock
    private JobService jobService;

    @Test
    void recoversExpiredLeasesWhenEnabled() {
        LeaseRecoveryScheduler scheduler = new LeaseRecoveryScheduler(
            jobService,
            new LeaseRecoverySchedulerProperties(true, 50)
        );
        when(jobService.recoverExpiredLeases(50)).thenReturn(List.of());

        scheduler.recoverExpiredLeases();

        verify(jobService).recoverExpiredLeases(50);
    }

    @Test
    void skipsRecoveryWhenDisabled() {
        LeaseRecoveryScheduler scheduler = new LeaseRecoveryScheduler(
            jobService,
            new LeaseRecoverySchedulerProperties(false, 50)
        );

        scheduler.recoverExpiredLeases();

        verifyNoInteractions(jobService);
    }

    @Test
    void exposesScheduledRecoveryLoop() throws NoSuchMethodException {
        Method method = LeaseRecoveryScheduler.class.getMethod("recoverExpiredLeases");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.initialDelayString())
            .isEqualTo("${forgeflow.scheduler.lease-recovery.initial-delay-ms}");
        assertThat(scheduled.fixedDelayString())
            .isEqualTo("${forgeflow.scheduler.lease-recovery.fixed-delay-ms}");
    }
}

