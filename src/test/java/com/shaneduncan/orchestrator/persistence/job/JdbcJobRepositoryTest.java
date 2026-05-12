package com.shaneduncan.orchestrator.persistence.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.JobStatus;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class JdbcJobRepositoryTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("forgeflow")
        .withUsername("forgeflow")
        .withPassword("forgeflow");

    @Autowired
    private JdbcJobRepository repository;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void insertsAndLoadsJob() {
        Instant now = Instant.parse("2026-05-12T18:00:00Z");
        JobId jobId = new JobId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));
        Job job = Job.create(jobId, "batch-inference", 3, now);

        repository.insert(job);

        assertThat(repository.findById(jobId))
            .hasValueSatisfying(loaded -> {
                assertThat(loaded.id()).isEqualTo(job.id());
                assertThat(loaded.taskType()).isEqualTo(job.taskType());
                assertThat(loaded.status()).isEqualTo(job.status());
                assertThat(loaded.attemptCount()).isEqualTo(job.attemptCount());
                assertThat(loaded.maxAttempts()).isEqualTo(job.maxAttempts());
                assertThat(loaded.assignmentVersion()).isEqualTo(job.assignmentVersion());
                assertThat(loaded.nextRunAt()).contains(now);
            });
    }

    @Test
    void updatesOnlyWhenAssignmentVersionMatches() {
        Instant now = Instant.parse("2026-05-12T19:00:00Z");
        JobId jobId = new JobId(UUID.fromString("223e4567-e89b-12d3-a456-426614174000"));
        Job job = Job.create(jobId, "embedding-generation", 3, now);
        repository.insert(job);

        Job claimed = job.claim(new WorkerId("worker-1"), now.plusSeconds(30), now.plusSeconds(1));

        assertThat(repository.updateIfAssignmentVersionMatches(claimed, 7)).isFalse();
        assertThat(repository.findById(jobId))
            .hasValueSatisfying(loaded -> assertThat(loaded.status()).isEqualTo(JobStatus.PENDING));

        assertThat(repository.updateIfAssignmentVersionMatches(claimed, 0)).isTrue();

        Job loaded = repository.findById(jobId).orElseThrow();
        assertThat(loaded.status()).isEqualTo(JobStatus.RUNNING);
        assertThat(loaded.assignmentVersion()).isEqualTo(1);
        assertThat(loaded.leasedBy()).contains(new WorkerId("worker-1"));
        assertThat(loaded.leaseExpiresAt()).contains(now.plusSeconds(30));
    }
}
