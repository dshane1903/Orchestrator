package com.shaneduncan.orchestrator.domain.job;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class Job {

    private final JobId id;
    private final String taskType;
    private final JobStatus status;
    private final int attemptCount;
    private final int maxAttempts;
    private final long assignmentVersion;
    private final WorkerId leasedBy;
    private final Instant leaseExpiresAt;
    private final Instant nextRunAt;
    private final String failureReason;
    private final Instant createdAt;
    private final Instant updatedAt;

    private Job(
        JobId id,
        String taskType,
        JobStatus status,
        int attemptCount,
        int maxAttempts,
        long assignmentVersion,
        WorkerId leasedBy,
        Instant leaseExpiresAt,
        Instant nextRunAt,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.taskType = requireText(taskType, "taskType is required");
        this.status = Objects.requireNonNull(status, "status is required");
        this.attemptCount = attemptCount;
        this.maxAttempts = maxAttempts;
        this.assignmentVersion = assignmentVersion;
        this.leasedBy = leasedBy;
        this.leaseExpiresAt = leaseExpiresAt;
        this.nextRunAt = nextRunAt;
        this.failureReason = failureReason;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    public static Job create(JobId id, String taskType, int maxAttempts, Instant now) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }

        return new Job(
            id,
            taskType,
            JobStatus.PENDING,
            0,
            maxAttempts,
            0,
            null,
            null,
            now,
            null,
            now,
            now
        );
    }

    public static Job restore(
        JobId id,
        String taskType,
        JobStatus status,
        int attemptCount,
        int maxAttempts,
        long assignmentVersion,
        WorkerId leasedBy,
        Instant leaseExpiresAt,
        Instant nextRunAt,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new Job(
            id,
            taskType,
            status,
            attemptCount,
            maxAttempts,
            assignmentVersion,
            leasedBy,
            leaseExpiresAt,
            nextRunAt,
            failureReason,
            createdAt,
            updatedAt
        );
    }

    public Job claim(WorkerId workerId, Instant leaseExpiresAt, Instant now) {
        Objects.requireNonNull(workerId, "workerId is required");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt is required");
        Objects.requireNonNull(now, "now is required");
        if (!leaseExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("leaseExpiresAt must be in the future");
        }

        return copy(
            JobStateMachine.transition(status, JobEvent.CLAIM),
            attemptCount + 1,
            assignmentVersion + 1,
            workerId,
            leaseExpiresAt,
            null,
            null,
            now
        );
    }

    public Job renewLease(
        WorkerId workerId,
        long observedAssignmentVersion,
        Instant leaseExpiresAt,
        Instant now
    ) {
        requireCurrentAssignment(workerId, observedAssignmentVersion);
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt is required");
        Objects.requireNonNull(now, "now is required");
        if (!leaseExpiresAt.isAfter(now)) {
            throw new IllegalArgumentException("leaseExpiresAt must be in the future");
        }

        return copy(
            JobStateMachine.transition(status, JobEvent.RENEW_LEASE),
            attemptCount,
            assignmentVersion,
            workerId,
            leaseExpiresAt,
            null,
            failureReason,
            now
        );
    }

    public Job complete(WorkerId workerId, long observedAssignmentVersion, Instant now) {
        requireCurrentAssignment(workerId, observedAssignmentVersion);
        return complete(observedAssignmentVersion, now);
    }

    public Job complete(long observedAssignmentVersion, Instant now) {
        requireFreshAssignment(observedAssignmentVersion);
        return copy(
            JobStateMachine.transition(status, JobEvent.COMPLETE),
            attemptCount,
            assignmentVersion,
            null,
            null,
            null,
            null,
            now
        );
    }

    public Job failForRetry(
        WorkerId workerId,
        long observedAssignmentVersion,
        String reason,
        Instant nextRunAt,
        Instant now
    ) {
        requireCurrentAssignment(workerId, observedAssignmentVersion);
        return failForRetry(observedAssignmentVersion, reason, nextRunAt, now);
    }

    public Job failForRetry(long observedAssignmentVersion, String reason, Instant nextRunAt, Instant now) {
        requireFreshAssignment(observedAssignmentVersion);
        Objects.requireNonNull(nextRunAt, "nextRunAt is required");

        return copy(
            JobStateMachine.transition(status, JobEvent.FAIL_RETRYABLE),
            attemptCount,
            assignmentVersion,
            null,
            null,
            nextRunAt,
            requireText(reason, "reason is required"),
            now
        );
    }

    public Job failPermanently(WorkerId workerId, long observedAssignmentVersion, String reason, Instant now) {
        requireCurrentAssignment(workerId, observedAssignmentVersion);
        return failPermanently(observedAssignmentVersion, reason, now);
    }

    public Job failPermanently(long observedAssignmentVersion, String reason, Instant now) {
        requireFreshAssignment(observedAssignmentVersion);

        return copy(
            JobStateMachine.transition(status, JobEvent.FAIL_PERMANENT),
            attemptCount,
            assignmentVersion,
            null,
            null,
            null,
            requireText(reason, "reason is required"),
            now
        );
    }

    public Job markDeadLettered(Instant now) {
        return copy(
            JobStateMachine.transition(status, JobEvent.MARK_DEAD_LETTERED),
            attemptCount,
            assignmentVersion,
            null,
            null,
            null,
            failureReason,
            now
        );
    }

    public Job expireLease(Instant now) {
        return copy(
            JobStateMachine.transition(status, JobEvent.LEASE_EXPIRED),
            attemptCount,
            assignmentVersion + 1,
            null,
            null,
            now,
            failureReason,
            now
        );
    }

    public Job cancel(Instant now) {
        return copy(
            JobStateMachine.transition(status, JobEvent.CANCEL),
            attemptCount,
            assignmentVersion,
            null,
            null,
            null,
            failureReason,
            now
        );
    }

    public JobId id() {
        return id;
    }

    public String taskType() {
        return taskType;
    }

    public JobStatus status() {
        return status;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public long assignmentVersion() {
        return assignmentVersion;
    }

    public Optional<WorkerId> leasedBy() {
        return Optional.ofNullable(leasedBy);
    }

    public Optional<Instant> leaseExpiresAt() {
        return Optional.ofNullable(leaseExpiresAt);
    }

    public Optional<Instant> nextRunAt() {
        return Optional.ofNullable(nextRunAt);
    }

    public Optional<String> failureReason() {
        return Optional.ofNullable(failureReason);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private void requireFreshAssignment(long observedAssignmentVersion) {
        if (observedAssignmentVersion != assignmentVersion) {
            throw new StaleJobAssignmentException(id, observedAssignmentVersion, assignmentVersion);
        }
    }

    private void requireCurrentAssignment(WorkerId workerId, long observedAssignmentVersion) {
        Objects.requireNonNull(workerId, "workerId is required");
        requireFreshAssignment(observedAssignmentVersion);
        if (!workerId.equals(leasedBy)) {
            WorkerId currentWorkerId = leasedBy == null ? new WorkerId("<unassigned>") : leasedBy;
            throw new JobAssignmentOwnershipException(id, workerId, currentWorkerId);
        }
    }

    private Job copy(
        JobStatus newStatus,
        int newAttemptCount,
        long newAssignmentVersion,
        WorkerId newLeasedBy,
        Instant newLeaseExpiresAt,
        Instant newNextRunAt,
        String newFailureReason,
        Instant newUpdatedAt
    ) {
        return new Job(
            id,
            taskType,
            newStatus,
            newAttemptCount,
            maxAttempts,
            newAssignmentVersion,
            newLeasedBy,
            newLeaseExpiresAt,
            newNextRunAt,
            newFailureReason,
            createdAt,
            newUpdatedAt
        );
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
