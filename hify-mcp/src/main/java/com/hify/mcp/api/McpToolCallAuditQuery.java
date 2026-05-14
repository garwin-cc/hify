package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class McpToolCallAuditQuery {

    private Long projectId;
    private Long agentId;
    private Long appId;
    private Long userId;
    private Long workflowId;
    private Long workflowRunId;
    private Long mcpServerId;
    private String toolName;
    private String status;
    private String traceId;
    private Long minElapsedMs;
    private Long maxElapsedMs;
    private LocalDateTime createdAtStart;
    private LocalDateTime createdAtEnd;
    private int page = 1;
    private int size = 20;
}
