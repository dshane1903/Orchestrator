package com.shaneduncan.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forgeflow.scheduler.dead-letter")
public record DeadLetterSchedulerProperties(
    boolean enabled,
    int batchSize
) {

    public DeadLetterSchedulerProperties {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be at least 1");
        }
    }
}
