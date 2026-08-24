package com.example.workflowtracker.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workflow_acknowledgements", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workflow_service", columnNames = {"workflow_id", "service_name"})
})
public class WorkflowAcknowledgement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Column(name = "service_name", nullable = false, length = 100)
    private String serviceName;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    protected WorkflowAcknowledgement() {
    }

    public WorkflowAcknowledgement(Workflow workflow, String serviceName) {
        this.workflow = workflow;
        this.serviceName = serviceName;
    }

    public void markAcknowledged(Instant acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    public boolean isAcknowledged() { return acknowledgedAt != null; }
    public UUID getId() { return id; }
    public String getServiceName() { return serviceName; }
    public Instant getAcknowledgedAt() { return acknowledgedAt; }
}
