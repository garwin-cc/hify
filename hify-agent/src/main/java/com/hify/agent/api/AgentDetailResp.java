package com.hify.agent.api;

import com.hify.model.api.ModelConfigResp;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AgentDetailResp {

    private Long id;
    private Long workspaceId;
    private Long projectId;
    private String name;
    private String description;
    private String systemPrompt;
    private Long modelConfigId;
    private ModelConfigResp modelConfig;
    private Long workflowId;
    private List<Long> knowledgeBaseIds;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer maxContextTurns;
    private Integer memoryEnabled;
    private Integer summaryTriggerMessageCount;
    private Integer summaryMaxTokens;
    private Long summaryModelConfigId;
    private List<Long> toolIds;
    private Integer draftVersionNo;
    private Long publishedVersionId;
    private String publishStatus;
    private Integer maxToolRounds;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
