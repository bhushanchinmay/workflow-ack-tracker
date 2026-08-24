package com.example.workflowtracker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "workflow.outbox.publisher.enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic workflowLifecycleTopic(
            @Value("${workflow.outbox.publisher.topic:workflow.lifecycle.v1}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
