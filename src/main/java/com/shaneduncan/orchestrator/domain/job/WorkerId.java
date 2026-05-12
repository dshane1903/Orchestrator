package com.shaneduncan.orchestrator.domain.job;

public record WorkerId(String value) {

    public WorkerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("value is required");
        }
    }
}

