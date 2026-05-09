package com.hify.mcp.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.metrics.HifyMetrics;
import com.hify.mcp.api.McpClientService;
import com.hify.mcp.infra.McpServerMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpClientServiceImpl implements McpClientService {

    private final McpServerMapper     mcpServerMapper;
    private final McpSdkClientFactory mcpSdkClientFactory;
    private final McpRawHttpClient    mcpRawHttpClient;
    private final HifyMetrics         hifyMetrics;

    @Override
    public String callTool(Long mcpServerId, String toolName, Map<String, Object> arguments) {
        McpServerPo server = findEnabledServer(mcpServerId);
        long start = System.currentTimeMillis();
        log.info("mcp client call start serverId={} tool={} argumentKeys={}",
                mcpServerId, toolName, arguments == null ? List.of() : arguments.keySet());
        try (McpSyncClient client = mcpSdkClientFactory.create(server.getEndpoint())) {
            McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
            String text = extractText(result);
            if (Boolean.TRUE.equals(result.isError())) {
                throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED,
                        text.isBlank() ? "MCP 工具调用失败" : text);
            }
            log.info("mcp client call end serverId={} tool={} elapsedMs={} resultLength={}",
                    mcpServerId, toolName, System.currentTimeMillis() - start, text.length());
            hifyMetrics.recordMcpToolCall(mcpServerId, toolName, "success");
            return text;
        } catch (BizException e) {
            log.warn("mcp client call biz failed serverId={} tool={} elapsedMs={} message={}",
                    mcpServerId, toolName, System.currentTimeMillis() - start, e.getMessage());
            hifyMetrics.recordMcpToolCall(mcpServerId, toolName, "failure");
            throw e;
        } catch (Throwable e) {
            log.warn("mcp sdk tool call failed, fallback to raw http serverId={} tool={}", mcpServerId, toolName, e);
            try {
                String text = mcpRawHttpClient.callTool(server.getEndpoint(), toolName, arguments);
                log.info("mcp client fallback call end serverId={} tool={} elapsedMs={} resultLength={}",
                        mcpServerId, toolName, System.currentTimeMillis() - start, text == null ? 0 : text.length());
                hifyMetrics.recordMcpToolCall(mcpServerId, toolName, "success");
                return text;
            } catch (Throwable fallbackError) {
                log.warn("mcp raw http tool call failed serverId={} tool={}", mcpServerId, toolName, fallbackError);
                hifyMetrics.recordMcpToolCall(mcpServerId, toolName, "failure");
                throw new BizException(ErrorCode.MCP_TOOL_CALL_FAILED, fallbackError.getMessage());
            }
        }
    }

    @Override
    public List<String> listTools(Long mcpServerId) {
        McpServerPo server = findEnabledServer(mcpServerId);
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
