package com.hify.model.api;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LlmCallStatRecord {

    private String traceId;
    private LlmCallContext context;
    private Long providerId;
    private String providerType;
    private Long modelConfigId;
    private String modelId;
    private String callType;
    private boolean success;
    private boolean fallbackUsed;
    private int inputTokens;
    private int outputTokens;
    private int latencyMs;
    private String errorCode;
    private String errorMessage;
}
