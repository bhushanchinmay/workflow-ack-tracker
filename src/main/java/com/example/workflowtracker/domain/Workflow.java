package com.example.workflowtracker.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "workflow_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workflow_event_id", columnNames = "event_id")
})
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 100)
    private String eventId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private Object payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkflowStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "ack_deadline", nullable = false)
    private Instant ackDeadline;

    @Version
    @Column(nullable = false)
    private long version;

    @OneToMany(mappedBy = "workflow", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("serviceName ASC")
    private List<WorkflowAcknowledgement> acknowledgements = new ArrayList<>();

    protected Workflow() {
    }

    public Workflow(String eventId, Object payload, Instant createdAt, Instant ackDeadline,
                    List<String> targetServices) {
        this.eventId = eventId;
        this.payload = payload;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.ackDeadline = ackDeadline;
        this.status = WorkflowStatus.PENDING;
        targetServices.forEach(this::addExpectedService);
    }

    public void addExpectedService(String serviceName) {
        acknowledgements.add(new WorkflowAcknowledgement(this, serviceName));
    }

    public void markAcknowledged(String serviceName, Instant acknowledgedAt) {
        acknowledgements.stream()
                .filter(ack -> ack.getServiceName().equals(serviceName))
                .findFirst()
                .ifPresent(ack -> ack.markAcknowledged(acknowledgedAt));
        refreshStatus(acknowledgedAt);
    }

    public void markFailed(String reason, Instant changedAt) {
        this.status = WorkflowStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = changedAt;
    }

    public void refreshStatus(Instant changedAt) {
        if (status == WorkflowStatus.PENDING
                && acknowledgements.stream().allMatch(WorkflowAcknowledgement::isAcknowledged)) {
            status = WorkflowStatus.COMPLETED;
            updatedAt = changedAt;
        } else {
            updatedAt = changedAt;
        }
    }

    public boolean hasExpectedService(String serviceName) {
        return acknowledgements.stream().anyMatch(ack -> ack.getServiceName().equals(serviceName));
    }

    public boolean isOverdue(Instant now) {
        return ackDeadline.isBefore(now) || ackDeadline.equals(now);
    }

    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public Object getPayload() { return payload; }
    public WorkflowStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAckDeadline() { return ackDeadline; }
    public List<WorkflowAcknowledgement> getAcknowledgements() { return acknowledgements; }
}
