package com.hify.conversation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.support.HifyMockIntegrationTest;
import com.hify.model.api.ChatRequest;
import com.hify.model.domain.adapter.MockProviderAdapter;
import com.hify.mcp.api.McpClientService;
import com.hify.mcp.api.McpService;
import com.hify.mcp.api.McpToolResp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChatControllerIntegrationTest extends HifyMockIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private McpService mcpService;

    @MockBean
    private McpClientService mcpClientService;

    @Test
    @Sql(statements = {
            "DELETE FROM t_chat_message WHERE session_id = 3101",
            "DELETE FROM t_chat_session WHERE id = 3101",
            "DELETE FROM t_agent_tool WHERE agent_id = 3001",
            "DELETE FROM t_agent WHERE id = 3001",
            "DELETE FROM t_model_config WHERE id = 2001",
            "DELETE FROM t_provider WHERE id = 1001",
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1001, 'Mock Chat Provider', 'OPENAI', '', '{\"apiKey\":\"sk-mock-token\"}', 1, 0, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted) VALUES (2001, 1001, 'Mock Chat Model', 'mock-chat', 'CHAT', 8192, '{}', 1, 0, 0, 0)",
            "INSERT INTO t_agent (id, name, description, system_prompt, model_config_id, workflow_id, knowledge_base_ids, temperature, max_turns, max_tokens, max_context_turns, enabled, version, created_by, deleted) VALUES (3001, 'Mock Chat Agent', '', '你是 Hify 助手', 2001, NULL, '[]', 0.70, 20, 1024, 5, 1, 0, 0, 0)",
            "INSERT INTO t_chat_session (id, agent_id, user_id, title, status, message_count, created_by, deleted) VALUES (3101, 3001, 0, '普通问答', 'ACTIVE', 0, 0, 0)"
    }, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Sql(statements = {
            "DELETE FROM t_chat_message WHERE session_id = 3101",
            "DELETE FROM t_chat_session WHERE id = 3101",
            "DELETE FROM t_agent_tool WHERE agent_id = 3001",
            "DELETE FROM t_agent WHERE id = 3001",
            "DELETE FROM t_model_config WHERE id = 2001",
            "DELETE FROM t_provider WHERE id = 1001"
    }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    void should_streamDeltaAndPersistMessages_when_normalChatQuestionIsSent() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("content", "你好"));

        MvcResult started = mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", 3101L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        List<JsonNode> events = parseSseEvents(completed.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<JsonNode> deltaEvents = events.stream()
                .filter(event -> "delta".equals(event.path("type").asText()))
                .toList();
        String deltaContent = deltaEvents.stream()
                .map(event -> event.path("content").asText())
                .reduce("", String::concat);

        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_chat_message WHERE session_id = ? AND role = 'user' AND deleted = 0",
                Integer.class, 3101L);
        Integer assistantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_chat_message WHERE session_id = ? AND role = 'assistant' AND deleted = 0",
                Integer.class, 3101L);
        String assistantContent = jdbcTemplate.queryForObject(
                "SELECT content FROM t_chat_message WHERE session_id = ? AND role = 'assistant' AND deleted = 0",
                String.class, 3101L);

        assertThat(deltaEvents)
                .isNotEmpty()
                .allSatisfy(event -> assertThat(event.path("content").asText()).isNotBlank());
        assertThat(events).last().extracting(event -> event.path("type").asText()).isEqualTo("done");
        assertThat(userCount).isEqualTo(1);
        assertThat(assistantCount).isEqualTo(1);
        assertThat(assistantContent).isEqualTo(deltaContent);
    }

    @Test
    @Sql(statements = {
            "DELETE FROM t_chat_message WHERE session_id = 3201",
            "DELETE FROM t_chat_session WHERE id = 3201",
            "DELETE FROM t_agent_tool WHERE agent_id = 3002",
            "DELETE FROM t_agent WHERE id = 3002",
            "DELETE FROM t_mcp_tool WHERE id = 4001",
            "DELETE FROM t_mcp_server WHERE id = 4101",
            "DELETE FROM t_model_config WHERE id = 2002",
            "DELETE FROM t_provider WHERE id = 1002",
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1002, 'Mock Tool Provider', 'OPENAI', '', '{\"apiKey\":\"sk-mock-token\"}', 1, 0, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted) VALUES (2002, 1002, 'Mock Tool Model', 'mock-chat', 'CHAT', 8192, '{}', 1, 0, 0, 0)",
            "INSERT INTO t_agent (id, name, description, system_prompt, model_config_id, workflow_id, knowledge_base_ids, temperature, max_turns, max_tokens, max_context_turns, enabled, version, created_by, deleted) VALUES (3002, 'Mock Tool Agent', '', '你是 Hify 助手', 2002, NULL, '[]', 0.70, 20, 1024, 5, 1, 0, 0, 0)",
            "INSERT INTO t_mcp_server (id, name, description, endpoint, auth_type, auth_config, enabled, created_by, deleted) VALUES (4101, 'Mock MCP Server', '', 'http://mock-mcp', 'NONE', '{}', 1, 0, 0)",
            "INSERT INTO t_mcp_tool (id, mcp_server_id, name, description, input_schema, created_by, deleted) VALUES (4001, 4101, 'check_refund_eligibility', 'Check refund eligibility', '{\"type\":\"object\",\"properties\":{\"orderId\":{\"type\":\"string\"}}}', 0, 0)",
            "INSERT INTO t_agent_tool (id, agent_id, tool_id) VALUES (4201, 3002, 4001)",
            "INSERT INTO t_chat_session (id, agent_id, user_id, title, status, message_count, created_by, deleted) VALUES (3201, 3002, 0, '工具调用成功', 'ACTIVE', 0, 0, 0)"
    }, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Sql(statements = {
            "DELETE FROM t_chat_message WHERE session_id = 3201",
            "DELETE FROM t_chat_session WHERE id = 3201",
            "DELETE FROM t_agent_tool WHERE agent_id = 3002",
            "DELETE FROM t_agent WHERE id = 3002",
            "DELETE FROM t_mcp_tool WHERE id = 4001",
            "DELETE FROM t_mcp_server WHERE id = 4101",
            "DELETE FROM t_model_config WHERE id = 2002",
            "DELETE FROM t_provider WHERE id = 1002"
    }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    void should_completeStreamAndPersistOneAssistantMessage_when_functionCallingToolSucceeds() throws Exception {
        reset(mcpService, mcpClientService);
        when(mcpService.listEnabledToolsByIds(List.of(4001L))).thenReturn(List.of(refundTool()));
        when(mcpClientService.callTool(any(), eq("check_refund_eligibility"), any()))
                .thenReturn("{\"eligible\":true,\"reason\":\"within refund window\"}");

        List<JsonNode> events = sendChatMessage(3201L, "帮我查一下订单 ORD-1001 能不能退款");

        Integer assistantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_chat_message WHERE session_id = ? AND role = 'assistant' AND deleted = 0",
                Integer.class, 3201L);

        assertThat(events).last().extracting(event -> event.path("type").asText()).isEqualTo("done");
        assertThat(assistantCount).isEqualTo(1);
        verify(mcpClientService).callTool(any(), eq("check_refund_eligibility"), any());
    }

    @Test
    @Sql(statements = {
            "DELETE FROM t_chat_message WHERE session_id = 3301",
            "DELETE FROM t_chat_session WHERE id = 3301",
            "DELETE FROM t_agent_tool WHERE agent_id = 3003",
            "DELETE FROM t_agent WHERE id = 3003",
            "DELETE FROM t_mcp_tool WHERE id = 4002",
            "DELETE FROM t_mcp_server WHERE id = 4102",
            "DELETE FROM t_model_config WHERE id = 2003",
            "DELETE FROM t_provider WHERE id = 1003",
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1003, 'Mock Tool Failure Provider', 'OPENAI', '', '{\"apiKey\":\"sk-mock-token\"}', 1, 0, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted) VALUES (2003, 1003, 'Mock Tool Failure Model', 'mock-chat', 'CHAT', 8192, '{}', 1, 0, 0, 0)",
            "INSERT INTO t_agent (id, name, description, system_prompt, model_config_id, workflow_id, knowledge_base_ids, temperature, max_turns, max_tokens, max_context_turns, enabled, version, created_by, deleted) VALUES (3003, 'Mock Tool Failure Agent', '', '你是 Hify 助手', 2003, NULL, '[]', 0.70, 20, 1024, 5, 1, 0, 0, 0)",
            "INSERT INTO t_mcp_server (id, name, description, endpoint, auth_type, auth_config, enabled, created_by, deleted) VALUES (4102, 'Mock MCP Failure Server', '', 'http://mock-mcp', 'NONE', '{}', 1, 0, 0)",
            "INSERT INTO t_mcp_tool (id, mcp_server_id, name, description, input_schema, created_by, deleted) VALUES (4002, 4102, 'check_refund_eligibility', 'Check refund eligibility', '{\"type\":\"object\",\"properties\":{\"orderId\":{\"type\":\"string\"}}}', 0, 0)",
            "INSERT INTO t_agent_tool (id, agent_id, tool_id) VALUES (4202, 3003, 4002)",
            "INSERT INTO t_chat_session (id, agent_id, user_id, title, status, message_count, created_by, deleted) VALUES (3301, 3003, 0, '工具调用失败', 'ACTIVE', 0, 0, 0)"
    }, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Sql(statements = {
            "DELETE FROM t_chat_message WHERE session_id = 3301",
            "DELETE FROM t_chat_session WHERE id = 3301",
            "DELETE FROM t_agent_tool WHERE agent_id = 3003",
            "DELETE FROM t_agent WHERE id = 3003",
            "DELETE FROM t_mcp_tool WHERE id = 4002",
            "DELETE FROM t_mcp_server WHERE id = 4102",
            "DELETE FROM t_model_config WHERE id = 2003",
            "DELETE FROM t_provider WHERE id = 1003"
    }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    void should_completeStreamAndPersistOneAssistantMessage_when_functionCallingToolFails() throws Exception {
        reset(mcpService, mcpClientService);
        when(mcpService.listEnabledToolsByIds(List.of(4002L))).thenReturn(List.of(refundTool()));
        when(mcpClientService.callTool(any(), eq("check_refund_eligibility"), any()))
                .thenThrow(new RuntimeException("mock tool unavailable"));

        List<JsonNode> events = sendChatMessage(3301L, "帮我查一下订单 ORD-1001 能不能退款");

        Integer assistantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_chat_message WHERE session_id = ? AND role = 'assistant' AND deleted = 0",
                Integer.class, 3301L);

        assertThat(events).last().extracting(event -> event.path("type").asText()).isEqualTo("done");
        assertThat(assistantCount).isEqualTo(1);
        verify(mcpClientService).callTool(any(), eq("check_refund_eligibility"), any());
    }

    @Test
    @Sql(statements = {
            "DELETE FROM t_chat_message WHERE session_id = 3401",
            "DELETE FROM t_chat_session WHERE id = 3401",
            "DELETE FROM t_agent_tool WHERE agent_id = 3004",
            "DELETE FROM t_agent WHERE id = 3004",
            "DELETE FROM t_model_config WHERE id = 2004",
            "DELETE FROM t_provider WHERE id = 1004",
            "INSERT INTO t_provider (id, name, type, base_url, auth_config, enabled, sort_order, created_by, deleted) VALUES (1004, 'Mock Multi Turn Provider', 'OPENAI', '', '{\"apiKey\":\"sk-mock-token\"}', 1, 0, 0, 0)",
            "INSERT INTO t_model_config (id, provider_id, name, model_id, model_type, context_size, extra_params, enabled, sort_order, created_by, deleted) VALUES (2004, 1004, 'Mock Multi Turn Model', 'mock-chat', 'CHAT', 8192, '{}', 1, 0, 0, 0)",
            "INSERT INTO t_agent (id, name, description, system_prompt, model_config_id, workflow_id, knowledge_base_ids, temperature, max_turns, max_tokens, max_context_turns, enabled, version, created_by, deleted) VALUES (3004, 'Mock Multi Turn Agent', '', '你是 Hify 助手', 2004, NULL, '[]', 0.70, 20, 1024, 5, 1, 0, 0, 0)",
            "INSERT INTO t_chat_session (id, agent_id, user_id, title, status, message_count, created_by, deleted) VALUES (3401, 3004, 0, '多轮上下文', 'ACTIVE', 0, 0, 0)"
    }, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    @Sql(statements = {
            "DELETE FROM t_chat_message WHERE session_id = 3401",
            "DELETE FROM t_chat_session WHERE id = 3401",
            "DELETE FROM t_agent_tool WHERE agent_id = 3004",
            "DELETE FROM t_agent WHERE id = 3004",
            "DELETE FROM t_model_config WHERE id = 2004",
            "DELETE FROM t_provider WHERE id = 1004"
    }, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, config = @SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED))
    void should_passConversationHistoryInAscendingOrder_when_multipleMessagesAreSentInSameSession() throws Exception {
        MockProviderAdapter.clearStreamRequests();

        sendChatMessage(3401L, "第一条");
        sendChatMessage(3401L, "第二条");
        sendChatMessage(3401L, "第三条");

        List<ChatRequest> requests = MockProviderAdapter.streamRequests();
        ChatRequest thirdRequest = requests.get(2);
        List<String> messageContents = thirdRequest.getMessages().stream()
                .map(message -> message.getContent())
                .toList();
        List<String> userContents = thirdRequest.getMessages().stream()
                .filter(message -> "user".equals(message.getRole()))
                .map(message -> message.getContent())
                .toList();

        assertThat(requests).hasSize(3);
        assertThat(messageContents).contains("第一条", "第二条");
        assertThat(userContents).containsExactly("第一条", "第二条", "第三条");
        assertThat(messageContents).last().isEqualTo("第三条");
    }

    private List<JsonNode> sendChatMessage(Long sessionId, String content) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("content", content));

        MvcResult started = mockMvc.perform(post("/api/v1/chat/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andReturn();

        return parseSseEvents(completed.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private McpToolResp refundTool() {
        McpToolResp tool = new McpToolResp();
        tool.setId(4001L);
        tool.setMcpServerId(4101L);
        tool.setName("check_refund_eligibility");
        tool.setDescription("Check refund eligibility");
        tool.setInputSchema(Map.of(
                "type", "object",
                "properties", Map.of(
                        "orderId", Map.of("type", "string")
                )
        ));
        return tool;
    }

    private List<JsonNode> parseSseEvents(String stream) throws Exception {
        List<JsonNode> events = new ArrayList<>();
        for (String line : stream.split("\\R")) {
            if (line.startsWith("data:")) {
                events.add(objectMapper.readTree(line.substring("data:".length()).trim()));
            }
        }
        return events;
    }
}
