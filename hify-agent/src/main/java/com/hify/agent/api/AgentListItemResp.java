package com.hify.agent.api;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentListItemResp {

    private Long id;
    private Long workspaceId;
    private Long projectId;
    private String name;
    private String description;
    private Long modelConfigId;
    private String modelName;
    private String modelId;
    private Long workflowId;
    private List<Long> knowledgeBaseIds;
    private BigDecimal temperature;
    private Integer memoryEnabled;
    private Integer toolCount;
    private Long publishedVersionId;
    private String publishStatus;
    private Integer maxToolRounds;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
