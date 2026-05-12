package com.shaneduncan.orchestrator.persistence.worker;

import static org.assertj.core.api.Assertions.assertThat;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import com.shaneduncan.orchestrator.persistence.job.JdbcJobRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
class JdbcWorkerRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("forgeflow")
        .withUsername("forgeflow")
        .withPassword("forgeflow");

    @Autowired
    private JdbcWorkerRepository workerRepository;

    @Autowired
    private JdbcJobRepository jobRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("forgeflow.scheduler.lease-recovery.enabled", () -> false);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM workers", Map.of());
        jdbcTemplate.update("DELETE FROM jobs", Map.of());
    }

    @Test
    void upsertsHeartbeatAndCurrentJob() {
        Instant now = Instant.parse("2026-05-13T05:00:00Z");
        WorkerId workerId = new WorkerId("worker-heartbeat-1");
        Job job = Job.create(
            new JobId(UUID.fromString("e23e4567-e89b-12d3-a456-426614174000")),
            "batch-inference",
            3,
            now
        );
        jobRepository.insert(job);

        workerRepository.upsertHeartbeat(workerId, Optional.empty(), now);
        workerRepository.upsertHeartbeat(workerId, Optional.of(job.id()), now.plusSeconds(5));

        assertThat(workerRepository.findById(workerId))
            .hasValueSatisfying(worker -> {
                assertThat(worker.currentJobId()).contains(job.id());
                assertThat(worker.lastHeartbeatAt()).isEqualTo(now.plusSeconds(5));
                assertThat(worker.createdAt()).isEqualTo(now);
                assertThat(worker.updatedAt()).isEqualTo(now.plusSeconds(5));
            });
    }
}
