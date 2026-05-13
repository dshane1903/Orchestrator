package com.shaneduncan.orchestrator.application.job;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.StaleJobAssignmentException;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import com.shaneduncan.orchestrator.observability.JobMetrics;
import com.shaneduncan.orchestrator.persistence.job.JdbcJobRepository;
import com.shaneduncan.orchestrator.persistence.workflow.JdbcWorkflowRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

    private final JdbcJobRepository jobRepository;
    private final JdbcWorkflowRepository workflowRepository;
    private final JobMetrics jobMetrics;
    private final Clock clock;

    public JobService(
        JdbcJobRepository jobRepository,
        JdbcWorkflowRepository workflowRepository,
        JobMetrics jobMetrics,
        Clock clock
    ) {
        this.jobRepository = jobRepository;
        this.workflowRepository = workflowRepository;
        this.jobMetrics = jobMetrics;
        this.clock = clock;
    }

    public Job createJob(String taskType, int maxAttempts) {
        return createJob(taskType, maxAttempts, null);
    }

    public Job createJob(String taskType, int maxAttempts, String idempotencyKey) {
        Optional<String> normalizedIdempotencyKey = normalize(idempotencyKey);
        if (normalizedIdempotencyKey.isPresent()) {
            Optional<Job> existingJob = jobRepository.findByIdempotencyKey(normalizedIdempotencyKey.get());
            if (existingJob.isPresent()) {
                return existingJob.get();
            }
        }

        Job job = Job.create(JobId.newId(), taskType, maxAttempts, Instant.now(clock));
        jobRepository.insert(job, normalizedIdempotencyKey.orElse(null));
        return job;
    }

    public Optional<Job> findJob(JobId id) {
        return jobRepository.findById(id);
    }

    public Optional<Job> claimNextRunnable(String workerId, Duration leaseDuration) {
        requirePositive(leaseDuration, "leaseDuration");

        Instant now = Instant.now(clock);
        Optional<Job> claimedJob = jobRepository.claimNextRunnable(
            new WorkerId(workerId),
            now,
            now.plus(leaseDuration)
        );
        claimedJob.ifPresent(jobMetrics::recordClaim);
        return claimedJob;
    }

    public Job renewLease(JobId id, String workerId, long assignmentVersion, Duration leaseDuration) {
        requirePositive(leaseDuration, "leaseDuration");
        Instant now = Instant.now(clock);
        Job current = findExisting(id);
        Job renewed = current.renewLease(
            new WorkerId(workerId),
            assignmentVersion,
            now.plus(leaseDuration),
            now
        );
        return persistFenced(renewed, assignmentVersion);
    }

    @Transactional
    public Job completeJob(JobId id, String workerId, long assignmentVersion) {
        Instant now = Instant.now(clock);
        Job current = findExisting(id);
        Job completed = current.complete(new WorkerId(workerId), assignmentVersion, now);
        Job persisted = persistFenced(completed, assignmentVersion);
        workflowRepository.promoteReadyDependents(id, now);
        workflowRepository.markWorkflowSucceededIfComplete(id, now);
        return persisted;
    }

    @Transactional
    public Job failJob(
        JobId id,
        String workerId,
        long assignmentVersion,
        String reason,
        boolean retryable,
        Duration retryDelay
    ) {
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay must not be negative");
        }

        Instant now = Instant.now(clock);
        Job current = findExisting(id);
        WorkerId currentWorker = new WorkerId(workerId);
        Job failed = retryable && current.attemptCount() < current.maxAttempts()
            ? current.failForRetry(currentWorker, assignmentVersion, reason, now.plus(retryDelay), now)
            : current.failPermanently(currentWorker, assignmentVersion, reason, now);
        Job persisted = persistFenced(failed, assignmentVersion);
        if (persisted.status().isTerminal()) {
            workflowRepository.markWorkflowFailedForJob(id, now);
        }
        return persisted;
    }

    public List<Job> recoverExpiredLeases(int batchSize) {
        List<Job> recoveredJobs = jobRepository.recoverExpiredLeases(Instant.now(clock), batchSize);
        jobMetrics.recordRecoveredExpiredLeases(recoveredJobs.size());
        return recoveredJobs;
    }

    private Job findExisting(JobId id) {
        return jobRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Job not found: " + id.value()));
    }

    private Job persistFenced(Job job, long expectedAssignmentVersion) {
        if (jobRepository.updateIfAssignmentVersionMatches(job, expectedAssignmentVersion)) {
            return job;
        }

        long currentVersion = jobRepository.findById(job.id())
            .map(Job::assignmentVersion)
            .orElse(-1L);
        throw new StaleJobAssignmentException(job.id(), expectedAssignmentVersion, currentVersion);
    }

    private void requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }

    private Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }
}
