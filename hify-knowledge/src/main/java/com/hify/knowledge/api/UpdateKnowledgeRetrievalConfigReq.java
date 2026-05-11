package com.hify.knowledge.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateKnowledgeRetrievalConfigReq {

    private String retrievalMode;

    @Min(value = 1, message = "topK 最小为 1")
    @Max(value = 50, message = "topK 最大为 50")
    private Integer topK;

    @Min(value = 1, message = "candidateTopK 最小为 1")
    @Max(value = 100, message = "candidateTopK 最大为 100")
    private Integer candidateTopK;

    @Min(value = 0, message = "scoreThreshold 最小为 0")
    @Max(value = 1, message = "scoreThreshold 最大为 1")
    private Double scoreThreshold;

    @Min(value = 128, message = "chunkSize 最小为 128")
    @Max(value = 4000, message = "chunkSize 最大为 4000")
    private Integer chunkSize;

    @Min(value = 0, message = "chunkOverlap 最小为 0")
    @Max(value = 1000, message = "chunkOverlap 最大为 1000")
    private Integer chunkOverlap;

    @Min(value = 512, message = "maxContextTokens 最小为 512")
    @Max(value = 16000, message = "maxContextTokens 最大为 16000")
    private Integer maxContextTokens;

    private Integer rerankEnabled;

    private Long rerankModelConfigId;

    @Min(value = 1, message = "rerankTopN 最小为 1")
    @Max(value = 100, message = "rerankTopN 最大为 100")
    private Integer rerankTopN;
}
