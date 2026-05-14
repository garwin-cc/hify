package com.hify.mcp.api;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class McpToolCallAuditRecord {

    private String sourceType;
    private String traceId;
    private Long workspaceId;
    private Long projectId;
    private Long agentId;
    private Long appId;
    private Long apiKeyId;
    private Long userId;
    private Long conversationSessionId;
    private Long conversationMessageId;
    private Long workflowId;
    private Long workflowRunId;
    private String workflowNodeKey;
    private Long mcpServerId;
    private String toolName;
    private String status;
    private Integer retryCount;
    private Integer timeoutMs;
    private String sourceId;
    private Map<String, Object> arguments;
    private Long elapsedMs;
    private boolean success;
    private String result;
    private String error;
}
