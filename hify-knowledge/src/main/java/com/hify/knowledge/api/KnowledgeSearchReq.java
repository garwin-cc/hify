package com.hify.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class KnowledgeSearchReq {

    private List<Long> knowledgeBaseIds;

    private List<Double> queryEmbedding;

    private String queryText;

    @Min(value = 1, message = "topK 最小为 1")
    @Max(value = 50, message = "topK 最大为 50")
    private Integer topK;

    @Min(value = 1, message = "candidateTopK 最小为 1")
    @Max(value = 100, message = "candidateTopK 最大为 100")
    private Integer candidateTopK;

    @Min(value = 0, message = "scoreThreshold 最小为 0")
    @Max(value = 1, message = "scoreThreshold 最大为 1")
    private Double scoreThreshold;

    private String retrievalMode;

    private String sourceType;

    private String sourceId;

    private Boolean includeTrace;

    private String department;

    private String documentType;

    private List<String> tags;

    private LocalDateTime createdAtStart;

    private LocalDateTime createdAtEnd;

    private Long projectId;

    private String permissionScope;
}
