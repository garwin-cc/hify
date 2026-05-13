package com.hify.conversation.infra;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_conversation_llm_trace")
@EqualsAndHashCode(callSuper = false)
public class ConversationLlmTracePo extends BaseEntity {

    private String traceId;
    private Long providerId;
    private String providerName;
    private String providerType;
    private Long modelConfigId;
    private String modelId;
    private Integer streaming;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer firstTokenLatencyMs;
    private Integer totalLatencyMs;
    private String status;
    private String errorCode;
    private String errorMessage;
}
