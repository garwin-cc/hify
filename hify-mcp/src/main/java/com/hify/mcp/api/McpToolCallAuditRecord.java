package com.hify.mcp.api;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class McpToolCallAuditRecord {

    private String sourceType;
    private Long conversationSessionId;
    private Long conversationMessageId;
    private Long workflowRunId;
    private String workflowNodeKey;
    private Long mcpServerId;
    private String toolName;
    private Map<String, Object> arguments;
    private Long elapsedMs;
    private boolean success;
    private String result;
    private String error;
}
