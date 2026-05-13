package com.hify.mcp.api;

import lombok.Data;

import java.util.List;

@Data
public class McpToolCallAuditResp {

    private String traceId;
    private String toolName;
    private List<String> argumentKeys;
    private Long elapsedMs;
    private Boolean success;
    private String errorSummary;
}
