package com.hify.knowledge.api;

import lombok.Data;

@Data
public class KnowledgeRebuildReq {

    private String reason;

    private Long embeddingModelConfigId;

    private Integer chunkSize;

    private Integer chunkOverlap;
}
