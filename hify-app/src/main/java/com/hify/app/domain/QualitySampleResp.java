package com.hify.app.domain;

import lombok.Data;

@Data
public class QualitySampleResp {

    private Long id;
    private Long messageId;
    private Long sessionId;
    private Long agentId;
    private String agentName;
    private Long projectId;
    private String traceId;
    private Long userId;
    private String rating;
    private String issueType;
    private String comment;
    private String correctedAnswer;
    private String reviewStatus;
    private String resolutionNote;
    private String userQuestion;
    private String assistantAnswer;
    private Boolean ragTriggered;
    private Boolean ragHit;
    private Boolean mcpTriggered;
    private String modelId;
    private String createdAt;
    private String updatedAt;
}
