package com.shaneduncan.orchestrator.domain.job;

public class JobAssignmentOwnershipException extends RuntimeException {

    public JobAssignmentOwnershipException(JobId jobId, WorkerId observedWorkerId, WorkerId currentWorkerId) {
        super(
            "Worker " + observedWorkerId.value()
                + " does not own job " + jobId.value()
                + "; current owner is " + currentWorkerId.value()
        );
    }
}
