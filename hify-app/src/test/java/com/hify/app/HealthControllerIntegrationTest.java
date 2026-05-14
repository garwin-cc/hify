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

        assertThat(root.path("status").asText()).isEqualTo("UP");
        assertThat(root.path("components").isObject()).isTrue();
        assertThat(root.path("components").size()).isZero();
    }

    @Test
    void should_returnReadinessWithCoreDependencyComponents() throws Exception {
        JsonNode root = getJson("/api/v1/health/readiness");

        assertThat(root.path("components").has("mysql")).isTrue();
        assertThat(root.path("components").has("redis")).isTrue();
        assertThat(root.path("components").has("pgvector")).isTrue();
        assertThat(root.path("components").has("providerSummary")).isFalse();
    }

    @Test
    void should_returnDeepHealthWithProviderSummary() throws Exception {
        JsonNode root = getJson("/api/v1/health/deep");

        assertThat(root.path("components").has("mysql")).isTrue();
        assertThat(root.path("components").has("redis")).isTrue();
        assertThat(root.path("components").has("pgvector")).isTrue();
        assertThat(root.path("components").has("providerSummary")).isTrue();
        assertThat(root.path("components").path("providerSummary").path("status").asText()).isNotBlank();
    }

    private JsonNode getJson(String path) throws Exception {
        String response = mockMvc.perform(get(path))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }
}
