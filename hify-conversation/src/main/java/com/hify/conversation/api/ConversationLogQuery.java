package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationLogQuery {

    private Long userId;
    private Long projectId;
    private Long agentId;
    private Long appId;
    private Long apiKeyId;
    private Long modelConfigId;
    private String traceId;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long cursorId;
    private LocalDateTime cursorTime;
    private Integer limit = 20;
}
