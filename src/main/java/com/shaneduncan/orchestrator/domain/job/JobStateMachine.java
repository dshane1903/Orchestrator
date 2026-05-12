package com.shaneduncan.orchestrator.domain.job;

import java.util.EnumMap;
import java.util.Map;

public final class JobStateMachine {

    private static final Map<JobStatus, Map<JobEvent, JobStatus>> TRANSITIONS = buildTransitions();

    private JobStateMachine() {
    }

    public static JobStatus transition(JobStatus currentStatus, JobEvent event) {
        Map<JobEvent, JobStatus> allowedEvents = TRANSITIONS.get(currentStatus);
        if (allowedEvents == null || !allowedEvents.containsKey(event)) {
            throw new InvalidJobStateTransitionException(currentStatus, event);
        }
        return allowedEvents.get(event);
    }

    public static boolean canTransition(JobStatus currentStatus, JobEvent event) {
        Map<JobEvent, JobStatus> allowedEvents = TRANSITIONS.get(currentStatus);
        return allowedEvents != null && allowedEvents.containsKey(event);
    }

    private static Map<JobStatus, Map<JobEvent, JobStatus>> buildTransitions() {
        EnumMap<JobStatus, Map<JobEvent, JobStatus>> transitions = new EnumMap<>(JobStatus.class);

        transitions.put(JobStatus.PENDING, Map.of(
            JobEvent.CLAIM, JobStatus.RUNNING,
            JobEvent.CANCEL, JobStatus.CANCELLED
        ));
        transitions.put(JobStatus.RUNNING, Map.of(
            JobEvent.COMPLETE, JobStatus.SUCCEEDED,
            JobEvent.FAIL_RETRYABLE, JobStatus.RETRYING,
            JobEvent.FAIL_PERMANENT, JobStatus.FAILED,
            JobEvent.LEASE_EXPIRED, JobStatus.PENDING,
            JobEvent.CANCEL, JobStatus.CANCELLED
        ));
        transitions.put(JobStatus.RETRYING, Map.of(
            JobEvent.CLAIM, JobStatus.RUNNING,
            JobEvent.MARK_DEAD_LETTERED, JobStatus.DEAD_LETTERED,
            JobEvent.CANCEL, JobStatus.CANCELLED
        ));
        transitions.put(JobStatus.FAILED, Map.of(
            JobEvent.MARK_DEAD_LETTERED, JobStatus.DEAD_LETTERED
        ));

        transitions.put(JobStatus.SUCCEEDED, Map.of());
        transitions.put(JobStatus.CANCELLED, Map.of());
        transitions.put(JobStatus.DEAD_LETTERED, Map.of());

        return Map.copyOf(transitions);
    }
}

