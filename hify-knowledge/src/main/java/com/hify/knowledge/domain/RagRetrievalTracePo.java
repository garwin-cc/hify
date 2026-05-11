package com.hify.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_rag_retrieval_trace")
@EqualsAndHashCode(callSuper = false)
public class RagRetrievalTracePo extends BaseEntity {

    private String traceId;

    private String sourceType;

    private String sourceId;

    private Long agentId;

    private String queryText;

    private String knowledgeBaseIdsJson;

    private String retrievalMode;

    private Integer topK;

    private Double scoreThreshold;

    private Integer rerankEnabled;

    private String selectedChunkIdsJson;

    private Integer hitCount;

    private Long latencyMs;

    private String status;

    private String errorMessage;
}
