package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class KnowledgeChunkResp {

    private Long id;

    private Long knowledgeBaseId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
}
