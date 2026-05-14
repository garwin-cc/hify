package com.hify.agent.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentVersionResp {

    private Long id;
    private Long agentId;
    private Integer versionNo;
    private String status;
    private String name;
    private Long modelConfigId;
    private Long workflowId;
    private List<Long> knowledgeBaseIds;
    private List<Long> toolIds;
    private Integer maxToolRounds;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
}
