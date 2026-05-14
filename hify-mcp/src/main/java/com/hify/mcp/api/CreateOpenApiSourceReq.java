package com.hify.mcp.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class CreateOpenApiSourceReq {

    private Long workspaceId;
    private Long projectId;

    @NotBlank(message = "OpenAPI 名称不能为空")
    private String name;

    private String description;

    @NotBlank(message = "OpenAPI baseUrl 不能为空")
    private String baseUrl;

    private Map<String, Object> specJson;

    private Long secretId;

    private Integer enabled;
}
