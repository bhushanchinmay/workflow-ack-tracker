package com.example.workflowtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WorkflowTrackerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowTrackerApplication.class, args);
    }
}
