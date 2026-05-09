package com.hify.agent.api;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class CreateAgentReq {

    @NotBlank(message = "Agent 名称不能为空")
    @Size(max = 100, message = "名称不超过 100 个字符")
    private String name;

    @Size(max = 500, message = "描述不超过 500 个字符")
    private String description = "";

    @NotBlank(message = "系统提示词不能为空")
    private String systemPrompt;

    @NotNull(message = "请选择模型配置")
    private Long modelConfigId;

    /** 绑定工作流 ID，留空表示不绑定 */
    private Long workflowId;

    /** 关联知识库 ID，留空表示不绑定 */
    private List<Long> knowledgeBaseIds;

    @DecimalMin(value = "0.00", message = "temperature 最小为 0.00")
    @DecimalMax(value = "2.00", message = "temperature 最大为 2.00")
    private BigDecimal temperature;

    @Min(value = 1, message = "maxTokens 最小为 1")
    private Integer maxTokens;

    @Min(value = 1, message = "maxContextTurns 最小为 1")
    @Max(value = 200, message = "maxContextTurns 不超过 200")
    private Integer maxContextTurns;

    /** 绑定的 MCP 工具 ID 列表，顺序即为 sort_order */
    private List<Long> toolIds;
}
