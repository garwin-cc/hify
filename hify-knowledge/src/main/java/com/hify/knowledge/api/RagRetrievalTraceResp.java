package com.hify.knowledge.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class RagRetrievalTraceResp {

    private Long id;

    private String traceId;

    private String sourceType;

    private String sourceId;

    private Long agentId;

    private String queryText;

    private List<Long> knowledgeBaseIds;

    private String retrievalMode;

    private Integer topK;

    private Double scoreThreshold;

    private Integer hitCount;

    private List<Long> selectedChunkIds;

    private Long latencyMs;

    private String status;

    private String errorMessage;

    private Map<String, Object> detail;

    private LocalDateTime createdAt;
}
