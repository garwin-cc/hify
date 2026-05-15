package com.hify.auth.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectResp {

    private Long id;
    private Long workspaceId;
    private String name;
    private String code;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
