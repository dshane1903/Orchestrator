package com.shaneduncan.orchestrator.api.job;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class JobControllerTest {

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
        jdbcTemplate.update("DELETE FROM jobs", Map.of());
    }

    @Test
    void createsAndFetchesJob() throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/jobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "taskType": "embedding-generation",
                      "maxAttempts": 3
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", notNullValue()))
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.taskType").value("embedding-generation"))
            .andExpect(jsonPath("$.status").value(JobStatus.PENDING.name()))
            .andExpect(jsonPath("$.attemptCount").value(0))
            .andExpect(jsonPath("$.assignmentVersion").value(0))
            .andReturn();

        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString());

        mockMvc.perform(get("/api/jobs/{id}", created.get("id").asText()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.get("id").asText()))
            .andExpect(jsonPath("$.status").value(JobStatus.PENDING.name()));
    }

    @Test
    void returnsNotFoundForUnknownJob() throws Exception {
        mockMvc.perform(get("/api/jobs/{id}", "123e4567-e89b-12d3-a456-426614174000"))
            .andExpect(status().isNotFound());
    }

    @Test
    void claimsNextRunnableJob() throws Exception {
        createJob("batch-inference", 3);

        mockMvc.perform(post("/api/jobs/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workerId": "worker-api-1",
                      "leaseSeconds": 30
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(JobStatus.RUNNING.name()))
            .andExpect(jsonPath("$.leasedBy").value("worker-api-1"))
            .andExpect(jsonPath("$.attemptCount").value(1))
            .andExpect(jsonPath("$.assignmentVersion").value(1))
            .andExpect(jsonPath("$.leaseExpiresAt", notNullValue()));
    }

    @Test
    void returnsNoContentWhenNoJobCanBeClaimed() throws Exception {
        mockMvc.perform(post("/api/jobs/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "workerId": "worker-api-1",
                      "leaseSeconds": 30
                    }
                    """))
            .andExpect(status().isNoContent());
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
