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
}
