package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KnowledgeBaseResp {

    private Long id;

    private String name;

    private String description;

    private Long embeddingModelConfigId;

    private Integer enabled;

    private Integer documentCount;

    private Integer chunkCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
