package com.hify.model.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_llm_call_stat")
@EqualsAndHashCode(callSuper = false)
public class LlmCallStatPo extends BaseEntity {

    private String traceId;
    private Long userId;
    private Long projectId;
    private Long appId;
    private Long agentId;
    private Long providerId;
    private String providerType;
    private Long modelConfigId;
    private String modelId;
    private String callType;
    private Integer success;
    private Integer fallbackUsed;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer latencyMs;
    private String errorCode;
    private String errorMessage;
}
