package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class McpToolCallAuditResp {

    private Long id;
    private String traceId;
    private Long projectId;
    private Long agentId;
    private Long userId;
    private Long workflowId;
    private Long workflowRunId;
    private Long mcpServerId;
    private String toolName;
    private String status;
    private List<String> argumentKeys;
    private String argumentSummary;
    private String resultSummary;
    private String errorCategory;
    private Long elapsedMs;
    private Boolean success;
    private String errorSummary;
    private LocalDateTime createdAt;
}
