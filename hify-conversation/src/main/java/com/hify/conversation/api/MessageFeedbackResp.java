package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageFeedbackResp {

    private Long id;
    private Long messageId;
    private Long sessionId;
    private Long agentId;
    private Long projectId;
    private String traceId;
    private Long userId;
    private String rating;
    private String issueType;
    private String comment;
    private String correctedAnswer;
    private String status;
    private String reviewStatus;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
