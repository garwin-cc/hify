package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class KnowledgeSearchResp {

    private Long id;

    private Long knowledgeBaseId;

    private String documentId;

    private Integer chunkIndex;

    private String content;

    private Double score;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
}
