package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class McpServerResp {
    private Long id;
    private Long workspaceId;
    private Long projectId;
    private String name;
    private String description;
    private String endpoint;
    private Integer enabled;
    private String visibility;
    private String shareScope;
    private Long secretId;
    private Integer connectTimeoutMs;
    private Integer readTimeoutMs;
    private Integer retryTimes;
    private Integer retryIntervalMs;
    private String fallbackStrategy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
