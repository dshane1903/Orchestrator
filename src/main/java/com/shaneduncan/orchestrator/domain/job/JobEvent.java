package com.shaneduncan.orchestrator.domain.job;

public enum JobEvent {
    CLAIM,
    COMPLETE,
    FAIL_RETRYABLE,
    FAIL_PERMANENT,
    MARK_DEAD_LETTERED,
    LEASE_EXPIRED,
    CANCEL
}

