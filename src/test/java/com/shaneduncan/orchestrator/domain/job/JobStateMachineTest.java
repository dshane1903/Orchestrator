package com.shaneduncan.orchestrator.domain.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class JobStateMachineTest {

    @Test
    void claimsPendingJobForExecution() {
        assertThat(JobStateMachine.transition(JobStatus.PENDING, JobEvent.CLAIM))
            .isEqualTo(JobStatus.RUNNING);
    }

    @Test
    void completesRunningJob() {
        assertThat(JobStateMachine.transition(JobStatus.RUNNING, JobEvent.COMPLETE))
            .isEqualTo(JobStatus.SUCCEEDED);
    }

    @Test
    void sendsRetryableFailureBackToRetrying() {
        assertThat(JobStateMachine.transition(JobStatus.RUNNING, JobEvent.FAIL_RETRYABLE))
            .isEqualTo(JobStatus.RETRYING);
    }

    @Test
    void releasesExpiredLeaseBackToPending() {
        assertThat(JobStateMachine.transition(JobStatus.RUNNING, JobEvent.LEASE_EXPIRED))
            .isEqualTo(JobStatus.PENDING);
    }

    @Test
    void marksExhaustedRetryAsDeadLettered() {
        assertThat(JobStateMachine.transition(JobStatus.RETRYING, JobEvent.MARK_DEAD_LETTERED))
            .isEqualTo(JobStatus.DEAD_LETTERED);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"PENDING", "RUNNING", "RETRYING"})
    void allowsCancellationBeforeTerminalState(JobStatus status) {
        assertThat(JobStateMachine.transition(status, JobEvent.CANCEL))
            .isEqualTo(JobStatus.CANCELLED);
    }

    @ParameterizedTest
    @EnumSource(value = JobStatus.class, names = {"SUCCEEDED", "CANCELLED", "DEAD_LETTERED"})
    void rejectsTransitionsFromTerminalStates(JobStatus status) {
        assertThatThrownBy(() -> JobStateMachine.transition(status, JobEvent.CLAIM))
            .isInstanceOf(InvalidJobStateTransitionException.class)
            .hasMessageContaining(status.name());
    }

    @Test
    void exposesTransitionChecksWithoutThrowing() {
        assertThat(JobStateMachine.canTransition(JobStatus.PENDING, JobEvent.CLAIM)).isTrue();
        assertThat(JobStateMachine.canTransition(JobStatus.SUCCEEDED, JobEvent.CLAIM)).isFalse();
    }
}

