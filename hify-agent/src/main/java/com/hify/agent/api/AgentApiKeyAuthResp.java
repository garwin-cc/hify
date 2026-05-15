package com.hify.agent.api;

import lombok.Data;

@Data
public class AgentApiKeyAuthResp {

    private Long agentId;
    private Long appId;
    private Long apiKeyId;
    private Long projectId;
    private Long agentVersionId;
    private Integer agentVersionNo;
    private String endpointPath;
    private AgentDetailResp agent;
}
