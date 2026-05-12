package com.shaneduncan.orchestrator.api;

import com.shaneduncan.orchestrator.config.ApplicationInfoProperties;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final ApplicationInfoProperties applicationInfo;

    public SystemController(ApplicationInfoProperties applicationInfo) {
        this.applicationInfo = applicationInfo;
    }

    @GetMapping
    public SystemInfoResponse getSystemInfo() {
        return new SystemInfoResponse(
            applicationInfo.name(),
            applicationInfo.version(),
            Instant.now()
        );
    }
}

