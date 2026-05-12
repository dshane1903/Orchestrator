package com.shaneduncan.orchestrator.persistence.job;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.JobStatus;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcJobRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcJobRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Job job) {
        jdbcTemplate.update(
            """
                INSERT INTO jobs (
                    id,
                    task_type,
                    status,
                    attempt_count,
                    max_attempts,
                    assignment_version,
                    leased_by,
                    lease_expires_at,
                    next_run_at,
                    failure_reason,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :taskType,
                    :status,
                    :attemptCount,
                    :maxAttempts,
                    :assignmentVersion,
                    :leasedBy,
                    :leaseExpiresAt,
                    :nextRunAt,
                    :failureReason,
                    :createdAt,
                    :updatedAt
                )
                """,
            parameters(job)
        );
    }

    public Optional<Job> findById(JobId id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    SELECT
                        id,
                        task_type,
                        status,
                        attempt_count,
                        max_attempts,
                        assignment_version,
                        leased_by,
                        lease_expires_at,
                        next_run_at,
                        failure_reason,
                        created_at,
                        updated_at
                    FROM jobs
                    WHERE id = :id
                    """,
                Map.of("id", id.value()),
                this::mapJob
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    public Optional<Job> claimNextRunnable(WorkerId workerId, Instant now, Instant leaseExpiresAt) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    UPDATE jobs
                    SET
                        status = 'RUNNING',
                        attempt_count = attempt_count + 1,
                        assignment_version = assignment_version + 1,
                        leased_by = :workerId,
                        lease_expires_at = :leaseExpiresAt,
                        next_run_at = NULL,
                        failure_reason = NULL,
                        updated_at = :now
                    WHERE id = (
                        SELECT id
                        FROM jobs
                        WHERE status IN ('PENDING', 'RETRYING')
                          AND next_run_at <= :now
                          AND attempt_count < max_attempts
                        ORDER BY next_run_at ASC, created_at ASC
                        FOR UPDATE SKIP LOCKED
                        LIMIT 1
                    )
                    RETURNING
                        id,
                        task_type,
                        status,
                        attempt_count,
                        max_attempts,
                        assignment_version,
                        leased_by,
                        lease_expires_at,
                        next_run_at,
                        failure_reason,
                        created_at,
                        updated_at
                    """,
                new MapSqlParameterSource()
                    .addValue("workerId", workerId.value())
                    .addValue("now", timestamp(now))
                    .addValue("leaseExpiresAt", timestamp(leaseExpiresAt)),
                this::mapJob
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    public List<Job> recoverExpiredLeases(Instant now, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }

        return jdbcTemplate.query(
            """
                UPDATE jobs
                SET
                    status = 'PENDING',
                    assignment_version = assignment_version + 1,
                    leased_by = NULL,
                    lease_expires_at = NULL,
                    next_run_at = :now,
                    updated_at = :now
                WHERE id IN (
                    SELECT id
                    FROM jobs
                    WHERE status = 'RUNNING'
                      AND lease_expires_at <= :now
                    ORDER BY lease_expires_at ASC, updated_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT :limit
                )
                RETURNING
                    id,
                    task_type,
                    status,
                    attempt_count,
                    max_attempts,
                    assignment_version,
                    leased_by,
                    lease_expires_at,
                    next_run_at,
                    failure_reason,
                    created_at,
                    updated_at
                """,
            new MapSqlParameterSource()
                .addValue("now", timestamp(now))
                .addValue("limit", limit),
            this::mapJob
        );
    }

    public boolean updateIfAssignmentVersionMatches(Job job, long expectedAssignmentVersion) {
        int updatedRows = jdbcTemplate.update(
            """
                UPDATE jobs
                SET
                    task_type = :taskType,
                    status = :status,
                    attempt_count = :attemptCount,
                    max_attempts = :maxAttempts,
                    assignment_version = :assignmentVersion,
                    leased_by = :leasedBy,
                    lease_expires_at = :leaseExpiresAt,
                    next_run_at = :nextRunAt,
                    failure_reason = :failureReason,
                    updated_at = :updatedAt
                WHERE id = :id
                  AND assignment_version = :expectedAssignmentVersion
                """,
            parameters(job).addValue("expectedAssignmentVersion", expectedAssignmentVersion)
        );

        return updatedRows == 1;
    }

    private MapSqlParameterSource parameters(Job job) {
        return new MapSqlParameterSource()
            .addValue("id", job.id().value())
            .addValue("taskType", job.taskType())
            .addValue("status", job.status().name())
            .addValue("attemptCount", job.attemptCount())
            .addValue("maxAttempts", job.maxAttempts())
            .addValue("assignmentVersion", job.assignmentVersion())
            .addValue("leasedBy", job.leasedBy().map(WorkerId::value).orElse(null))
            .addValue("leaseExpiresAt", timestamp(job.leaseExpiresAt().orElse(null)))
            .addValue("nextRunAt", timestamp(job.nextRunAt().orElse(null)))
            .addValue("failureReason", job.failureReason().orElse(null))
            .addValue("createdAt", timestamp(job.createdAt()))
            .addValue("updatedAt", timestamp(job.updatedAt()));
    }

    private Job mapJob(ResultSet resultSet, int rowNumber) throws SQLException {
        return Job.restore(
            new JobId(resultSet.getObject("id", UUID.class)),
            resultSet.getString("task_type"),
            JobStatus.valueOf(resultSet.getString("status")),
            resultSet.getInt("attempt_count"),
            resultSet.getInt("max_attempts"),
            resultSet.getLong("assignment_version"),
            nullableWorkerId(resultSet.getString("leased_by")),
            nullableInstant(resultSet, "lease_expires_at"),
            nullableInstant(resultSet, "next_run_at"),
            resultSet.getString("failure_reason"),
            nullableInstant(resultSet, "created_at"),
            nullableInstant(resultSet, "updated_at")
        );
    }

    private WorkerId nullableWorkerId(String value) {
        return value == null ? null : new WorkerId(value);
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
