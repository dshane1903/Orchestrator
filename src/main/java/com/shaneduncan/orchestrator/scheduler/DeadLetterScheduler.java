package com.shaneduncan.orchestrator.scheduler;

import com.shaneduncan.orchestrator.application.job.JobService;
import com.shaneduncan.orchestrator.config.DeadLetterSchedulerProperties;
import com.shaneduncan.orchestrator.domain.job.Job;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeadLetterScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeadLetterScheduler.class);

    private final JobService jobService;
    private final DeadLetterSchedulerProperties properties;

    public DeadLetterScheduler(
        JobService jobService,
        DeadLetterSchedulerProperties properties
    ) {
        this.jobService = jobService;
        this.properties = properties;
    }

    @Scheduled(
        initialDelayString = "${forgeflow.scheduler.dead-letter.initial-delay-ms}",
        fixedDelayString = "${forgeflow.scheduler.dead-letter.fixed-delay-ms}"
    )
    public void moveFailedJobsToDeadLetter() {
        if (!properties.enabled()) {
            return;
        }

        List<Job> deadLetteredJobs = jobService.moveFailedJobsToDeadLetter(properties.batchSize());
        if (!deadLetteredJobs.isEmpty()) {
            LOGGER.info("Moved {} failed jobs to the dead-letter queue", deadLetteredJobs.size());
        }
    }
}
