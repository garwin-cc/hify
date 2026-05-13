package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationSummaryResp {

    private Long id;
    private Long sessionId;
    private Long agentId;
    private String summary;
    private Integer version;
    private Long sourceMessageStartId;
    private Long sourceMessageEndId;
    private Integer sourceMessageCount;
    private String status;
    private String errorMessage;
    private LocalDateTime summarizedAt;
    private LocalDateTime updatedAt;
}
