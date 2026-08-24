package com.example.workflowtracker.service;

import com.example.workflowtracker.api.WorkflowDtos;
import com.example.workflowtracker.domain.Workflow;
import com.example.workflowtracker.domain.WorkflowAcknowledgement;
import com.example.workflowtracker.domain.WorkflowStatus;
import com.example.workflowtracker.exception.WorkflowExceptions.WorkflowConflictException;
import com.example.workflowtracker.exception.WorkflowExceptions.WorkflowNotFoundException;
import com.example.workflowtracker.repository.WorkflowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration acknowledgementTimeout;

    public WorkflowService(WorkflowRepository workflowRepository,
                           ObjectMapper objectMapper,
                           Clock clock,
                           @Value("${workflow.acknowledgement-timeout:PT15M}") Duration acknowledgementTimeout) {
        this.workflowRepository = workflowRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.acknowledgementTimeout = acknowledgementTimeout;
    }

    @Transactional
    public WorkflowDtos.WorkflowResponse create(WorkflowDtos.CreateWorkflowRequest request) {
        Instant now = clock.instant();
        List<String> services = request.targetServices().stream().distinct().toList();
        if (services.size() != request.targetServices().size()) {
            throw new WorkflowConflictException("targetServices must not contain duplicates");
        }
        JsonNode payload = request.payload() == null ? objectMapper.createObjectNode() : request.payload();
        Workflow workflow = new Workflow(request.eventId(), payload, now,
                now.plus(acknowledgementTimeout), services);
        try {
            return toResponse(workflowRepository.saveAndFlush(workflow));
        } catch (DataIntegrityViolationException ex) {
            throw new WorkflowConflictException("eventId already exists: " + request.eventId());
        }
    }

    @Transactional
    public WorkflowDtos.WorkflowResponse acknowledge(UUID workflowId,
                                                     WorkflowDtos.AcknowledgeWorkflowRequest request) {
        Workflow workflow = getForUpdate(workflowId);
        if (workflow.getStatus() == WorkflowStatus.COMPLETED) {
            throw new WorkflowConflictException("workflow is already completed");
        }
        if (workflow.getStatus() == WorkflowStatus.FAILED) {
            throw new WorkflowConflictException("workflow is already failed");
        }
        if (workflow.isOverdue(clock.instant())) {
            throw new WorkflowConflictException("acknowledgement deadline has been exceeded");
        }
        if (!workflow.hasExpectedService(request.serviceName())) {
            throw new WorkflowConflictException("service is not an expected acknowledgement target: "
                    + request.serviceName());
        }
        WorkflowAcknowledgement acknowledgement = workflow.getAcknowledgements().stream()
                .filter(ack -> ack.getServiceName().equals(request.serviceName()))
                .findFirst()
                .orElseThrow();
        if (acknowledgement.isAcknowledged()) {
            throw new WorkflowConflictException("duplicate acknowledgement from service: "
                    + request.serviceName());
        }
        workflow.markAcknowledged(request.serviceName(), clock.instant());
        return toResponse(workflowRepository.save(workflow));
    }

    @Transactional(readOnly = true)
    public WorkflowDtos.WorkflowResponse get(UUID workflowId) {
        return toResponse(workflowRepository.findWithAcknowledgementsById(workflowId)
                .orElseThrow(() -> notFound(workflowId)));
    }

    @Transactional(readOnly = true)
    public List<WorkflowDtos.WorkflowResponse> pending() {
        return workflowRepository.findByStatusOrderByCreatedAtAsc(WorkflowStatus.PENDING)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public WorkflowDtos.WorkflowResponse fail(UUID workflowId, WorkflowDtos.FailWorkflowRequest request) {
        Workflow workflow = getForUpdate(workflowId);
        if (workflow.getStatus() == WorkflowStatus.COMPLETED) {
            throw new WorkflowConflictException("completed workflow cannot be marked failed");
        }
        if (workflow.getStatus() == WorkflowStatus.FAILED) {
            throw new WorkflowConflictException("workflow is already failed");
        }
        Instant now = clock.instant();
        if (!workflow.isOverdue(now)) {
            throw new WorkflowConflictException("workflow is not overdue; acknowledgement deadline is "
                    + workflow.getAckDeadline());
        }
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? "Acknowledgement deadline exceeded" : request.reason().trim();
        workflow.markFailed(reason, now);
        return toResponse(workflowRepository.save(workflow));
    }

    @Transactional
    public int failOverdue() {
        Instant now = clock.instant();
        List<Workflow> overdue = workflowRepository.findOverdueForUpdate(WorkflowStatus.PENDING, now);
        overdue.forEach(workflow -> workflow.markFailed("Acknowledgement deadline exceeded", now));
        workflowRepository.saveAll(overdue);
        return overdue.size();
    }

    private Workflow getForUpdate(UUID workflowId) {
        return workflowRepository.findByIdForUpdate(workflowId)
                .orElseThrow(() -> notFound(workflowId));
    }

    private WorkflowNotFoundException notFound(UUID id) {
        return new WorkflowNotFoundException("workflow not found: " + id);
    }

    private WorkflowDtos.WorkflowResponse toResponse(Workflow workflow) {
        List<WorkflowDtos.AcknowledgementResponse> acknowledgements = workflow.getAcknowledgements().stream()
                .map(ack -> new WorkflowDtos.AcknowledgementResponse(ack.getServiceName(),
                        ack.isAcknowledged(), ack.getAcknowledgedAt()))
                .toList();
        List<String> acknowledged = workflow.getAcknowledgements().stream()
                .filter(WorkflowAcknowledgement::isAcknowledged)
                .map(WorkflowAcknowledgement::getServiceName).toList();
        List<String> pending = workflow.getAcknowledgements().stream()
                .filter(ack -> !ack.isAcknowledged())
                .map(WorkflowAcknowledgement::getServiceName).toList();
        return new WorkflowDtos.WorkflowResponse(workflow.getId(), workflow.getEventId(),
                objectMapper.convertValue(workflow.getPayload(), JsonNode.class), workflow.getStatus(),
                workflow.getFailureReason(), workflow.getCreatedAt(), workflow.getUpdatedAt(),
                workflow.getAckDeadline(), acknowledgements, acknowledged, pending);
    }
}
