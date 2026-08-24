package com.example.workflowtracker.outbox;

public enum OutboxStatus {
    PENDING,
    CLAIMED,
    PUBLISHED,
    DEAD_LETTER
}
