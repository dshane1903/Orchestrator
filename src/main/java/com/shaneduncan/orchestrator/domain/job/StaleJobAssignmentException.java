package com.shaneduncan.orchestrator.domain.job;

public class StaleJobAssignmentException extends RuntimeException {

    public StaleJobAssignmentException(JobId jobId, long observedVersion, long currentVersion) {
        super(
            "Stale assignment for job " + jobId.value()
                + ": observed version " + observedVersion
                + " but current version is " + currentVersion
        );
    }
}

