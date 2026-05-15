package com.hify.mcp.api;

import lombok.Data;

import java.util.Map;

@Data
public class McpToolCallRequest {

    private Long mcpServerId;
    private String toolName;
    private Map<String, Object> arguments;
    private Long workspaceId;
    private Long projectId;
    private Long agentId;
    private Long appId;
    private Long apiKeyId;
    private Long userId;
    private Long workflowId;
    private Long workflowRunId;
    private String workflowNodeKey;
    private String sourceType;
    private String sourceId;
    private Integer timeoutMs;
}
