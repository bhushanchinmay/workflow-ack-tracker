package com.example.workflowtracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class WorkflowApiIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("workflows")
            .withUsername("workflow")
            .withPassword("workflow");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("workflow.acknowledgement-timeout", () -> "PT1H");
        registry.add("workflow.scheduler.enabled", () -> "false");
    }

    @Test
    void tracksAcknowledgementsAndRejectsDuplicateAcknowledgement() throws Exception {
        String response = mockMvc.perform(post("/workflows")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "eventId": "order-1001",
                                  "payload": {"orderId": "1001", "amount": 250},
                                  "targetServices": ["billing", "shipping"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.pendingServices").isArray())
                .andReturn().getResponse().getContentAsString();

        String workflowId = com.jayway.jsonpath.JsonPath.read(response, "$.workflowId");

        mockMvc.perform(post("/workflows/{id}/acknowledge", workflowId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceName\":\"billing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acknowledgedServices[0]").value("billing"))
                .andExpect(jsonPath("$.pendingServices[0]").value("shipping"));

        mockMvc.perform(post("/workflows/{id}/acknowledge", workflowId)
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceName\":\"billing\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_WORKFLOW_STATE"));

        mockMvc.perform(get("/workflows/{id}", workflowId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void rejectsAcknowledgementForUnknownWorkflow() throws Exception {
        mockMvc.perform(post("/workflows/{id}/acknowledge",
                        "00000000-0000-0000-0000-000000000000")
                        .contentType(APPLICATION_JSON)
                        .content("{\"serviceName\":\"billing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("WORKFLOW_NOT_FOUND"));
    }
}
