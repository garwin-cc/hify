package com.hify.mcp.api;

import lombok.Data;

import java.util.Map;

@Data
public class OpenApiToolResp {

    private Long id;
    private Long sourceId;
    private String name;
    private String description;
    private String httpMethod;
    private String path;
    private Map<String, Object> inputSchema;
    private Integer enabled;
}
