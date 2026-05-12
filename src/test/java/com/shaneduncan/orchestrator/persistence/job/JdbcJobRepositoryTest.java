package com.shaneduncan.orchestrator.persistence.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.job.JobStatus;
import com.shaneduncan.orchestrator.domain.job.WorkerId;
import java.time.Instant;
import java.util.List;
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
class JdbcJobRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("forgeflow")
        .withUsername("forgeflow")
        .withPassword("forgeflow");

    @Autowired
    private JdbcJobRepository repository;

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
        jdbcTemplate.update("DELETE FROM jobs", Map.of());
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

    @Test
    void atomicallyClaimsOldestRunnableJob() {
        Instant now = Instant.parse("2026-05-12T20:00:00Z");
        Job newerJob = Job.create(
            new JobId(UUID.fromString("323e4567-e89b-12d3-a456-426614174000")),
            "batch-inference",
            3,
            now.minusSeconds(30)
        );
        Job olderJob = Job.create(
            new JobId(UUID.fromString("423e4567-e89b-12d3-a456-426614174000")),
            "embedding-generation",
            3,
            now.minusSeconds(60)
        );
        repository.insert(newerJob);
        repository.insert(olderJob);

        Optional<Job> claimed = repository.claimNextRunnable(
            new WorkerId("worker-claimant"),
            now,
            now.plusSeconds(45)
        );

        assertThat(claimed)
            .hasValueSatisfying(job -> {
                assertThat(job.id()).isEqualTo(olderJob.id());
                assertThat(job.status()).isEqualTo(JobStatus.RUNNING);
                assertThat(job.attemptCount()).isEqualTo(1);
                assertThat(job.assignmentVersion()).isEqualTo(1);
                assertThat(job.leasedBy()).contains(new WorkerId("worker-claimant"));
                assertThat(job.leaseExpiresAt()).contains(now.plusSeconds(45));
                assertThat(job.nextRunAt()).isEmpty();
            });

        assertThat(repository.findById(newerJob.id()))
            .hasValueSatisfying(job -> assertThat(job.status()).isEqualTo(JobStatus.PENDING));
    }

    @Test
    void doesNotClaimFutureRetry() {
        Instant now = Instant.parse("2026-05-12T21:00:00Z");
        Job retrying = Job.create(
                new JobId(UUID.fromString("523e4567-e89b-12d3-a456-426614174000")),
                "embedding-generation",
                3,
                now.minusSeconds(30)
            )
            .claim(new WorkerId("worker-1"), now.minusSeconds(10), now.minusSeconds(20))
            .failForRetry(1, "temporary failure", now.plusSeconds(60), now.minusSeconds(5));
        repository.insert(retrying);

        assertThat(repository.claimNextRunnable(new WorkerId("worker-2"), now, now.plusSeconds(30)))
            .isEmpty();
    }

    @Test
    void doesNotClaimMoreThanMaxAttempts() {
        Instant now = Instant.parse("2026-05-12T22:00:00Z");
        Job exhausted = Job.create(
                new JobId(UUID.fromString("623e4567-e89b-12d3-a456-426614174000")),
                "batch-inference",
                1,
                now.minusSeconds(30)
            )
            .claim(new WorkerId("worker-1"), now.minusSeconds(10), now.minusSeconds(20))
            .failForRetry(1, "temporary failure", now.minusSeconds(1), now.minusSeconds(5));
        repository.insert(exhausted);

        assertThat(repository.claimNextRunnable(new WorkerId("worker-2"), now, now.plusSeconds(30)))
            .isEmpty();
    }

    @Test
    void subsequentClaimSkipsAlreadyClaimedJob() {
        Instant now = Instant.parse("2026-05-12T23:00:00Z");
        Job job = Job.create(
            new JobId(UUID.fromString("723e4567-e89b-12d3-a456-426614174000")),
            "batch-inference",
            3,
            now.minusSeconds(30)
        );
        repository.insert(job);

        Optional<Job> firstClaim = repository.claimNextRunnable(
            new WorkerId("worker-1"),
            now,
            now.plusSeconds(30)
        );
        Optional<Job> secondClaim = repository.claimNextRunnable(
            new WorkerId("worker-2"),
            now.plusSeconds(1),
            now.plusSeconds(31)
        );

        assertThat(firstClaim).isPresent();
        assertThat(secondClaim).isEmpty();
        assertThat(repository.findById(job.id()))
            .hasValueSatisfying(loaded -> assertThat(loaded.leasedBy()).contains(new WorkerId("worker-1")));
    }

    @Test
    void recoversExpiredRunningLeases() {
        Instant now = Instant.parse("2026-05-13T00:00:00Z");
        Job expired = Job.create(
                new JobId(UUID.fromString("823e4567-e89b-12d3-a456-426614174000")),
                "embedding-generation",
                3,
                now.minusSeconds(60)
            )
            .claim(new WorkerId("worker-1"), now.minusSeconds(1), now.minusSeconds(30));
        Job active = Job.create(
                new JobId(UUID.fromString("923e4567-e89b-12d3-a456-426614174000")),
                "batch-inference",
                3,
                now.minusSeconds(60)
            )
            .claim(new WorkerId("worker-2"), now.plusSeconds(60), now.minusSeconds(30));
        repository.insert(expired);
        repository.insert(active);

        List<Job> recovered = repository.recoverExpiredLeases(now, 10);

        assertThat(recovered)
            .singleElement()
            .satisfies(job -> {
                assertThat(job.id()).isEqualTo(expired.id());
                assertThat(job.status()).isEqualTo(JobStatus.PENDING);
                assertThat(job.assignmentVersion()).isEqualTo(2);
                assertThat(job.leasedBy()).isEmpty();
                assertThat(job.leaseExpiresAt()).isEmpty();
                assertThat(job.nextRunAt()).contains(now);
            });
        assertThat(repository.findById(active.id()))
            .hasValueSatisfying(job -> {
                assertThat(job.status()).isEqualTo(JobStatus.RUNNING);
                assertThat(job.leasedBy()).contains(new WorkerId("worker-2"));
            });
    }

    @Test
    void recoveredJobCanBeClaimedWithNewFencingVersion() {
        Instant now = Instant.parse("2026-05-13T01:00:00Z");
        Job expired = Job.create(
                new JobId(UUID.fromString("a23e4567-e89b-12d3-a456-426614174000")),
                "embedding-generation",
                3,
                now.minusSeconds(60)
            )
            .claim(new WorkerId("worker-1"), now.minusSeconds(1), now.minusSeconds(30));
        repository.insert(expired);

        repository.recoverExpiredLeases(now, 10);
        Optional<Job> reclaimed = repository.claimNextRunnable(
            new WorkerId("worker-2"),
            now.plusSeconds(1),
            now.plusSeconds(31)
        );

        assertThat(reclaimed)
            .hasValueSatisfying(job -> {
                assertThat(job.status()).isEqualTo(JobStatus.RUNNING);
                assertThat(job.attemptCount()).isEqualTo(2);
                assertThat(job.assignmentVersion()).isEqualTo(3);
                assertThat(job.leasedBy()).contains(new WorkerId("worker-2"));
            });
    }

    @Test
    void recoveryFencesLateCompletionFromExpiredWorker() {
        Instant now = Instant.parse("2026-05-13T02:00:00Z");
        Job expired = Job.create(
                new JobId(UUID.fromString("b23e4567-e89b-12d3-a456-426614174000")),
                "embedding-generation",
                3,
                now.minusSeconds(60)
            )
            .claim(new WorkerId("worker-1"), now.minusSeconds(1), now.minusSeconds(30));
        repository.insert(expired);

        repository.recoverExpiredLeases(now, 10);
        Job lateCompletion = expired.complete(expired.assignmentVersion(), now.plusSeconds(1));

        assertThat(repository.updateIfAssignmentVersionMatches(lateCompletion, expired.assignmentVersion()))
            .isFalse();
        assertThat(repository.findById(expired.id()))
            .hasValueSatisfying(job -> {
                assertThat(job.status()).isEqualTo(JobStatus.PENDING);
                assertThat(job.assignmentVersion()).isEqualTo(2);
            });
    }

    @Test
    void limitsExpiredLeaseRecoveryBatchSize() {
        Instant now = Instant.parse("2026-05-13T03:00:00Z");
        Job first = Job.create(
                new JobId(UUID.fromString("c23e4567-e89b-12d3-a456-426614174000")),
                "embedding-generation",
                3,
                now.minusSeconds(60)
            )
            .claim(new WorkerId("worker-1"), now.minusSeconds(10), now.minusSeconds(30));
        Job second = Job.create(
                new JobId(UUID.fromString("d23e4567-e89b-12d3-a456-426614174000")),
                "batch-inference",
                3,
                now.minusSeconds(60)
            )
            .claim(new WorkerId("worker-2"), now.minusSeconds(5), now.minusSeconds(30));
        repository.insert(first);
        repository.insert(second);

        assertThat(repository.recoverExpiredLeases(now, 1)).hasSize(1);
        assertThat(repository.recoverExpiredLeases(now, 10)).hasSize(1);
    }
}
