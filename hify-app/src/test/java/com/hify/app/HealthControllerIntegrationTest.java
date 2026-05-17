package com.hify.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.support.HifyMockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HealthControllerIntegrationTest extends HifyMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_returnLivenessWithoutDependencyComponents() throws Exception {
        JsonNode root = getJson("/api/v1/health/liveness");

        JsonNode data = assertOkResult(root);
        assertThat(data.path("status").asText()).isEqualTo("UP");
        assertThat(data.path("components").isObject()).isTrue();
        assertThat(data.path("components").size()).isZero();
    }

    @Test
    void should_returnReadinessWithCoreDependencyComponents() throws Exception {
        JsonNode root = getJson("/api/v1/health/readiness");
        JsonNode data = assertOkResult(root);

        assertThat(data.path("components").has("mysql")).isTrue();
        assertThat(data.path("components").has("redis")).isTrue();
        assertThat(data.path("components").has("pgvector")).isTrue();
        assertThat(data.path("components").has("taskQueues")).isTrue();
        assertThat(data.path("components").has("providerSummary")).isFalse();
    }

    @Test
    void should_returnDeepHealthWithProviderSummaryAndCapacitySignals() throws Exception {
        JsonNode root = getJson("/api/v1/health/deep");
        JsonNode data = assertOkResult(root);

        assertThat(data.path("components").has("mysql")).isTrue();
        assertThat(data.path("components").has("redis")).isTrue();
        assertThat(data.path("components").has("pgvector")).isTrue();
        assertThat(data.path("components").has("providerSummary")).isTrue();
        assertThat(data.path("components").has("sseConnections")).isTrue();
        assertThat(data.path("components").has("logArchive")).isTrue();
        assertThat(data.path("components").has("taskQueues")).isTrue();
        assertThat(data.path("components").path("providerSummary").path("status").asText()).isNotBlank();
        assertThat(data.path("components").path("sseConnections").path("active").isInt()).isTrue();
        assertThat(data.path("components").path("taskQueues").path("queues").isArray()).isTrue();
    }

    private JsonNode getJson(String path) throws Exception {
        String response = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode assertOkResult(JsonNode root) {
        assertThat(root.path("code").asInt()).isEqualTo(200);
        assertThat(root.path("message").asText()).isEqualTo("ok");
        assertThat(root.path("data").isObject()).isTrue();
        return root.path("data");
    }
}
