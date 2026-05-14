package com.hify.agent.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentAppResp {

    private Long id;
    private Long agentId;
    private Long publishedVersionId;
    private String name;
    private String description;
    private Integer webEnabled;
    private Integer apiEnabled;
    private String endpointPath;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
