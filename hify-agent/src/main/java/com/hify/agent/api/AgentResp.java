package com.hify.agent.api;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentResp {

    private Long id;
    private String name;
    private String description;
    private String systemPrompt;
    private Long modelConfigId;
    private Long workflowId;
    private List<Long> knowledgeBaseIds;
    private BigDecimal temperature;
    private Integer maxTurns;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
