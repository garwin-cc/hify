package com.hify.mcp.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ToolSecretResp {

    private Long id;
    private Long workspaceId;
    private Long projectId;
    private String name;
    private String secretType;
    private String keyPrefix;
    private String status;
    private LocalDateTime createdAt;
}
