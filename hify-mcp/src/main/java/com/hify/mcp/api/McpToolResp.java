package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class McpToolResp {

    private Long id;

    private Long mcpServerId;

    private String toolType;

    private Long openapiToolId;

    private Long workspaceId;

    private Long projectId;

    private String name;

    private String description;

    private Map<String, Object> inputSchema;

    private Integer dangerous;

    private String permissionLevel;

    private Integer schemaValidationEnabled;

    private Integer timeoutMs;

    private Integer retryTimes;

    private String fallbackStrategy;

    private LocalDateTime createdAt;
}
