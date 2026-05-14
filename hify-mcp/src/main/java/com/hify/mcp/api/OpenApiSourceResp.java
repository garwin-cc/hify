package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OpenApiSourceResp {

    private Long id;
    private Long workspaceId;
    private Long projectId;
    private String name;
    private String description;
    private String baseUrl;
    private Long secretId;
    private Integer enabled;
    private String status;
    private LocalDateTime createdAt;
}
