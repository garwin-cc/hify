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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OperationsAnalyticsControllerIntegrationTest extends HifyMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Sql(statements = {
            "INSERT INTO t_project (id, name, code, status, deleted) VALUES (6101, 'Ops Project', 'ops', 'ACTIVE', 0)",
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, deleted) VALUES (6201, 'Ops Provider', 'OPENAI', '', '{}', 1, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, deleted) VALUES (6202, 6201, 'Ops Model', 'gpt-4o-mini', 'CHAT', 8192, '{}', 1, 0, 0)",
            "INSERT INTO t_agent (id, project_id, name, description, system_prompt, model_config_id, knowledge_base_ids, enabled, deleted) VALUES (6301, 6101, 'Support Agent', '', 'help', 6202, '[]', 1, 0)",
            "INSERT INTO t_agent (id, project_id, name, description, system_prompt, model_config_id, knowledge_base_ids, enabled, deleted) VALUES (6302, 6101, 'Ops Agent', '', 'ops', 6202, '[]', 1, 0)",
            "INSERT INTO t_conversation_trace (trace_id, session_id, user_message_id, assistant_message_id, project_id, agent_id, agent_name, provider_id, provider_name, provider_type, model_config_id, model_id, rag_triggered, mcp_triggered, status, error_code, error_message, started_at, finished_at, deleted) VALUES ('ops-t1', 1, 101, 201, 6101, 6301, 'Support Agent', 6201, 'Ops Provider', 'OPENAI', 6202, 'gpt-4o-mini', 1, 1, 'DONE', NULL, NULL, '2026-05-10 10:00:00', '2026-05-10 10:00:03', 0)",
            "INSERT INTO t_conversation_trace (trace_id, session_id, user_message_id, assistant_message_id, project_id, agent_id, agent_name, provider_id, provider_name, provider_type, model_config_id, model_id, rag_triggered, mcp_triggered, status, error_code, error_message, started_at, finished_at, deleted) VALUES ('ops-t2', 2, 102, 202, 6101, 6301, 'Support Agent', 6201, 'Ops Provider', 'OPENAI', 6202, 'gpt-4o-mini', 1, 0, 'ERROR', 'LLM_TIMEOUT', 'timeout', '2026-05-10 11:00:00', '2026-05-10 11:00:08', 0)",
            "INSERT INTO t_conversation_trace (trace_id, session_id, user_message_id, assistant_message_id, project_id, agent_id, agent_name, provider_id, provider_name, provider_type, model_config_id, model_id, rag_triggered, mcp_triggered, status, error_code, error_message, started_at, finished_at, deleted) VALUES ('ops-t3', 3, 103, 203, 6101, 6302, 'Ops Agent', 6201, 'Ops Provider', 'OPENAI', 6202, 'gpt-4o-mini', 0, 0, 'DONE', NULL, NULL, '2026-05-11 10:00:00', '2026-05-11 10:00:02', 0)",
            "INSERT INTO t_llm_call_stat (trace_id, project_id, agent_id, provider_id, provider_type, model_config_id, model_id, call_type, success, input_tokens, output_tokens, latency_ms, deleted, created_at) VALUES ('ops-t1', 6101, 6301, 6201, 'OPENAI', 6202, 'gpt-4o-mini', 'STREAM', 1, 100, 50, 1200, 0, '2026-05-10 10:00:01')",
            "INSERT INTO t_llm_call_stat (trace_id, project_id, agent_id, provider_id, provider_type, model_config_id, model_id, call_type, success, input_tokens, output_tokens, latency_ms, error_code, deleted, created_at) VALUES ('ops-t2', 6101, 6301, 6201, 'OPENAI', 6202, 'gpt-4o-mini', 'STREAM', 0, 200, 0, 3000, 'LLM_TIMEOUT', 0, '2026-05-10 11:00:01')",
            "INSERT INTO t_llm_call_stat (trace_id, project_id, agent_id, provider_id, provider_type, model_config_id, model_id, call_type, success, input_tokens, output_tokens, latency_ms, deleted, created_at) VALUES ('ops-t3', 6101, 6302, 6201, 'OPENAI', 6202, 'gpt-4o-mini', 'CHAT', 1, 80, 40, 900, 0, '2026-05-11 10:00:01')",
            "INSERT INTO t_conversation_rag_trace (trace_id, knowledge_base_id, knowledge_base_name, chunk_id, chunk_index, score, content_preview, deleted, created_at) VALUES ('ops-t1', 1, 'KB', 1001, 1, 0.92, 'hit', 0, '2026-05-10 10:00:02')",
            "INSERT INTO t_workflow (id, project_id, name, description, enabled, start_node_key, deleted) VALUES (6401, 6101, 'Support Workflow', '', 1, 'start', 0)",
            "INSERT INTO t_workflow_run (id, workflow_id, trace_id, status, elapsed_ms, source, created_at, finished_at, deleted) VALUES (6501, 6401, 'ops-t1', 'SUCCESS', 1500, 'TOOL', '2026-05-10 10:00:01', '2026-05-10 10:00:03', 0)",
            "INSERT INTO t_workflow_run (id, workflow_id, trace_id, status, elapsed_ms, source, error, created_at, finished_at, deleted) VALUES (6502, 6401, 'ops-w2', 'FAILED', 2200, 'MANUAL', 'node failed', '2026-05-11 10:00:01', '2026-05-11 10:00:04', 0)",
            "INSERT INTO t_mcp_server (id, project_id, name, endpoint, enabled, deleted) VALUES (6601, 6101, 'CRM MCP', 'http://mcp.local', 1, 0)",
            "INSERT INTO t_mcp_tool_call_audit (trace_id, source_type, project_id, agent_id, mcp_server_id, tool_name, status, success, elapsed_ms, error_summary, created_at, deleted) VALUES ('ops-t1', 'CONVERSATION', 6101, 6301, 6601, 'search_customer', 'SUCCESS', 1, 300, '', '2026-05-10 10:00:02', 0)",
            "INSERT INTO t_mcp_tool_call_audit (trace_id, source_type, project_id, agent_id, mcp_server_id, tool_name, status, success, elapsed_ms, error_summary, created_at, deleted) VALUES ('ops-t4', 'CONVERSATION', 6101, 6301, 6601, 'search_customer', 'FAILED', 0, 800, 'timeout', '2026-05-10 11:00:02', 0)"
    })
    void should_returnOperationsOverviewFromRuntimeFacts() throws Exception {
        String response = mockMvc.perform(get("/api/v1/ops/analytics/overview")
                        .param("projectId", "6101")
                        .param("from", "2026-05-10T00:00:00")
                        .param("to", "2026-05-12T00:00:00"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");

        assertThat(data.path("summary").path("conversationCount").asInt()).isEqualTo(3);
        assertThat(data.path("summary").path("conversationFailureRate").asDouble()).isEqualTo(0.3333);
        assertThat(data.path("summary").path("totalTokens").asInt()).isEqualTo(470);
        assertThat(data.path("summary").path("ragHitRate").asDouble()).isEqualTo(0.5);
        assertThat(data.path("summary").path("workflowSuccessRate").asDouble()).isEqualTo(0.5);
        assertThat(data.path("summary").path("mcpFailureRate").asDouble()).isEqualTo(0.5);

        JsonNode topAgent = data.path("agents").get(0);
        assertThat(topAgent.path("agentId").asLong()).isEqualTo(6301L);
        assertThat(topAgent.path("agentName").asText()).isEqualTo("Support Agent");
        assertThat(topAgent.path("conversationCount").asInt()).isEqualTo(2);
        assertThat(topAgent.path("failureRate").asDouble()).isEqualTo(0.5);
        assertThat(topAgent.path("totalTokens").asInt()).isEqualTo(350);

        assertThat(data.path("workflows").get(0).path("workflowName").asText()).isEqualTo("Support Workflow");
        assertThat(data.path("mcpTools").get(0).path("toolName").asText()).isEqualTo("search_customer");
        assertThat(data.path("errors").get(0).path("errorCode").asText()).isEqualTo("LLM_TIMEOUT");
        assertThat(data.path("slowLlmCalls").get(0).path("traceId").asText()).isEqualTo("ops-t2");
        assertThat(data.path("slowLlmCalls").get(0).path("latencyMs").asInt()).isEqualTo(3000);
        assertThat(data.path("riskConversations").get(0).path("traceId").asText()).isEqualTo("ops-t2");
        assertThat(data.path("riskConversations").get(0).path("ragHit").asBoolean()).isFalse();

        JsonNode diagnostics = data.path("diagnostics");
        assertThat(diagnostics).hasSizeGreaterThanOrEqualTo(4);
        assertThat(diagnostics.findValuesAsText("type"))
                .contains("CONVERSATION_FAILURE", "RAG_MISS", "WORKFLOW_FAILURE", "MCP_FAILURE");
        JsonNode topDiagnostic = diagnostics.get(0);
        assertThat(topDiagnostic.path("severity").asText()).isEqualTo("HIGH");
        assertThat(topDiagnostic.path("impactCount").asInt()).isGreaterThan(0);
        assertThat(topDiagnostic.path("recommendation").asText()).isNotBlank();
    }
}
