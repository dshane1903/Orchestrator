package com.shaneduncan.orchestrator.api.worker;

import com.shaneduncan.orchestrator.api.job.JobResponse;
import com.shaneduncan.orchestrator.application.worker.WorkerService;
import com.shaneduncan.orchestrator.domain.job.JobId;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping("/{workerId}/heartbeat")
    public ResponseEntity<WorkerResponse> heartbeat(
        @PathVariable String workerId,
        @RequestBody WorkerHeartbeatRequest request
    ) {
        Optional<JobId> currentJobId = Optional.ofNullable(request.currentJobId()).map(JobId::new);
        return ResponseEntity.ok(WorkerResponse.from(workerService.heartbeat(workerId, currentJobId)));
    }

    @PostMapping("/{workerId}/poll")
    public ResponseEntity<JobResponse> poll(
        @PathVariable String workerId,
        @Valid @RequestBody PollWorkerRequest request
    ) {
        return workerService.pollForJob(
                workerId,
                Duration.ofSeconds(request.leaseSeconds()),
                Duration.ofSeconds(request.waitSeconds())
            )
            .map(JobResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{workerId}/jobs/{jobId}/renew-lease")
    public ResponseEntity<JobResponse> renewLease(
        @PathVariable String workerId,
        @PathVariable UUID jobId,
        @Valid @RequestBody RenewLeaseRequest request
    ) {
        return ResponseEntity.ok(JobResponse.from(workerService.renewLease(
            new JobId(jobId),
            workerId,
            request.assignmentVersion(),
            Duration.ofSeconds(request.leaseSeconds())
        )));
    }

    @PostMapping("/{workerId}/jobs/{jobId}/complete")
    public ResponseEntity<JobResponse> completeJob(
        @PathVariable String workerId,
        @PathVariable UUID jobId,
        @Valid @RequestBody CompleteJobRequest request
    ) {
        return ResponseEntity.ok(JobResponse.from(workerService.completeJob(
            new JobId(jobId),
            workerId,
            request.assignmentVersion()
        )));
    }

    @PostMapping("/{workerId}/jobs/{jobId}/fail")
    public ResponseEntity<JobResponse> failJob(
        @PathVariable String workerId,
        @PathVariable UUID jobId,
        @Valid @RequestBody FailJobRequest request
    ) {
        return ResponseEntity.ok(JobResponse.from(workerService.failJob(
            new JobId(jobId),
            workerId,
            request.assignmentVersion(),
            request.reason(),
            request.retryable(),
            Duration.ofSeconds(request.retryDelaySeconds())
        )));
    }
}
