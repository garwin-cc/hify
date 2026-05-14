package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class McpToolResp {

    private Long id;

    private Long mcpServerId;

    private Long workspaceId;

    private Long projectId;

    private String name;

    private String description;

    private Map<String, Object> inputSchema;

    private Integer dangerous;

    private String permissionLevel;

    private Integer schemaValidationEnabled;

    private LocalDateTime createdAt;
}
