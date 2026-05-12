package com.shaneduncan.orchestrator.domain.job;

import java.util.Objects;
import java.util.UUID;

public record JobId(UUID value) {

    public JobId {
        Objects.requireNonNull(value, "value is required");
    }

    public static JobId newId() {
        return new JobId(UUID.randomUUID());
    }
}

