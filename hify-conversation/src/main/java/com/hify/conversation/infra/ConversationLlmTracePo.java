package com.hify.conversation.infra;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@TableName(value = "t_conversation_llm_trace", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class ConversationLlmTracePo extends BaseEntity {

    private String traceId;
    private Long providerId;
    private String providerName;
    private String providerType;
    private Long modelConfigId;
    private String modelId;
    private Integer streaming;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> requestSummary;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer firstTokenLatencyMs;
    private Integer totalLatencyMs;
    private String status;
    private String errorCode;
    private String errorMessage;
}
