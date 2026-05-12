package com.shaneduncan.orchestrator.application.workflow;

import com.shaneduncan.orchestrator.domain.job.Job;
import com.shaneduncan.orchestrator.domain.job.JobId;
import com.shaneduncan.orchestrator.domain.workflow.Workflow;
import com.shaneduncan.orchestrator.domain.workflow.WorkflowId;
import com.shaneduncan.orchestrator.domain.workflow.WorkflowNode;
import com.shaneduncan.orchestrator.persistence.job.JdbcJobRepository;
import com.shaneduncan.orchestrator.persistence.workflow.JdbcWorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkflowService {

    private final JdbcWorkflowRepository workflowRepository;
    private final JdbcJobRepository jobRepository;
    private final Clock clock;

    public WorkflowService(
        JdbcWorkflowRepository workflowRepository,
        JdbcJobRepository jobRepository,
        Clock clock
    ) {
        this.workflowRepository = workflowRepository;
        this.jobRepository = jobRepository;
        this.clock = clock;
    }

    @Transactional
    public WorkflowDetails createWorkflow(String name, List<WorkflowNode> nodes) {
        validateNodes(nodes);

        Instant now = Instant.now(clock);
        Workflow workflow = Workflow.create(WorkflowId.newId(), name, now);
        Map<String, Job> jobsByNodeKey = new HashMap<>();
        List<WorkflowJobDetails> jobs = new ArrayList<>();

        workflowRepository.insertWorkflow(workflow);
        for (WorkflowNode node : nodes) {
            JobId jobId = JobId.newId();
            Job job = node.dependsOn().isEmpty()
                ? Job.create(jobId, node.taskType(), node.maxAttempts(), now)
                : Job.createBlocked(jobId, node.taskType(), node.maxAttempts(), now);
            jobRepository.insert(job);
            workflowRepository.linkJob(workflow.id(), job.id(), node.key());
            jobsByNodeKey.put(node.key(), job);
            jobs.add(new WorkflowJobDetails(node.key(), job));
        }

        for (WorkflowNode node : nodes) {
            for (String upstreamNodeKey : node.dependsOn()) {
                workflowRepository.insertDependency(
                    workflow.id(),
                    jobsByNodeKey.get(upstreamNodeKey).id(),
                    jobsByNodeKey.get(node.key()).id()
                );
            }
        }

        return new WorkflowDetails(workflow, jobs);
    }

    public Optional<WorkflowDetails> findWorkflow(WorkflowId workflowId) {
        return workflowRepository.findDetailsById(workflowId);
    }

    public WorkflowDetails getWorkflow(WorkflowId workflowId) {
        return findWorkflow(workflowId)
            .orElseThrow(() -> new NoSuchElementException("Workflow not found: " + workflowId.value()));
    }

    private void validateNodes(List<WorkflowNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("workflow must contain at least one node");
        }

        Map<String, WorkflowNode> nodesByKey = new HashMap<>();
        for (WorkflowNode node : nodes) {
            WorkflowNode previous = nodesByKey.put(node.key(), node);
            if (previous != null) {
                throw new IllegalArgumentException("duplicate workflow node key: " + node.key());
            }
        }

        for (WorkflowNode node : nodes) {
            for (String dependencyKey : node.dependsOn()) {
                if (!nodesByKey.containsKey(dependencyKey)) {
                    throw new IllegalArgumentException(
                        "node " + node.key() + " depends on unknown node " + dependencyKey
                    );
                }
            }
        }

        assertAcyclic(nodesByKey);
    }

    private void assertAcyclic(Map<String, WorkflowNode> nodesByKey) {
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeKey : nodesByKey.keySet()) {
            visit(nodeKey, nodesByKey, visiting, visited);
        }
    }

    private void visit(
        String nodeKey,
        Map<String, WorkflowNode> nodesByKey,
        Set<String> visiting,
        Set<String> visited
    ) {
        if (visited.contains(nodeKey)) {
            return;
        }
        if (!visiting.add(nodeKey)) {
            throw new IllegalArgumentException("workflow graph contains a cycle at node " + nodeKey);
        }

        for (String dependencyKey : nodesByKey.get(nodeKey).dependsOn()) {
            visit(dependencyKey, nodesByKey, visiting, visited);
        }

        visiting.remove(nodeKey);
        visited.add(nodeKey);
    }
}
