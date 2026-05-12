package com.shaneduncan.orchestrator.api.worker;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaneduncan.orchestrator.domain.job.JobStatus;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WorkerControllerTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
        .withDatabaseName("forgeflow")
        .withUsername("forgeflow")
        .withPassword("forgeflow");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    void heartbeatRegistersWorker() throws Exception {
        mockMvc.perform(post("/api/workers/{workerId}/heartbeat", "worker-api-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "currentJobId": null
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("worker-api-1"))
            .andExpect(jsonPath("$.lastHeartbeatAt", notNullValue()));
    }

    @Test
    void workerPollClaimsRunnableJob() throws Exception {
        createJob("batch-inference", 3);

        mockMvc.perform(post("/api/workers/{workerId}/poll", "worker-api-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "leaseSeconds": 30,
                      "waitSeconds": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(JobStatus.RUNNING.name()))
            .andExpect(jsonPath("$.leasedBy").value("worker-api-1"))
            .andExpect(jsonPath("$.assignmentVersion").value(1));
    }

    @Test
    void workerRenewsAndCompletesOwnedAssignment() throws Exception {
        JsonNode claimed = pollClaimedJob("worker-api-1");
        String jobId = claimed.get("id").asText();
        long assignmentVersion = claimed.get("assignmentVersion").asLong();

        mockMvc.perform(post("/api/workers/{workerId}/jobs/{jobId}/renew-lease", "worker-api-1", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assignmentVersion": %d,
                      "leaseSeconds": 60
                    }
                    """.formatted(assignmentVersion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(JobStatus.RUNNING.name()))
            .andExpect(jsonPath("$.assignmentVersion").value(assignmentVersion))
            .andExpect(jsonPath("$.leaseExpiresAt", notNullValue()));

        mockMvc.perform(post("/api/workers/{workerId}/jobs/{jobId}/complete", "worker-api-1", jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assignmentVersion": %d
                    }
                    """.formatted(assignmentVersion)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(JobStatus.SUCCEEDED.name()))
            .andExpect(jsonPath("$.leasedBy").doesNotExist())
            .andExpect(jsonPath("$.leaseExpiresAt").doesNotExist());
    }

    @Test
    void staleWorkerCompletionIsRejected() throws Exception {
        JsonNode claimed = pollClaimedJob("worker-api-1");

        mockMvc.perform(post("/api/workers/{workerId}/jobs/{jobId}/complete", "worker-api-1", claimed.get("id").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assignmentVersion": 0
                    }
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void workerFailureSchedulesRetry() throws Exception {
        JsonNode claimed = pollClaimedJob("worker-api-1");

        mockMvc.perform(post("/api/workers/{workerId}/jobs/{jobId}/fail", "worker-api-1", claimed.get("id").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assignmentVersion": %d,
                      "reason": "temporary model server error",
                      "retryable": true,
                      "retryDelaySeconds": 30
                    }
                    """.formatted(claimed.get("assignmentVersion").asLong())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(JobStatus.RETRYING.name()))
            .andExpect(jsonPath("$.failureReason").value("temporary model server error"))
            .andExpect(jsonPath("$.nextRunAt", notNullValue()));
    }

    private JsonNode pollClaimedJob(String workerId) throws Exception {
        createJob("batch-inference", 3);
        MvcResult result = mockMvc.perform(post("/api/workers/{workerId}/poll", workerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "leaseSeconds": 30,
                      "waitSeconds": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private void createJob(String taskType, int maxAttempts) throws Exception {
        mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "taskType": "%s",
                      "maxAttempts": %d
                    }
                    """.formatted(taskType, maxAttempts)))
            .andExpect(status().isCreated());
    }
}
