package com.hify.app;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppPublishingIntegrationTest extends HifyMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Sql(statements = {
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (7001, 'App Publish Provider', 'OPENAI', '', '{\"apiKey\":\"sk-test\"}', 1, 0, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted) VALUES (7101, 7001, 'App Publish Model', 'gpt-4o', 'CHAT', 8192, '{}', 1, 0, 0, 0)",
            "INSERT INTO t_agent (id, name, description, system_prompt, model_config_id, workflow_id, knowledge_base_ids, temperature, max_turns, max_tokens, max_context_turns, enabled, version, created_by, deleted) VALUES (7201, 'App Publish Agent', '', '你是应用发布助手', 7101, NULL, '[]', 0.70, 20, 1024, 5, 1, 0, 0, 0)"
    })
    void should_publishAgentAppAndCreateApiKey_when_agentVersionIsPublished() throws Exception {
        String versionBody = objectMapper.writeValueAsString(Map.of("description", "publish integration"));
        String versionResponse = mockMvc.perform(post("/api/v1/agents/{id}/versions/publish", 7201L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(versionBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long versionId = objectMapper.readTree(versionResponse).path("data").path("id").asLong();

        String appBody = objectMapper.writeValueAsString(Map.of(
                "publishedVersionId", versionId,
                "name", "内部助手应用",
                "description", "内部 Web App 和 API",
                "webEnabled", 1,
                "apiEnabled", 1,
                "endpointPath", "/internal-assistant"
        ));
        String appResponse = mockMvc.perform(post("/api/v1/agents/{id}/apps", 7201L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(appBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Long appId = objectMapper.readTree(appResponse).path("data").path("id").asLong();

        String keyBody = objectMapper.writeValueAsString(Map.of("name", "integration-key"));
        String keyResponse = mockMvc.perform(post("/api/v1/agent-apps/{appId}/api-keys", appId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(keyBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode keyRoot = objectMapper.readTree(keyResponse);
        String apiKey = keyRoot.path("data").path("apiKey").asText();
        Long keyId = keyRoot.path("data").path("id").asLong();

        String listResponse = mockMvc.perform(get("/api/v1/agent-apps/{appId}/api-keys", appId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode firstKey = objectMapper.readTree(listResponse).path("data").get(0);

        Integer appCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent_app WHERE id = ? AND published_version_id = ? AND status = 'ACTIVE'",
                Integer.class, appId, versionId);
        Integer keyCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_agent_api_key WHERE id = ? AND agent_app_id = ? AND status = 'ACTIVE'",
                Integer.class, keyId, appId);

        assertThat(versionId).isPositive();
        assertThat(appId).isPositive();
        assertThat(apiKey).startsWith("hify_");
        assertThat(firstKey.path("id").asLong()).isEqualTo(keyId);
        assertThat(firstKey.hasNonNull("apiKey")).isFalse();
        assertThat(appCount).isEqualTo(1);
        assertThat(keyCount).isEqualTo(1);
    }
}
