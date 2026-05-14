package com.hify.mcp.api;

import lombok.Data;

@Data
public class McpServerQuery {

    private int page = 1;

    private int pageSize = 20;

    private String name;

    private Integer enabled;

    private Long projectId;

    private String visibility;
}
