package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class KnowledgeSearchResp {

    private String traceId;

    private Integer rank;

    private Long id;

    private Long knowledgeBaseId;

    private String documentId;

    private String documentName;

    private Integer chunkIndex;

    private String content;

    private Double score;

    private Double finalScore;

    private Double vectorScore;

    private Double keywordScore;

    private Double fusionScore;

    private Double rerankScore;

    private String retrievalMode;

    private Map<String, Object> metadata;

    private LocalDateTime createdAt;
}
