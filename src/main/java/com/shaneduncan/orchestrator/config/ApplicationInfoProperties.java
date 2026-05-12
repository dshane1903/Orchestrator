package com.shaneduncan.orchestrator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "forgeflow.application")
public record ApplicationInfoProperties(
    String name,
    String version
) {
}
