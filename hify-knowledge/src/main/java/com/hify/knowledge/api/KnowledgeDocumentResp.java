package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeDocumentResp {

    private Long id;

    private Long knowledgeBaseId;

    private String name;

    private String fileType;

    private Long fileSize;

    private String department;

    private String documentType;

    private List<String> tags;

    private String permissionScope;

    private String status;

    private String processStage;

    private Integer processProgress;

    private Integer processedChunkCount;

    private Integer chunkCount;

    private String errorMessage;

    private String errorCode;

    private String failedStage;

    private Integer retryable;

    private Integer cancelRequested;

    private Integer retryCount;

    private Long processingTaskId;

    private String taskStatus;

    private String progressMessage;

    private Long parseLatencyMs;

    private Long chunkLatencyMs;

    private Long embeddingLatencyMs;

    private Long vectorSaveLatencyMs;

    private Integer lastProcessedChunkIndex;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
