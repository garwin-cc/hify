package com.hify.knowledge.domain;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeChunk {

    private Long id;

    private Long knowledgeBaseId;

    private String documentId;

    private Integer chunkIndex;

    private String content;

    private Integer tokenCount;

    private List<Double> embedding;

    private String metadataJson;

    private Long indexVersion;

    private Boolean active;

    private LocalDateTime createdAt;
}
