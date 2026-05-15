package com.hify.conversation.infra;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_conversation_trace")
@EqualsAndHashCode(callSuper = false)
public class ConversationTracePo extends BaseEntity {

    private String traceId;
    private Long sessionId;
    private Long userId;
    private Long projectId;
    private Long appId;
    private Long apiKeyId;
    private Long userMessageId;
    private Long assistantMessageId;
    private Long agentId;
    private String agentName;
    private Long agentVersionId;
    private Integer agentVersionNo;
    private String agentSystemPrompt;
    private Integer maxToolRounds;
    private Long modelConfigId;
    private Long providerId;
    private String providerName;
    private String providerType;
    private String modelId;
    private Long workflowId;
    private Long workflowRunId;
    private Integer ragTriggered;
    private Integer mcpTriggered;
    private Integer memoryEnabled;
    private Integer summaryUsed;
    private Integer summaryVersion;
    private Integer summaryLatencyMs;
    private String summaryErrorMessage;
    private String status;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime firstTokenAt;
    private LocalDateTime finishedAt;
}
