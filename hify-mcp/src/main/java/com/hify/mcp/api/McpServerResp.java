package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class McpServerResp {
    private Long id;
    private String name;
    private String description;
    private String endpoint;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
