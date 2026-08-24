package com.example.workflowtracker.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class WorkflowMetrics {

    private final Counter created;
    private final Counter acknowledged;
    private final Counter completed;
    private final Counter failed;
    private final Counter rejectedAcknowledgements;

    public WorkflowMetrics(MeterRegistry meterRegistry) {
        created = Counter.builder("workflow.created")
                .description("Workflows created")
                .register(meterRegistry);
        acknowledged = Counter.builder("workflow.acknowledged")
                .description("Downstream acknowledgements accepted")
                .register(meterRegistry);
        completed = Counter.builder("workflow.completed")
                .description("Workflows completed")
                .register(meterRegistry);
        failed = Counter.builder("workflow.failed")
                .description("Workflows marked failed")
                .register(meterRegistry);
        rejectedAcknowledgements = Counter.builder("workflow.acknowledgement.rejected")
                .description("Downstream acknowledgements rejected")
                .register(meterRegistry);
    }

    public void recordCreated() { created.increment(); }
    public void recordAcknowledged() { acknowledged.increment(); }
    public void recordCompleted() { completed.increment(); }
    public void recordFailed() { failed.increment(); }
    public void recordRejectedAcknowledgement() { rejectedAcknowledgements.increment(); }
}
