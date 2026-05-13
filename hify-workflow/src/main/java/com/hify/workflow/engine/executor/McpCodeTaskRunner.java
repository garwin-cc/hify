package com.hify.workflow.engine.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.api.McpClientService;
import com.hify.mcp.api.McpToolCallAuditRecord;
import com.hify.mcp.api.McpToolCallAuditService;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.WorkflowCallTrace;
import com.hify.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class McpCodeTaskRunner implements CodeTaskRunner {

    private final McpClientService mcpClientService;
    private final McpToolCallAuditService mcpToolCallAuditService;
    private final ObjectMapper objectMapper;

    @Override
    public String executorType() {
        return "MCP";
    }

    @Override
    public CodeTaskResult run(WorkflowNode node, CodeTaskConfig config, ExecutionContext ctx) {
        if (config.mcpServerId() == null) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "CODE_TASK 缺少 MCP Server ID");
        }
        if (!StringUtils.hasText(config.toolName())) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "CODE_TASK 缺少 MCP 工具名称");
        }
        String task = ctx.resolve(config.task());
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("task", task);
        arguments.put("nodeKey", node.nodeKey());
        arguments.put("timeoutSeconds", config.timeoutSeconds() == null ? 600 : config.timeoutSeconds());
        arguments.put("context", ctx.snapshot());
        long start = System.currentTimeMillis();
        try {
            String response = mcpClientService.callTool(config.mcpServerId(), config.toolName(), arguments);
            ctx.recordCall(new WorkflowCallTrace(
                    "MCP",
                    config.toolName(),
                    Map.of("mcpServerId", config.mcpServerId(), "toolName", config.toolName(), "argumentKeys", arguments.keySet()),
                    Map.of("resultPreview", response == null ? "" : response),
                    "SUCCESS",
                    null,
                    elapsed(start)));
            recordAudit(ctx, node, config, arguments, System.currentTimeMillis() - start, true, response, null);
            return parseResult(response);
        } catch (Exception e) {
            ctx.recordCall(new WorkflowCallTrace(
                    "MCP",
                    config.toolName(),
                    Map.of("mcpServerId", config.mcpServerId(), "toolName", config.toolName(), "argumentKeys", arguments.keySet()),
                    Map.of(),
                    "FAILED",
                    e.getMessage(),
                    elapsed(start)));
            recordAudit(ctx, node, config, arguments, System.currentTimeMillis() - start, false, null, e.getMessage());
            throw e;
        }
    }

    private void recordAudit(ExecutionContext ctx, WorkflowNode node, CodeTaskConfig config,
                             Map<String, Object> arguments, long elapsedMs, boolean success,
                             String result, String error) {
        try {
            mcpToolCallAuditService.record(McpToolCallAuditRecord.builder()
                    .sourceType("WORKFLOW")
                    .workflowRunId(ctx.getWorkflowRunId())
                    .workflowNodeKey(node.nodeKey())
                    .mcpServerId(config.mcpServerId())
                    .toolName(config.toolName())
                    .arguments(arguments)
                    .elapsedMs(elapsedMs)
                    .success(success)
                    .result(result)
                    .error(error)
                    .build());
        } catch (Exception ignored) {
            // Audit must never change workflow execution semantics.
        }
    }

    private CodeTaskResult parseResult(String response) {
        if (!StringUtils.hasText(response)) {
            return new CodeTaskResult("SUCCESS", "", Collections.emptyList(), "", "", null);
        }
        try {
            Map<String, Object> value = objectMapper.readValue(response, new TypeReference<>() {
            });
            return new CodeTaskResult(
                    stringValue(value.get("status"), "SUCCESS"),
                    stringValue(value.get("summary"), response),
                    listValue(value.get("changedFiles")),
                    stringValue(value.get("diff"), ""),
                    stringValue(value.get("logs"), ""),
                    stringValue(value.get("error"), null));
        } catch (Exception e) {
            return new CodeTaskResult("SUCCESS", response, Collections.emptyList(), "", response, null);
        }
    }

    private static String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static List<String> listValue(Object value) {
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }
}
