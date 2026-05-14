package com.hify.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.support.HifyMockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentControllerIntegrationTest extends HifyMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ── IT-A01 ────────────────────────────────────────────────────────────

    @Test
    @Sql(statements = {
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (5001, 'Agent IT Provider', 'OPENAI', '', '{\"apiKey\":\"sk-test\"}', 1, 0, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted) VALUES (5101, 5001, 'Agent IT Model', 'gpt-4o', 'CHAT', 8192, '{}', 1, 0, 0, 0)"
    })
    void should_createAgentAndReturnDetail_when_requestIsValid() throws Exception {
        // Given
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "IT Agent A01",
                "systemPrompt", "你是 Hify 助手",
                "modelConfigId", 5101
        ));

        // When
        String response = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        JsonNode root = objectMapper.readTree(response);
        Long id = root.path("data").path("id").asLong();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent WHERE id = ? AND name = ? AND deleted = 0",
                Integer.class, id, "IT Agent A01");

        assertThat(root.path("code").asInt()).isEqualTo(200);
        assertThat(id).isPositive();
        assertThat(root.path("data").path("modelConfig").path("name").asText()).isEqualTo("Agent IT Model");
        assertThat(root.path("data").path("enabled").asInt()).isEqualTo(1);
        assertThat(count).isEqualTo(1);
    }

    // ── IT-A02 ────────────────────────────────────────────────────────────

    @Test
    void should_returnAgentModelUnavailableCode_when_modelConfigDoesNotExist() throws Exception {
        // Given
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "IT Agent A02",
                "systemPrompt", "你是 Hify 助手",
                "modelConfigId", 999999
        ));

        // When
        String response = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        JsonNode root = objectMapper.readTree(response);
        assertThat(root.path("code").asInt()).isEqualTo(3001);
        assertThat(root.path("data").isNull()).isTrue();
    }

    // ── IT-A03 ────────────────────────────────────────────────────────────

    @Test
    @Sql(statements = {
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (5002, 'Agent IT Provider 2', 'OPENAI', '', '{\"apiKey\":\"sk-test\"}', 1, 0, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted) VALUES (5102, 5002, 'Agent IT Model 2', 'gpt-4o', 'CHAT', 8192, '{}', 1, 0, 0, 0)",
            "INSERT INTO t_agent (id, name, description, system_prompt, model_config_id, workflow_id, knowledge_base_ids, temperature, max_turns, max_tokens, max_context_turns, enabled, version, created_by, deleted) VALUES (6001, 'Duplicate Agent Name', '', '你是助手', 5102, NULL, '[]', 0.70, 20, 1024, 5, 1, 0, 0, 0)"
    })
    void should_returnConflictCode_when_agentNameAlreadyExists() throws Exception {
        // Given
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Duplicate Agent Name",
                "systemPrompt", "你是 Hify 助手",
                "modelConfigId", 5102
        ));

        // When
        String response = mockMvc.perform(post("/api/v1/agents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Then
        JsonNode root = objectMapper.readTree(response);
        Integer agentCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent WHERE name = ? AND deleted = 0",
                Integer.class, "Duplicate Agent Name");

        assertThat(root.path("code").asInt()).isEqualTo(409);
        assertThat(root.path("data").isNull()).isTrue();
        assertThat(agentCount).isEqualTo(1);
    }
}
