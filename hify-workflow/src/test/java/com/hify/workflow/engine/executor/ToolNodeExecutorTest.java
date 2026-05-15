package com.hify.workflow.engine.executor;

import com.hify.mcp.api.McpClientService;
import com.hify.mcp.api.McpToolCallRequest;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.WorkflowCallTrace;
import com.hify.workflow.engine.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolNodeExecutorTest {

    @Test
    void should_call_mcp_tool_and_write_response_when_tool_node_executes() {
        CapturingMcpClientService mcpClientService = new CapturingMcpClientService();
        ToolNodeExecutor executor = new ToolNodeExecutor(mcpClientService);
        ExecutionContext ctx = new ExecutionContext(99L, "find finance docs");
        ctx.set("classify", "intent", "finance");
        RecordingTraceSink traceSink = new RecordingTraceSink();
        ctx.bindCurrentNodeRun(100L, "tool", "TOOL", traceSink);
        Map<String, Object> inputMapping = new LinkedHashMap<>();
        inputMapping.put("query", "{{start.userMessage}}");
        inputMapping.put("intent", "{{classify.intent}}");
        inputMapping.put("limit", 3);

        executor.execute(
                new WorkflowNode("tool", "TOOL", "工具调用"),
                new ToolConfig(7L, "search", inputMapping, "result", 12),
                ctx);

        assertThat(mcpClientService.request.getMcpServerId()).isEqualTo(7L);
        assertThat(mcpClientService.request.getToolName()).isEqualTo("search");
        assertThat(mcpClientService.request.getTimeoutMs()).isEqualTo(12000);
        assertThat(mcpClientService.request.getWorkflowRunId()).isEqualTo(99L);
        assertThat(mcpClientService.request.getWorkflowNodeKey()).isEqualTo("tool");
        assertThat(mcpClientService.request.getSourceType()).isEqualTo("WORKFLOW");
        assertThat(mcpClientService.request.getArguments())
                .containsEntry("query", "find finance docs")
                .containsEntry("intent", "finance")
                .containsEntry("limit", 3);
        assertThat(ctx.get("tool", "result")).isEqualTo("{\"ok\":true}");
        assertThat(traceSink.trace.status()).isEqualTo("SUCCESS");
    }

    private static class CapturingMcpClientService implements McpClientService {

        private McpToolCallRequest request;

        @Override
        public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
            throw new UnsupportedOperationException("request overload expected");
        }

        @Override
        public String callTool(McpToolCallRequest request) {
            this.request = request;
            return "{\"ok\":true}";
        }

        @Override
        public java.util.List<String> listTools(Long mcpServerId) {
            return java.util.List.of();
        }
    }

    private static class RecordingTraceSink implements com.hify.workflow.engine.WorkflowCallTraceSink {

        private WorkflowCallTrace trace;

        @Override
        public void record(Long workflowRunId, Long workflowNodeRunId, String nodeKey,
                           String nodeType, WorkflowCallTrace trace) {
            this.trace = trace;
        }
    }
}
