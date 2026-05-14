package com.hify.mcp.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMcpServerReq {

    @NotBlank(message = "MCP Server 名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "MCP Server endpoint 不能为空")
    private String endpoint;

    private Integer enabled;

    private Long workspaceId;

    private Long projectId;

    private String visibility;

    private String shareScope;

    private Long secretId;

    private Integer connectTimeoutMs;

    private Integer readTimeoutMs;

    private Integer retryTimes;

    private Integer retryIntervalMs;

    private String fallbackStrategy;
}
