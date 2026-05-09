package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class McpToolResp {

    private Long id;

    private Long mcpServerId;

    private String name;

    private String description;

    private Map<String, Object> inputSchema;

    private LocalDateTime createdAt;
}
