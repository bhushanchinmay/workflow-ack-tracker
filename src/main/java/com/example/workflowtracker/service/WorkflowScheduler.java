package com.example.workflowtracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "workflow.scheduler.enabled", havingValue = "true")
public class WorkflowScheduler {
    private static final Logger log = LoggerFactory.getLogger(WorkflowScheduler.class);
    private final WorkflowService workflowService;

    public WorkflowScheduler(WorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @Scheduled(fixedDelayString = "${workflow.scheduler.fixed-delay-ms:60000}")
    public void markOverdueWorkflowsAsFailed() {
        int failed = workflowService.failOverdue();
        if (failed > 0) {
            log.info("Marked {} overdue workflows as failed", failed);
        }
    }
}
