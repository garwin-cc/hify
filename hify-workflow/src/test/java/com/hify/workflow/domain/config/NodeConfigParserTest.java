package com.hify.workflow.domain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeConfigParserTest {

    private final NodeConfigParser parser = new NodeConfigParser(new ObjectMapper());

    @Test
    void parsesLlmConfigIntoTypedRecord() throws Exception {
        NodeConfig config = parser.parse("LLM", new ObjectMapper().readTree("""
                {
                  "prompt": "提取订单号",
                  "modelConfigId": 1,
                  "outputVariable": "answer",
                  "temperature": 0.2,
                  "maxTokens": 256
                }
                """));

        assertThat(config).isInstanceOf(LlmNodeConfig.class);
        LlmNodeConfig llm = (LlmNodeConfig) config;
        assertThat(llm.prompt()).isEqualTo("提取订单号");
        assertThat(llm.modelConfigId()).isEqualTo(1L);
        assertThat(llm.outputVariable()).isEqualTo("answer");
        assertThat(llm.maxTokens()).isEqualTo(256);
    }

    @Test
    void parsesConditionConfigIntoTypedRecord() throws Exception {
        NodeConfig config = parser.parse("CONDITION", new ObjectMapper().readTree("""
                {
                  "expression": "'{{classify.intent}}' == 'refund'",
                  "outputVariable": "matched"
                }
                """));

        assertThat(config).isInstanceOf(ConditionNodeConfig.class);
        ConditionNodeConfig condition = (ConditionNodeConfig) config;
        assertThat(condition.expression()).isEqualTo("'{{classify.intent}}' == 'refund'");
        assertThat(condition.outputVariable()).isEqualTo("matched");
    }

    @Test
    void parsesEndConfigIntoTypedRecord() throws Exception {
        NodeConfig config = parser.parse("END", new ObjectMapper().readTree("""
                {
                  "outputVariable": "llm.answer"
                }
                """));

        assertThat(config).isInstanceOf(EndNodeConfig.class);
        EndNodeConfig end = (EndNodeConfig) config;
        assertThat(end.outputVariable()).isEqualTo("llm.answer");
    }

    @Test
    void parsesCodeTaskConfigIntoTypedRecord() throws Exception {
        NodeConfig config = parser.parse("CODE_TASK", new ObjectMapper().readTree("""
                {
                  "task": "实现 {{start.userMessage}}",
                  "executor": "MCP",
                  "mcpServerId": 7,
                  "toolName": "code_worker",
                  "timeoutSeconds": 600,
                  "outputVariable": "result"
                }
                """));

        assertThat(config).isInstanceOf(CodeTaskNodeConfig.class);
        CodeTaskNodeConfig codeTask = (CodeTaskNodeConfig) config;
        assertThat(codeTask.task()).isEqualTo("实现 {{start.userMessage}}");
        assertThat(codeTask.executor()).isEqualTo("MCP");
        assertThat(codeTask.mcpServerId()).isEqualTo(7L);
        assertThat(codeTask.toolName()).isEqualTo("code_worker");
        assertThat(codeTask.outputVariable()).isEqualTo("result");
    }

    @Test
    void should_parse_tool_config_into_typed_record() throws Exception {
        NodeConfig config = parser.parse("TOOL", new ObjectMapper().readTree("""
                {
                  "mcpServerId": 7,
                  "toolName": "search",
                  "inputMapping": {
                    "query": "{{start.userMessage}}"
                  },
                  "timeoutSeconds": 12,
                  "outputVariable": "result"
                }
                """));

        assertThat(config).isInstanceOf(ToolNodeConfig.class);
        ToolNodeConfig tool = (ToolNodeConfig) config;
        assertThat(tool.mcpServerId()).isEqualTo(7L);
        assertThat(tool.toolName()).isEqualTo("search");
        assertThat(tool.inputMapping()).containsEntry("query", "{{start.userMessage}}");
        assertThat(tool.timeoutSeconds()).isEqualTo(12);
        assertThat(tool.outputVariable()).isEqualTo("result");
    }

    @Test
    void should_parse_tool_execution_config_when_node_type_is_tool() throws Exception {
        com.hify.workflow.engine.NodeConfigDef config = parser.parseExecutionConfig("TOOL", """
                {
                  "mcpServerId": 7,
                  "toolName": "search",
                  "inputMapping": {
                    "query": "{{start.userMessage}}"
                  },
                  "timeoutSeconds": 12,
                  "outputVariable": "result"
                }
                """);

        assertThat(config).isInstanceOf(com.hify.workflow.engine.executor.ToolConfig.class);
        com.hify.workflow.engine.executor.ToolConfig tool =
                (com.hify.workflow.engine.executor.ToolConfig) config;
        assertThat(tool.mcpServerId()).isEqualTo(7L);
        assertThat(tool.toolName()).isEqualTo("search");
        assertThat(tool.timeoutSeconds()).isEqualTo(12);
        assertThat(tool.outputVariable()).isEqualTo("result");
    }

    @Test
    void should_parse_api_call_config_with_body_and_response_json_path() throws Exception {
        NodeConfig config = parser.parse("API_CALL", new ObjectMapper().readTree("""
                {
                  "url": "https://api.example.test/orders",
                  "method": "PATCH",
                  "headers": {
                    "Content-Type": "application/json"
                  },
                  "body": "{\\"name\\":\\"{{start.userMessage}}\\"}",
                  "responseJsonPath": "$.data.id",
                  "outputVariable": "orderId"
                }
                """));

        assertThat(config).isInstanceOf(ApiCallNodeConfig.class);
        ApiCallNodeConfig apiCall = (ApiCallNodeConfig) config;
        assertThat(apiCall.method()).isEqualTo("PATCH");
        assertThat(apiCall.body()).contains("start.userMessage");
        assertThat(apiCall.responseJsonPath()).isEqualTo("$.data.id");

        com.hify.workflow.engine.NodeConfigDef executionConfig = parser.parseExecutionConfig("API_CALL", """
                {
                  "url": "https://api.example.test/orders",
                  "method": "PATCH",
                  "body": "{\\"name\\":\\"{{start.userMessage}}\\"}",
                  "responseJsonPath": "$.data.id",
                  "outputVariable": "orderId"
                }
                """);

        assertThat(executionConfig).isInstanceOf(com.hify.workflow.engine.executor.ApiCallConfig.class);
        com.hify.workflow.engine.executor.ApiCallConfig apiExecutionConfig =
                (com.hify.workflow.engine.executor.ApiCallConfig) executionConfig;
        assertThat(apiExecutionConfig.body()).contains("start.userMessage");
        assertThat(apiExecutionConfig.responseJsonPath()).isEqualTo("$.data.id");
    }

    @Test
    void should_parse_reply_execution_config_when_node_type_is_reply() {
        com.hify.workflow.engine.NodeConfigDef config = parser.parseExecutionConfig("REPLY", """
                {
                  "content": "处理中：{{start.userMessage}}"
                }
                """);

        assertThat(config).isInstanceOf(com.hify.workflow.engine.executor.ReplyConfig.class);
        com.hify.workflow.engine.executor.ReplyConfig reply =
                (com.hify.workflow.engine.executor.ReplyConfig) config;
        assertThat(reply.content()).contains("start.userMessage");
    }

    @Test
    void should_parse_variable_assigner_config_into_typed_record() throws Exception {
        NodeConfig config = parser.parse("VARIABLE_ASSIGNER", new ObjectMapper().readTree("""
                {
                  "assignments": {
                    "summary": "用户：{{start.userMessage}}",
                    "status": "READY"
                  }
                }
                """));

        assertThat(config).isInstanceOf(VariableAssignerNodeConfig.class);
        VariableAssignerNodeConfig assigner = (VariableAssignerNodeConfig) config;
        assertThat(assigner.assignments()).containsEntry("summary", "用户：{{start.userMessage}}");
        assertThat(assigner.assignments()).containsEntry("status", "READY");
    }

    @Test
    void should_parse_variable_assigner_execution_config_when_node_type_is_variable_assigner() {
        com.hify.workflow.engine.NodeConfigDef config = parser.parseExecutionConfig("VARIABLE_ASSIGNER", """
                {
                  "assignments": {
                    "summary": "用户：{{start.userMessage}}"
                  }
                }
                """);

        assertThat(config).isInstanceOf(com.hify.workflow.engine.executor.VariableAssignerConfig.class);
        com.hify.workflow.engine.executor.VariableAssignerConfig assigner =
                (com.hify.workflow.engine.executor.VariableAssignerConfig) config;
        assertThat(assigner.assignments()).containsEntry("summary", "用户：{{start.userMessage}}");
    }

    @Test
    void rejectsUnsupportedNodeType() {
        assertThatThrownBy(() -> parser.parse("UNKNOWN", new ObjectMapper().createObjectNode()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的节点类型");
    }
}
