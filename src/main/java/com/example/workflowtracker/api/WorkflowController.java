package com.example.workflowtracker.api;

import com.example.workflowtracker.service.WorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static com.example.workflowtracker.api.WorkflowDtos.*;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowService workflowService;

    public WorkflowController(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @PostMapping
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody CreateWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.create(request));
    }

    @PostMapping("/{workflowId}/acknowledge")
    public WorkflowResponse acknowledge(@PathVariable UUID workflowId,
                                        @Valid @RequestBody AcknowledgeWorkflowRequest request) {
        return workflowService.acknowledge(workflowId, request);
    }

    @GetMapping("/{workflowId}")
    public WorkflowResponse get(@PathVariable UUID workflowId) {
        return workflowService.get(workflowId);
    }

    @GetMapping("/pending")
    public List<WorkflowResponse> pending() {
        return workflowService.pending();
    }

    @PostMapping("/{workflowId}/failed")
    public WorkflowResponse fail(@PathVariable UUID workflowId,
                                  @Valid @RequestBody(required = false) FailWorkflowRequest request) {
        return workflowService.fail(workflowId, request);
    }
}
