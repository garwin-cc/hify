package com.hify.agent.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AgentApiKeyResp {

    private Long id;
    private Long agentAppId;
    private String name;
    private String keyPrefix;
    private String status;
    private LocalDateTime lastUsedAt;
    private LocalDateTime createdAt;
}
