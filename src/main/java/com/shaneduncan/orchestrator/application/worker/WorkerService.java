package com.shaneduncan.orchestrator.application.worker;

import com.shaneduncan.orchestrator.application.job.JobService;
import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import com.shaneduncan.orchestrator.domain.worker.WorkerNode;
import com.shaneduncan.orchestrator.persistence.worker.JdbcWorkerRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class WorkerService {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(250);
    private static final Duration MAX_WAIT = Duration.ofSeconds(30);

    private final JdbcWorkerRepository workerRepository;
    private final JobService jobService;
    private final Clock clock;

    public WorkerService(
        JdbcWorkerRepository workerRepository,
        JobService jobService,
        Clock clock
    ) {
        this.workerRepository = workerRepository;
        this.jobService = jobService;
        this.clock = clock;
    }

    public WorkerNode heartbeat(String workerId, Optional<JobId> currentJobId) {
        return workerRepository.upsertHeartbeat(
            new WorkerId(workerId),
            currentJobId,
            Instant.now(clock)
        );
    }

    public Optional<Job> pollForJob(String workerId, Duration leaseDuration, Duration waitDuration) {
        requirePositive(leaseDuration, "leaseDuration");
        if (waitDuration.isNegative()) {
            throw new IllegalArgumentException("waitDuration must not be negative");
        }
        if (waitDuration.compareTo(MAX_WAIT) > 0) {
            throw new IllegalArgumentException("waitDuration must be 30 seconds or less");
        }

        Instant deadline = Instant.now(clock).plus(waitDuration);
        while (true) {
            heartbeat(workerId, Optional.empty());
            Optional<Job> claimed = jobService.claimNextRunnable(workerId, leaseDuration);
            if (claimed.isPresent()) {
                heartbeat(workerId, Optional.of(claimed.get().id()));
                return claimed;
            }
            if (!Instant.now(clock).isBefore(deadline)) {
                return Optional.empty();
            }
            sleepUntilNextPoll(deadline);
        }
    }

    public Job renewLease(JobId id, String workerId, long assignmentVersion, Duration leaseDuration) {
        Job renewed = jobService.renewLease(id, workerId, assignmentVersion, leaseDuration);
        heartbeat(workerId, Optional.of(id));
        return renewed;
    }

    public Job completeJob(JobId id, String workerId, long assignmentVersion) {
        Job completed = jobService.completeJob(id, workerId, assignmentVersion);
        heartbeat(workerId, Optional.empty());
        return completed;
    }

    public Job failJob(
        JobId id,
        String workerId,
        long assignmentVersion,
        String reason,
        boolean retryable,
        Duration retryDelay
    ) {
        Job failed = jobService.failJob(id, workerId, assignmentVersion, reason, retryable, retryDelay);
        heartbeat(workerId, Optional.empty());
        return failed;
    }

    private void sleepUntilNextPoll(Instant deadline) {
        Duration remaining = Duration.between(Instant.now(clock), deadline);
        Duration sleepDuration = remaining.compareTo(POLL_INTERVAL) < 0 ? remaining : POLL_INTERVAL;
        if (sleepDuration.isNegative() || sleepDuration.isZero()) {
            return;
        }

        try {
            Thread.sleep(sleepDuration.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
