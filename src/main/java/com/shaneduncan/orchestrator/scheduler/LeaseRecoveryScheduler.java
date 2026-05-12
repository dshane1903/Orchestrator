package com.shaneduncan.orchestrator.scheduler;

import com.shaneduncan.orchestrator.application.job.JobService;
import com.shaneduncan.orchestrator.config.LeaseRecoverySchedulerProperties;
import com.shaneduncan.orchestrator.domain.job.Job;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LeaseRecoveryScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(LeaseRecoveryScheduler.class);

    private final JobService jobService;
    private final LeaseRecoverySchedulerProperties properties;

    public LeaseRecoveryScheduler(
        JobService jobService,
        LeaseRecoverySchedulerProperties properties
    ) {
        this.jobService = jobService;
        this.properties = properties;
    }

    @Scheduled(
        initialDelayString = "${forgeflow.scheduler.lease-recovery.initial-delay-ms}",
        fixedDelayString = "${forgeflow.scheduler.lease-recovery.fixed-delay-ms}"
    )
    public void recoverExpiredLeases() {
        if (!properties.enabled()) {
            return;
        }

        List<Job> recoveredJobs = jobService.recoverExpiredLeases(properties.batchSize());
        if (!recoveredJobs.isEmpty()) {
            LOGGER.info("Recovered {} expired job leases", recoveredJobs.size());
        }
    }
}
