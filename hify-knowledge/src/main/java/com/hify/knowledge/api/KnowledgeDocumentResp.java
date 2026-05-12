package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeDocumentResp {

    private Long id;

    private Long knowledgeBaseId;

    private String name;

    private String fileType;

    private Long fileSize;

    private String status;

    private String processStage;

    private Integer processProgress;

    private Integer processedChunkCount;

    private Integer chunkCount;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
