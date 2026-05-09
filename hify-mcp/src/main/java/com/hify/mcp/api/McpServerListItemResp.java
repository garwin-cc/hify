package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class McpServerListItemResp {

    private Long id;

    private String name;

    private String description;

    private String endpoint;

    private Integer enabled;

    private Integer toolCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
