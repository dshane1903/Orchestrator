package com.shaneduncan.orchestrator.api.job;

import com.shaneduncan.orchestrator.application.job.JobService;
import com.shaneduncan.orchestrator.domain.job.JobId;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobResponse response = JobResponse.from(jobService.createJob(request.taskType(), request.maxAttempts()));
        return ResponseEntity
            .created(URI.create("/api/jobs/" + response.id()))
            .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID id) {
        return jobService.findJob(new JobId(id))
            .map(JobResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/claim")
    public ResponseEntity<JobResponse> claimJob(@Valid @RequestBody ClaimJobRequest request) {
        return jobService.claimNextRunnable(request.workerId(), Duration.ofSeconds(request.leaseSeconds()))
            .map(JobResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }
}

