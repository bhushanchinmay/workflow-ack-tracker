package com.example.workflowtracker;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OutboxKafkaIntegrationTest {

    private static final String TOPIC = "test.workflow.lifecycle.v1";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("workflows")
            .withUsername("workflow")
            .withPassword("workflow");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void databaseAndKafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("workflow.acknowledgement-timeout", () -> "PT1H");
        registry.add("workflow.scheduler.enabled", () -> "false");
        registry.add("workflow.outbox.publisher.enabled", () -> "true");
        registry.add("workflow.outbox.publisher.topic", () -> TOPIC);
        registry.add("workflow.outbox.publisher.fixed-delay-ms", () -> "100");
        registry.add("workflow.outbox.publisher.send-timeout", () -> "PT5S");
    }

    @Test
    void publishesWorkflowCompletionAfterTheTerminalStateIsStored() throws Exception {
        String response = mockMvc.perform(post("/api/v1/workflows")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "order-kafka-1001",
                                  "payload": {"orderId": "1001"},
                                  "targetServices": ["billing"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String workflowId = com.jayway.jsonpath.JsonPath.read(response, "$.workflowId");

        mockMvc.perform(post("/api/v1/workflows/{id}/acknowledge", workflowId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceName\":\"billing\"}"))
                .andExpect(status().isOk());

        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        boolean completionPublished = false;
        Instant deadline = Instant.now().plusSeconds(15);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            while (Instant.now().isBefore(deadline) && !completionPublished) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                completionPublished = records.records(TOPIC).stream()
                        .anyMatch(record -> record.key().equals(workflowId)
                                && record.value().contains("WORKFLOW_COMPLETED"));
            }
        }

        assertThat(completionPublished).isTrue();
    }
}
