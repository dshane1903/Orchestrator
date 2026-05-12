package com.shaneduncan.orchestrator.api;

import java.time.Instant;

public record SystemInfoResponse(
    String name,
    String version,
    Instant serverTime
) {
}

