package com.hify.agent.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "t_agent_version", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class AgentVersionPo extends BaseEntity {

    private Long agentId;
    private Integer versionNo;
    private String status;
    private String name;
    private String description;
    private String systemPrompt;
    private Long modelConfigId;
    private Long workflowId;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> knowledgeBaseIdsJson;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> toolIdsJson;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer maxContextTurns;
    private Integer memoryEnabled;
    private Integer summaryTriggerMessageCount;
    private Integer summaryMaxTokens;
    private Long summaryModelConfigId;
    private Integer maxToolRounds;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> snapshotJson;
    private LocalDateTime publishedAt;
}
