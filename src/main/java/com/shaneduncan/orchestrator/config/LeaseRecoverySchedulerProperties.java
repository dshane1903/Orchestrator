package com.shaneduncan.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forgeflow.scheduler.lease-recovery")
public record LeaseRecoverySchedulerProperties(
    boolean enabled,
    int batchSize
) {

    public LeaseRecoverySchedulerProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1");
        }
    }
}

