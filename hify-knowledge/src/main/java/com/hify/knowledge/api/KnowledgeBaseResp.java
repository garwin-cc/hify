package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseResp {

    private Long id;

    private Long workspaceId;

    private Long projectId;

    private String visibility;

    private String shareScope;

    private String name;

    private String description;

    private Long embeddingModelConfigId;

    private Integer enabled;

    private Integer documentCount;

    private Integer chunkCount;

    private String retrievalMode;

    private Integer topK;

    private Integer candidateTopK;

    private Double scoreThreshold;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer maxContextTokens;

    private Integer rerankEnabled;

    private Long rerankModelConfigId;

    private Integer rerankTopN;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
