package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationLogResp {

    private String traceId;
    private Long sessionId;
    private Long userMessageId;
    private Long assistantMessageId;
    private Long userId;
    private Long appId;
    private Long apiKeyId;
    private Long agentId;
    private String agentName;
    private Long modelConfigId;
    private String modelId;
    private Long workflowId;
    private Long workflowRunId;
    private Boolean ragTriggered;
    private Boolean mcpTriggered;
    private Boolean summaryUsed;
    private Integer summaryLatencyMs;
    private String summaryErrorMessage;
    private String status;
    private String errorCode;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime firstTokenAt;
    private LocalDateTime finishedAt;
}
