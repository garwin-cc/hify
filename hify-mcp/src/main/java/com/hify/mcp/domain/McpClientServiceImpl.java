package com.hify.mcp.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.metrics.HifyMetrics;
import com.hify.mcp.api.McpClientService;
import com.hify.mcp.api.McpToolCallAuditRecord;
import com.hify.mcp.api.McpToolCallAuditService;
import com.hify.mcp.api.McpToolCallRequest;
import com.hify.mcp.infra.McpServerMapper;
import com.hify.mcp.infra.McpToolMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientServiceImpl implements McpClientService {

    private final McpServerMapper     mcpServerMapper;
    private final McpToolMapper       mcpToolMapper;
    private final McpSdkClientFactory mcpSdkClientFactory;
    private final McpRawHttpClient    mcpRawHttpClient;
    private final HifyMetrics         hifyMetrics;
    private final McpEndpointGuard    mcpEndpointGuard;
    private final McpToolSchemaValidator schemaValidator;
    private final McpToolCallAuditService auditService;
    private final @Qualifier("mcpExecutor") ThreadPoolExecutor mcpExecutor;

    @Override
    public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
        McpToolCallRequest request = new McpToolCallRequest();
        request.setMcpServerId(mcpServerId);
        request.setToolName(toolName);
        request.setArguments(arguments);
        request.setSourceType("UNKNOWN");
        return callTool(request);
    }

    @Override
    public String callTool(McpToolCallRequest request) {
        McpServerPo server = findEnabledServer(request.getMcpServerId());
        McpToolPo tool = findTool(server.getId(), request.getToolName());
        ensureProjectAccess(server, request.getProjectId());
        mcpEndpointGuard.validate(server.getEndpoint());
        long start = System.currentTimeMillis();
        int timeoutMs = effectiveTimeoutMs(request, server, tool);
        if (tool.getSchemaValidationEnabled() == null || tool.getSchemaValidationEnabled() == 1) {
            try {
                schemaValidator.validate(tool.getInputSchema(), request.getArguments());
            } catch (BizException e) {
                hifyMetrics.recordMcpToolCall(server.getId(), request.getToolName(), "schema_invalid");
                recordAudit(request, server, false, "SCHEMA_INVALID", start, 0, timeoutMs, null, e.getMessage());
                throw e;
            }
        }
        int maxRetries = effectiveRetryTimes(server, tool);
        log.info("mcp client call start serverId={} tool={} argumentKeys={}",
                server.getId(), request.getToolName(), request.getArguments() == null ? List.of() : request.getArguments().keySet());
        int attempts = 0;
        Throwable lastError = null;
        while (attempts <= maxRetries) {
            attempts++;
            try {
                String text = callToolWithTimeout(server, request, timeoutMs);
                log.info("mcp client call end serverId={} tool={} elapsedMs={} resultLength={}",
                        server.getId(), request.getToolName(), System.currentTimeMillis() - start, text.length());
                hifyMetrics.recordMcpToolCall(server.getId(), request.getToolName(), "success");
                recordAudit(request, server, true, "SUCCESS", start, attempts - 1, timeoutMs, text, null);
                return text;
            } catch (TimeoutException e) {
                lastError = e;
                if (attempts > maxRetries) {
                    hifyMetrics.recordMcpToolCall(server.getId(), request.getToolName(), "timeout");
                    recordAudit(request, server, false, "TIMEOUT", start, attempts - 1, timeoutMs, null, "MCP 工具调用超时");
                    return fallbackOrThrow(server, tool, new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, "MCP 工具调用超时"));
                }
                sleepBeforeRetry(server);
            } catch (BizException e) {
                lastError = e;
                if (attempts > maxRetries) {
                    hifyMetrics.recordMcpToolCall(server.getId(), request.getToolName(), "failure");
                    recordAudit(request, server, false, "FAILED", start, attempts - 1, timeoutMs, null, e.getMessage());
                    return fallbackOrThrow(server, tool, e);
                }
                sleepBeforeRetry(server);
            } catch (Throwable e) {
                lastError = e;
                if (attempts > maxRetries) {
                    hifyMetrics.recordMcpToolCall(server.getId(), request.getToolName(), "failure");
                    recordAudit(request, server, false, "FAILED", start, attempts - 1, timeoutMs, null, e.getMessage());
                    return fallbackOrThrow(server, tool, new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, e.getMessage()));
                }
                sleepBeforeRetry(server);
            }
        }
        throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, lastError == null ? "MCP 工具调用失败" : lastError.getMessage());
    }

    @Override
    public List<String> listTools(Long mcpServerId) {
        McpServerPo server = findEnabledServer(mcpServerId);
        mcpEndpointGuard.validate(server.getEndpoint());
        try (McpSyncClient client = mcpSdkClientFactory.create(server.getEndpoint())) {
            return client.listTools().tools().stream()
                    .map(McpSchema.Tool::name)
                    .toList();
        } catch (Throwable e) {
            log.warn("mcp sdk list tools failed, fallback to raw http serverId={}", mcpServerId, e);
            try {
                return mcpRawHttpClient.listTools(mcpServerId, server.getEndpoint()).stream()
                        .map(McpToolPo::getName)
                        .toList();
            } catch (Throwable fallbackError) {
                log.warn("mcp raw http list tools failed serverId={}", mcpServerId, fallbackError);
                throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, fallbackError.getMessage());
            }
        }
    }

    private McpServerPo findEnabledServer(Long id) {
        McpServerPo server = mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServerPo>()
                .eq(McpServerPo::getId, id)
                .eq(McpServerPo::getEnabled, 1));
        if (server == null) {
            throw new BizException(ErrorCode.MCP_SERVER_NOT_FOUND, "MCP Server ID=" + id + " 不存在或未启用");
        }
        return server;
    }

    private McpToolPo findTool(Long serverId, String toolName) {
        McpToolPo tool = mcpToolMapper.selectOne(new LambdaQueryWrapper<McpToolPo>()
                .eq(McpToolPo::getMcpServerId, serverId)
                .eq(McpToolPo::getName, toolName)
                .last("LIMIT 1"));
        if (tool == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 工具不存在: " + toolName);
        }
        return tool;
    }

    private void ensureProjectAccess(McpServerPo server, Long projectId) {
        if (projectId == null) {
            return;
        }
        if ("PROJECT".equalsIgnoreCase(server.getVisibility()) && !projectId.equals(server.getProjectId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "当前项目无权访问该 MCP 工具");
        }
    }

    private String callToolOnce(McpServerPo server, McpToolCallRequest request) {
        try (McpSyncClient client = mcpSdkClientFactory.create(server.getEndpoint())) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(request.getToolName(), request.getArguments()));
            String text = extractText(result);
            if (Boolean.TRUE.equals(result.isError())) {
                throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED,
                        text.isBlank() ? "MCP 工具调用失败" : text);
            }
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Throwable e) {
            log.warn("mcp sdk tool call failed, fallback to raw http serverId={} tool={}", server.getId(), request.getToolName(), e);
            try {
                return mcpRawHttpClient.callTool(server.getEndpoint(), request.getToolName(), request.getArguments());
            } catch (Throwable fallbackError) {
                throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, fallbackError.getMessage());
            }
        }
    }

    private String callToolWithTimeout(McpServerPo server, McpToolCallRequest request, int timeoutMs)
            throws TimeoutException {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> callToolOnce(server, request), mcpExecutor);
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, "MCP 工具调用被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof BizException bizException) {
                throw bizException;
            }
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, cause == null ? "MCP 工具调用失败" : cause.getMessage());
        }
    }

    private String fallbackOrThrow(McpServerPo server, McpToolPo tool, BizException exception) {
        String strategy = tool.getFallbackStrategy() != null ? tool.getFallbackStrategy() : server.getFallbackStrategy();
        if ("RETURN_ERROR_MESSAGE".equalsIgnoreCase(strategy)) {
            return "工具调用失败: " + exception.getMessage();
        }
        throw exception;
    }

    private int effectiveRetryTimes(McpServerPo server, McpToolPo tool) {
        if (tool.getRetryTimes() != null) {
            return Math.max(0, tool.getRetryTimes());
        }
        return Math.max(0, server.getRetryTimes() == null ? 0 : server.getRetryTimes());
    }

    private int effectiveTimeoutMs(McpToolCallRequest request, McpServerPo server, McpToolPo tool) {
        if (request.getTimeoutMs() != null && request.getTimeoutMs() > 0) {
            return request.getTimeoutMs();
        }
        if (tool.getTimeoutMs() != null && tool.getTimeoutMs() > 0) {
            return tool.getTimeoutMs();
        }
        return server.getReadTimeoutMs() == null ? 30000 : server.getReadTimeoutMs();
    }

    private void sleepBeforeRetry(McpServerPo server) {
        int interval = server.getRetryIntervalMs() == null ? 300 : server.getRetryIntervalMs();
        if (interval <= 0) {
            return;
        }
        try {
            Thread.sleep(interval);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, "MCP 工具重试等待被中断", e);
        }
    }

    private void recordAudit(McpToolCallRequest request, McpServerPo server, boolean success, String status,
                             long startedAt, int retryCount, int timeoutMs, String result, String error) {
        auditService.record(McpToolCallAuditRecord.builder()
                .sourceType(request.getSourceType())
                .traceId(com.hify.common.log.TraceContext.ensureTraceId())
                .workspaceId(request.getWorkspaceId() == null ? server.getWorkspaceId() : request.getWorkspaceId())
                .projectId(request.getProjectId() == null ? server.getProjectId() : request.getProjectId())
                .agentId(request.getAgentId())
                .appId(request.getAppId())
                .apiKeyId(request.getApiKeyId())
                .userId(request.getUserId())
                .workflowId(request.getWorkflowId())
                .workflowRunId(request.getWorkflowRunId())
                .workflowNodeKey(request.getWorkflowNodeKey())
                .mcpServerId(server.getId())
                .toolName(request.getToolName())
                .status(status)
                .retryCount(retryCount)
                .timeoutMs(timeoutMs)
                .sourceId(request.getSourceId())
                .arguments(request.getArguments())
                .elapsedMs(System.currentTimeMillis() - startedAt)
                .success(success)
                .result(result)
                .error(error)
                .build());
    }

    private static String extractText(McpSchema.CallToolResult result) {
        if (result == null || result.content() == null) {
            return "";
        }
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }
}
