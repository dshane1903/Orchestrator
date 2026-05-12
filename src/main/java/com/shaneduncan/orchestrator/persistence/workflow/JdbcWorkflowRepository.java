package com.shaneduncan.orchestrator.persistence.workflow;

import com.shaneduncan.orchestrator.application.workflow.WorkflowDetails;
import com.shaneduncan.orchestrator.application.workflow.WorkflowJobDetails;
import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.JobStatus;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import com.shaneduncan.orchestrator.domain.workflow.Workflow;
import com.shaneduncan.orchestrator.domain.workflow.WorkflowId;
import com.shaneduncan.orchestrator.domain.workflow.WorkflowStatus;
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
public class JdbcWorkflowRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkflowRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertWorkflow(Workflow workflow) {
        jdbcTemplate.update(
            """
                INSERT INTO workflows (
                    id,
                    name,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :name,
                    :status,
                    :createdAt,
                    :updatedAt
                )
                """,
            new MapSqlParameterSource()
                .addValue("id", workflow.id().value())
                .addValue("name", workflow.name())
                .addValue("status", workflow.status().name())
                .addValue("createdAt", timestamp(workflow.createdAt()))
                .addValue("updatedAt", timestamp(workflow.updatedAt()))
        );
    }

    public void linkJob(WorkflowId workflowId, JobId jobId, String nodeKey) {
        jdbcTemplate.update(
            """
                INSERT INTO workflow_jobs (
                    workflow_id,
                    job_id,
                    node_key
                )
                VALUES (
                    :workflowId,
                    :jobId,
                    :nodeKey
                )
                """,
            new MapSqlParameterSource()
                .addValue("workflowId", workflowId.value())
                .addValue("jobId", jobId.value())
                .addValue("nodeKey", nodeKey)
        );
    }

    public void insertDependency(WorkflowId workflowId, JobId upstreamJobId, JobId downstreamJobId) {
        jdbcTemplate.update(
            """
                INSERT INTO job_dependencies (
                    workflow_id,
                    upstream_job_id,
                    downstream_job_id
                )
                VALUES (
                    :workflowId,
                    :upstreamJobId,
                    :downstreamJobId
                )
                """,
            new MapSqlParameterSource()
                .addValue("workflowId", workflowId.value())
                .addValue("upstreamJobId", upstreamJobId.value())
                .addValue("downstreamJobId", downstreamJobId.value())
        );
    }

    public Optional<WorkflowDetails> findDetailsById(WorkflowId workflowId) {
        try {
            Workflow workflow = jdbcTemplate.queryForObject(
                """
                    SELECT
                        id,
                        name,
                        status,
                        created_at,
                        updated_at
                    FROM workflows
                    WHERE id = :id
                    """,
                Map.of("id", workflowId.value()),
                this::mapWorkflow
            );
            return Optional.of(new WorkflowDetails(workflow, findJobs(workflowId)));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    @Transactional
    public List<Job> promoteReadyDependents(JobId completedJobId, Instant now) {
        return jdbcTemplate.query(
            """
                UPDATE jobs
                SET
                    status = 'PENDING',
                    next_run_at = :now,
                    updated_at = :now
                WHERE status = 'BLOCKED'
                  AND id IN (
                      SELECT downstream_job_id
                      FROM job_dependencies
                      WHERE upstream_job_id = :completedJobId
                  )
                  AND NOT EXISTS (
                      SELECT 1
                      FROM job_dependencies dependency
                      JOIN jobs upstream ON upstream.id = dependency.upstream_job_id
                      WHERE dependency.downstream_job_id = jobs.id
                        AND upstream.status <> 'SUCCEEDED'
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
                .addValue("completedJobId", completedJobId.value())
                .addValue("now", timestamp(now)),
            this::mapJob
        );
    }

    public void markWorkflowSucceededIfComplete(JobId jobId, Instant now) {
        jdbcTemplate.update(
            """
                UPDATE workflows
                SET
                    status = 'SUCCEEDED',
                    updated_at = :now
                WHERE id = (
                    SELECT workflow_id
                    FROM workflow_jobs
                    WHERE job_id = :jobId
                )
                  AND status = 'RUNNING'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM workflow_jobs
                      JOIN jobs ON jobs.id = workflow_jobs.job_id
                      WHERE workflow_jobs.workflow_id = workflows.id
                        AND jobs.status <> 'SUCCEEDED'
                  )
                """,
            new MapSqlParameterSource()
                .addValue("jobId", jobId.value())
                .addValue("now", timestamp(now))
        );
    }

    public void markWorkflowFailedForJob(JobId jobId, Instant now) {
        jdbcTemplate.update(
            """
                UPDATE workflows
                SET
                    status = 'FAILED',
                    updated_at = :now
                WHERE id = (
                    SELECT workflow_id
                    FROM workflow_jobs
                    WHERE job_id = :jobId
                )
                  AND status = 'RUNNING'
                """,
            new MapSqlParameterSource()
                .addValue("jobId", jobId.value())
                .addValue("now", timestamp(now))
        );
    }

    private List<WorkflowJobDetails> findJobs(WorkflowId workflowId) {
        return jdbcTemplate.query(
            """
                SELECT
                    workflow_jobs.node_key,
                    jobs.id,
                    jobs.task_type,
                    jobs.status,
                    jobs.attempt_count,
                    jobs.max_attempts,
                    jobs.assignment_version,
                    jobs.leased_by,
                    jobs.lease_expires_at,
                    jobs.next_run_at,
                    jobs.failure_reason,
                    jobs.created_at,
                    jobs.updated_at
                FROM workflow_jobs
                JOIN jobs ON jobs.id = workflow_jobs.job_id
                WHERE workflow_jobs.workflow_id = :workflowId
                ORDER BY (
                    SELECT COUNT(*)
                    FROM job_dependencies
                    WHERE job_dependencies.downstream_job_id = jobs.id
                ), workflow_jobs.node_key
                """,
            Map.of("workflowId", workflowId.value()),
            (resultSet, rowNumber) -> new WorkflowJobDetails(
                resultSet.getString("node_key"),
                mapJob(resultSet, rowNumber)
            )
        );
    }

    private Workflow mapWorkflow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Workflow(
            new WorkflowId(resultSet.getObject("id", UUID.class)),
            resultSet.getString("name"),
            WorkflowStatus.valueOf(resultSet.getString("status")),
            nullableInstant(resultSet, "created_at"),
            nullableInstant(resultSet, "updated_at")
        );
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
        return Timestamp.from(instant);
    }
}
