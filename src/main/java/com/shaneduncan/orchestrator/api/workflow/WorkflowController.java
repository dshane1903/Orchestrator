package com.shaneduncan.orchestrator.api.workflow;

import com.shaneduncan.orchestrator.application.workflow.WorkflowService;
import com.shaneduncan.orchestrator.domain.workflow.WorkflowId;
import com.shaneduncan.orchestrator.domain.workflow.WorkflowNode;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public ResponseEntity<WorkflowResponse> createWorkflow(
        @Valid @RequestBody CreateWorkflowRequest request
    ) {
        WorkflowResponse response = WorkflowResponse.from(workflowService.createWorkflow(
            request.name(),
            request.nodes().stream()
                .map(node -> new WorkflowNode(
                    node.key(),
                    node.taskType(),
                    node.maxAttempts(),
                    node.dependsOn()
                ))
                .toList()
        ));

        return ResponseEntity
            .created(URI.create("/api/workflows/" + response.id()))
            .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable UUID id) {
        return workflowService.findWorkflow(new WorkflowId(id))
            .map(WorkflowResponse::from)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
