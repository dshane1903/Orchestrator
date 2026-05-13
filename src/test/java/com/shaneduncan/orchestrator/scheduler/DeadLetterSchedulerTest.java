package com.shaneduncan.orchestrator.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.shaneduncan.orchestrator.application.job.JobService;
import com.shaneduncan.orchestrator.config.DeadLetterSchedulerProperties;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

@ExtendWith(MockitoExtension.class)
class DeadLetterSchedulerTest {

    @Mock
    private JobService jobService;

    @Test
    void movesFailedJobsWhenEnabled() {
        DeadLetterScheduler scheduler = new DeadLetterScheduler(
            jobService,
            new DeadLetterSchedulerProperties(true, 50)
        );
        when(jobService.moveFailedJobsToDeadLetter(50)).thenReturn(List.of());

        scheduler.moveFailedJobsToDeadLetter();

        verify(jobService).moveFailedJobsToDeadLetter(50);
    }

    @Test
    void skipsDeadLetterSweepWhenDisabled() {
        DeadLetterScheduler scheduler = new DeadLetterScheduler(
            jobService,
            new DeadLetterSchedulerProperties(false, 50)
        );

        scheduler.moveFailedJobsToDeadLetter();

        verifyNoInteractions(jobService);
    }

    @Test
    void exposesScheduledDeadLetterLoop() throws NoSuchMethodException {
        Method method = DeadLetterScheduler.class.getMethod("moveFailedJobsToDeadLetter");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.initialDelayString())
            .isEqualTo("${forgeflow.scheduler.dead-letter.initial-delay-ms}");
        assertThat(scheduled.fixedDelayString())
            .isEqualTo("${forgeflow.scheduler.dead-letter.fixed-delay-ms}");
    }
}
