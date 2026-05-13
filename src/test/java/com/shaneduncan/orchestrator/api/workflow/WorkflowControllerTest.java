package com.shaneduncan.orchestrator.api.workflow;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaneduncan.orchestrator.domain.job.JobStatus;
import com.shaneduncan.orchestrator.domain.workflow.WorkflowStatus;
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
class WorkflowControllerTest {

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
        registry.add("forgeflow.scheduler.dead-letter.enabled", () -> false);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM job_dependencies", Map.of());
        jdbcTemplate.update("DELETE FROM workflow_jobs", Map.of());
        jdbcTemplate.update("DELETE FROM workers", Map.of());
        jdbcTemplate.update("DELETE FROM workflows", Map.of());
        jdbcTemplate.update("DELETE FROM jobs", Map.of());
    }

    @Test
    void createsWorkflowDagWithBlockedDependentJob() throws Exception {
        MvcResult result = createWorkflow();
        JsonNode workflow = objectMapper.readTree(result.getResponse().getContentAsString());

        mockMvc.perform(get("/api/workflows/{id}", workflow.get("id").asText()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("document-indexing"))
            .andExpect(jsonPath("$.status").value(WorkflowStatus.RUNNING.name()))
            .andExpect(jsonPath("$.jobs[0].nodeKey").value("ingest"))
            .andExpect(jsonPath("$.jobs[0].job.status").value(JobStatus.PENDING.name()))
            .andExpect(jsonPath("$.jobs[1].nodeKey").value("embed"))
            .andExpect(jsonPath("$.jobs[1].job.status").value(JobStatus.BLOCKED.name()));
    }

    @Test
    void duplicateIdempotencyKeyReturnsOriginalWorkflow() throws Exception {
        MvcResult firstResult = createWorkflow("workflow-submit-123");
        JsonNode firstWorkflow = objectMapper.readTree(firstResult.getResponse().getContentAsString());

        mockMvc.perform(post("/api/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workflowRequest("workflow-submit-123")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(firstWorkflow.get("id").asText()))
            .andExpect(jsonPath("$.jobs[0].nodeKey").value("ingest"))
            .andExpect(jsonPath("$.jobs[1].nodeKey").value("embed"));

        Long workflowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM workflows", Map.of(), Long.class);
        Long jobCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jobs", Map.of(), Long.class);
        org.assertj.core.api.Assertions.assertThat(workflowCount).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(jobCount).isEqualTo(2);
    }

    @Test
    void completingUpstreamJobPromotesReadyDependentJob() throws Exception {
        createWorkflow();
        JsonNode claimedIngest = pollJob("worker-dag-1");

        mockMvc.perform(post("/api/workers/{workerId}/jobs/{jobId}/complete", "worker-dag-1", claimedIngest.get("id").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "assignmentVersion": %d
                    }
                    """.formatted(claimedIngest.get("assignmentVersion").asLong())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(JobStatus.SUCCEEDED.name()));

        mockMvc.perform(post("/api/workers/{workerId}/poll", "worker-dag-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "leaseSeconds": 30,
                      "waitSeconds": 0
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taskType").value("embedding-generation"))
            .andExpect(jsonPath("$.status").value(JobStatus.RUNNING.name()))
            .andExpect(jsonPath("$.leasedBy").value("worker-dag-2"));
    }

    @Test
    void rejectsWorkflowCycles() throws Exception {
        mockMvc.perform(post("/api/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "cyclic",
                      "nodes": [
                        {
                          "key": "a",
                          "taskType": "a-task",
                          "maxAttempts": 3,
                          "dependsOn": ["b"]
                        },
                        {
                          "key": "b",
                          "taskType": "b-task",
                          "maxAttempts": 3,
                          "dependsOn": ["a"]
                        }
                      ]
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    private MvcResult createWorkflow() throws Exception {
        return createWorkflow(null);
    }

    private MvcResult createWorkflow(String idempotencyKey) throws Exception {
        return mockMvc.perform(post("/api/workflows")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workflowRequest(idempotencyKey)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", notNullValue()))
            .andExpect(jsonPath("$.status").value(WorkflowStatus.RUNNING.name()))
            .andExpect(jsonPath("$.jobs[0].job.status").value(JobStatus.PENDING.name()))
            .andExpect(jsonPath("$.jobs[1].job.status").value(JobStatus.BLOCKED.name()))
            .andReturn();
    }

    private String workflowRequest(String idempotencyKey) {
        String idempotencyField = idempotencyKey == null
            ? ""
            : """
              "idempotencyKey": "%s",
              """.formatted(idempotencyKey);
        return """
            {
              "name": "document-indexing",
              %s
              "nodes": [
                {
                  "key": "ingest",
                  "taskType": "document-ingestion",
                  "maxAttempts": 3,
                  "dependsOn": []
                },
                {
                  "key": "embed",
                  "taskType": "embedding-generation",
                  "maxAttempts": 3,
                  "dependsOn": ["ingest"]
                }
              ]
            }
            """.formatted(idempotencyField);
    }

    private JsonNode pollJob(String workerId) throws Exception {
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
}
