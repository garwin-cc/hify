package com.hify.auth.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProjectMemberResp {

    private Long id;
    private Long workspaceId;
    private Long projectId;
    private Long userId;
    private String username;
    private String displayName;
    private ProjectRole role;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
