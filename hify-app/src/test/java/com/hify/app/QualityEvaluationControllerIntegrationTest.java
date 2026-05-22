package com.hify.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.support.HifyMockIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QualityEvaluationControllerIntegrationTest extends HifyMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Sql(statements = {
            "INSERT INTO t_project (id, name, code, status, deleted) VALUES (7101, 'Quality Project', 'quality', 'ACTIVE', 0)",
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, deleted) VALUES (7201, 'Quality Provider', 'OPENAI', '', '{}', 1, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, deleted) VALUES (7202, 7201, 'Quality Model', 'gpt-4o-mini', 'CHAT', 8192, '{}', 1, 0, 0)",
            "INSERT INTO t_agent (id, project_id, name, description, system_prompt, model_config_id, knowledge_base_ids, enabled, deleted) VALUES (7301, 7101, 'Quality Agent', '', 'help', 7202, '[]', 1, 0)",
            "INSERT INTO t_chat_session (id, agent_id, title, status, message_count, deleted) VALUES (7401, 7301, 'quality session', 'ACTIVE', 4, 0)",
            "INSERT INTO t_chat_message (id, session_id, trace_id, role, content, status, deleted) VALUES (7501, 7401, 'quality-t1', 'user', 'question 1', 'DONE', 0)",
            "INSERT INTO t_chat_message (id, session_id, trace_id, role, content, status, deleted) VALUES (7502, 7401, 'quality-t1', 'assistant', 'answer 1', 'DONE', 0)",
            "INSERT INTO t_chat_message (id, session_id, trace_id, role, content, status, deleted) VALUES (7503, 7401, 'quality-t2', 'user', 'question 2', 'DONE', 0)",
            "INSERT INTO t_chat_message (id, session_id, trace_id, role, content, status, deleted) VALUES (7504, 7401, 'quality-t2', 'assistant', 'answer 2', 'DONE', 0)",
            "INSERT INTO t_conversation_trace (trace_id, session_id, user_message_id, assistant_message_id, project_id, agent_id, agent_name, provider_id, provider_name, provider_type, model_config_id, model_id, rag_triggered, mcp_triggered, status, started_at, finished_at, deleted) VALUES ('quality-t1', 7401, 7501, 7502, 7101, 7301, 'Quality Agent', 7201, 'Quality Provider', 'OPENAI', 7202, 'gpt-4o-mini', 1, 0, 'DONE', '2026-05-20 10:00:00', '2026-05-20 10:00:03', 0)",
            "INSERT INTO t_conversation_trace (trace_id, session_id, user_message_id, assistant_message_id, project_id, agent_id, agent_name, provider_id, provider_name, provider_type, model_config_id, model_id, rag_triggered, mcp_triggered, status, started_at, finished_at, deleted) VALUES ('quality-t2', 7401, 7503, 7504, 7101, 7301, 'Quality Agent', 7201, 'Quality Provider', 'OPENAI', 7202, 'gpt-4o-mini', 1, 0, 'DONE', '2026-05-20 11:00:00', '2026-05-20 11:00:03', 0)",
            "INSERT INTO t_conversation_rag_trace (trace_id, knowledge_base_id, knowledge_base_name, chunk_id, chunk_index, score, content_preview, deleted, created_at) VALUES ('quality-t2', 1, 'KB', 1001, 1, 0.88, 'hit', 0, '2026-05-20 11:00:01')",
            "INSERT INTO t_message_feedback (id, message_id, session_id, agent_id, project_id, trace_id, user_id, rating, issue_type, comment, corrected_answer, status, review_status, resolution_note, deleted, created_at, updated_at) VALUES (7601, 7502, 7401, 7301, 7101, 'quality-t1', 1, 'DISLIKE', 'RAG_MISS', 'no useful context', NULL, 'ACTIVE', 'OPEN', '', 0, '2026-05-20 10:01:00', '2026-05-20 10:01:00')",
            "INSERT INTO t_message_feedback (id, message_id, session_id, agent_id, project_id, trace_id, user_id, rating, issue_type, comment, corrected_answer, status, review_status, resolution_note, deleted, created_at, updated_at) VALUES (7602, 7504, 7401, 7301, 7101, 'quality-t2', 1, 'LIKE', '', '', NULL, 'ACTIVE', 'RESOLVED', '', 0, '2026-05-20 11:01:00', '2026-05-20 11:01:00')"
    })
    void should_returnQualityOverviewAndUpdateSampleStatus_when_feedbackExists() throws Exception {
        String overviewResponse = mockMvc.perform(get("/api/v1/ops/quality/overview")
                        .param("projectId", "7101")
                        .param("from", "2026-05-20T00:00:00")
                        .param("to", "2026-05-21T00:00:00"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode overview = objectMapper.readTree(overviewResponse).path("data");
        assertThat(overview.path("summary").path("feedbackCount").asInt()).isEqualTo(2);
        assertThat(overview.path("summary").path("negativeFeedbackCount").asInt()).isEqualTo(1);
        assertThat(overview.path("summary").path("negativeFeedbackRate").asDouble()).isEqualTo(0.5);
        assertThat(overview.path("summary").path("ragHelpfulRate").asDouble()).isEqualTo(0.5);
        assertThat(overview.path("agents").get(0).path("agentName").asText()).isEqualTo("Quality Agent");
        assertThat(overview.path("issues").get(0).path("issueType").asText()).isEqualTo("RAG_MISS");

        String samplesResponse = mockMvc.perform(get("/api/v1/ops/quality/samples")
                        .param("projectId", "7101")
                        .param("reviewStatus", "OPEN"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode sample = objectMapper.readTree(samplesResponse).path("data").get(0);
        assertThat(sample.path("id").asLong()).isEqualTo(7601L);
        assertThat(sample.path("traceId").asText()).isEqualTo("quality-t1");
        assertThat(sample.path("userQuestion").asText()).isEqualTo("question 1");
        assertThat(sample.path("assistantAnswer").asText()).isEqualTo("answer 1");
        assertThat(sample.path("ragTriggered").asBoolean()).isTrue();
        assertThat(sample.path("ragHit").asBoolean()).isFalse();

        mockMvc.perform(put("/api/v1/ops/quality/samples/7601/status")
                        .contentType("application/json")
                        .content("{\"reviewStatus\":\"RESOLVED\",\"resolutionNote\":\"add knowledge\"}"))
                .andExpect(status().isOk());

        String resolvedResponse = mockMvc.perform(get("/api/v1/ops/quality/samples")
                        .param("projectId", "7101")
                        .param("reviewStatus", "RESOLVED"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode resolved = objectMapper.readTree(resolvedResponse).path("data").get(0);
        assertThat(resolved.path("id").asLong()).isEqualTo(7601L);
        assertThat(resolved.path("resolutionNote").asText()).isEqualTo("add knowledge");
    }
}
