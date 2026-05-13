package com.hify.agent.api;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpdateAgentReq {

    @Size(max = 100, message = "名称不超过 100 个字符")
    private String name;

    @Size(max = 500, message = "描述不超过 500 个字符")
    private String description;

    private String systemPrompt;

    private Long modelConfigId;

    /**
     * null 表示不修改工作流绑定。
     * bindWorkflow=true 且 workflowId=null 表示解绑工作流。
     */
    private Long workflowId;

    private Boolean bindWorkflow;

    private List<Long> knowledgeBaseIds;

    @DecimalMin(value = "0.00", message = "temperature 最小为 0.00")
    @DecimalMax(value = "2.00", message = "temperature 最大为 2.00")
    private BigDecimal temperature;

    @Min(value = 1)
    private Integer maxTokens;

    @Min(value = 1, message = "maxContextTurns 最小为 1")
    @Max(value = 200, message = "maxContextTurns 不超过 200")
    private Integer maxContextTurns;

    private Integer memoryEnabled;

    @Min(value = 4, message = "summaryTriggerMessageCount 最小为 4")
    @Max(value = 500, message = "summaryTriggerMessageCount 不超过 500")
    private Integer summaryTriggerMessageCount;

    @Min(value = 100, message = "summaryMaxTokens 最小为 100")
    @Max(value = 4000, message = "summaryMaxTokens 不超过 4000")
    private Integer summaryMaxTokens;

    private Long summaryModelConfigId;

    /** null 表示不修改工具绑定；空列表表示清空所有工具 */
    private List<Long> toolIds;
}
