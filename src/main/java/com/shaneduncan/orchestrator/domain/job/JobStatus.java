package com.shaneduncan.orchestrator.domain.job;

public enum JobStatus {
    PENDING(false),
    RUNNING(false),
    RETRYING(false),
    SUCCEEDED(true),
    FAILED(true),
    CANCELLED(true),
    DEAD_LETTERED(true);

    private final boolean terminal;

    JobStatus(boolean terminal) {
        this.terminal = terminal;
    }

    public boolean isTerminal() {
        return terminal;
    }
}

