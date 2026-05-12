package com.shaneduncan.orchestrator.application.job;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import com.shaneduncan.orchestrator.persistence.job.JdbcJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class JobService {

    private final JdbcJobRepository jobRepository;
    private final Clock clock;

    public JobService(JdbcJobRepository jobRepository, Clock clock) {
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    public Job createJob(String taskType, int maxAttempts) {
        Job job = Job.create(JobId.newId(), taskType, maxAttempts, Instant.now(clock));
        jobRepository.insert(job);
        return job;
    }

    public Optional<Job> findJob(JobId id) {
        return jobRepository.findById(id);
    }

    public Optional<Job> claimNextRunnable(String workerId, Duration leaseDuration) {
        if (leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }

        Instant now = Instant.now(clock);
        return jobRepository.claimNextRunnable(
            new WorkerId(workerId),
            now,
            now.plus(leaseDuration)
        );
    }
}

