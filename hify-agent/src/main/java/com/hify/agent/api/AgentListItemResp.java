package com.hify.agent.api;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentListItemResp {

    private Long id;
    private String name;
    private String description;
    private Long modelConfigId;
    private String modelName;
    private String modelId;
    private Long workflowId;
    private List<Long> knowledgeBaseIds;
    private BigDecimal temperature;
    private Integer toolCount;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
