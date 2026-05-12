package com.shaneduncan.orchestrator.persistence.worker;

import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import com.shaneduncan.orchestrator.domain.worker.WorkerNode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcWorkerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcWorkerRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WorkerNode upsertHeartbeat(WorkerId workerId, Optional<JobId> currentJobId, Instant now) {
        return jdbcTemplate.queryForObject(
            """
                INSERT INTO workers (
                    id,
                    current_job_id,
                    last_heartbeat_at,
                    created_at,
                    updated_at
                )
                VALUES (
                    :id,
                    :currentJobId,
                    :now,
                    :now,
                    :now
                )
                ON CONFLICT (id)
                DO UPDATE SET
                    current_job_id = EXCLUDED.current_job_id,
                    last_heartbeat_at = EXCLUDED.last_heartbeat_at,
                    updated_at = EXCLUDED.updated_at
                RETURNING
                    id,
                    current_job_id,
                    last_heartbeat_at,
                    created_at,
                    updated_at
                """,
            new MapSqlParameterSource()
                .addValue("id", workerId.value())
                .addValue("currentJobId", currentJobId.map(JobId::value).orElse(null))
                .addValue("now", timestamp(now)),
            this::mapWorker
        );
    }

    public Optional<WorkerNode> findById(WorkerId workerId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                """
                    SELECT
                        id,
                        current_job_id,
                        last_heartbeat_at,
                        created_at,
                        updated_at
                    FROM workers
                    WHERE id = :id
                    """,
                Map.of("id", workerId.value()),
                this::mapWorker
            ));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private WorkerNode mapWorker(ResultSet resultSet, int rowNumber) throws SQLException {
        return new WorkerNode(
            new WorkerId(resultSet.getString("id")),
            nullableJobId(resultSet.getObject("current_job_id", UUID.class)),
            nullableInstant(resultSet, "last_heartbeat_at"),
            nullableInstant(resultSet, "created_at"),
            nullableInstant(resultSet, "updated_at")
        );
    }

    private JobId nullableJobId(UUID value) {
        return value == null ? null : new JobId(value);
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private Timestamp timestamp(Instant instant) {
        return Timestamp.from(instant);
    }
}
