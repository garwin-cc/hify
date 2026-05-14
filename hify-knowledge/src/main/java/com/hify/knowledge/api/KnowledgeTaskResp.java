package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeTaskResp {

    private Long id;

    private Long knowledgeBaseId;

    private Long documentId;

    private String taskType;

    private String targetType;

    private Long targetId;

    private String reason;

    private String status;

    private String processStage;

    private Integer processProgress;

    private String progressMessage;

    private Integer attempt;

    private Integer maxAttempt;

    private Integer lastProcessedChunkIndex;

    private Integer cancelRequested;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
