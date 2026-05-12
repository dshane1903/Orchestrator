package com.shaneduncan.orchestrator.domain.job;

public class InvalidJobStateTransitionException extends RuntimeException {

    public InvalidJobStateTransitionException(JobStatus currentStatus, JobEvent event) {
        super("Cannot apply event " + event + " to job in status " + currentStatus);
    }
}

