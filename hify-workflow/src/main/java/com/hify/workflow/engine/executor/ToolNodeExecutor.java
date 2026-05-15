package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.api.McpClientService;
import com.hify.mcp.api.McpToolCallRequest;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowCallTrace;
import com.hify.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ToolNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    private final McpClientService mcpClientService;

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        ToolConfig toolConfig = requireConfig(config, ToolConfig.class);
        Map<String, Object> arguments = resolveArguments(toolConfig.inputMapping(), ctx);
        long startedAt = System.currentTimeMillis();
        try {
            validate(toolConfig);
            McpToolCallRequest request = new McpToolCallRequest();
            request.setMcpServerId(toolConfig.mcpServerId());
            request.setToolName(toolConfig.toolName());
            request.setArguments(arguments);
            request.setWorkflowRunId(ctx.getWorkflowRunId());
            request.setWorkflowNodeKey(node.nodeKey());
            request.setSourceType("WORKFLOW");
            String response = mcpClientService.callTool(request);
            ctx.recordCall(new WorkflowCallTrace(
                    "MCP",
                    toolConfig.toolName(),
                    Map.of("mcpServerId", toolConfig.mcpServerId(),
                            "toolName", toolConfig.toolName(),
                            "argumentKeys", arguments.keySet()),
                    Map.of("resultPreview", response == null ? "" : response),
                    "SUCCESS",
                    null,
                    elapsed(startedAt)));
            ctx.set(node.nodeKey(), outputVariable(toolConfig.outputVariable()), response);
        } catch (Exception e) {
            ctx.recordCall(new WorkflowCallTrace(
                    "MCP",
                    toolConfig.toolName() == null ? "" : toolConfig.toolName(),
                    Map.of("argumentKeys", arguments.keySet()),
                    Map.of(),
                    "FAILED",
                    e.getMessage(),
                    elapsed(startedAt)));
            throw toExecuteException(node, e);
        }
    }

    @Override
    public String nodeType() {
        return "TOOL";
    }

    private void validate(ToolConfig config) {
        if (config.mcpServerId() == null) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "TOOL 缺少 MCP Server ID");
        }
        if (!StringUtils.hasText(config.toolName())) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "TOOL 缺少 MCP 工具名称");
        }
    }

    private Map<String, Object> resolveArguments(Map<String, Object> inputMapping, ExecutionContext ctx) {
        if (inputMapping == null || inputMapping.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> arguments = new LinkedHashMap<>();
        inputMapping.forEach((key, value) -> arguments.put(key, resolveValue(value, ctx)));
        return arguments;
    }

    private Object resolveValue(Object value, ExecutionContext ctx) {
        if (value instanceof String text) {
            return ctx.resolve(text);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> resolved = new LinkedHashMap<>();
            map.forEach((key, childValue) -> resolved.put(String.valueOf(key), resolveValue(childValue, ctx)));
            return resolved;
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> resolved = new ArrayList<>();
            iterable.forEach(item -> resolved.add(resolveValue(item, ctx)));
            return resolved;
        }
        return value;
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }
}
