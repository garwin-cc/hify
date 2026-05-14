package com.hify.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_knowledge_base")
@EqualsAndHashCode(callSuper = false)
public class KnowledgeBasePo extends BaseEntity {

    private Long workspaceId;

    private Long projectId;

    private String name;

    private String description;

    private Long embeddingModelConfigId;

    private Integer enabled;

    private Integer documentCount;

    private Integer chunkCount;

    private String retrievalMode;

    private Integer topK;

    private Integer candidateTopK;

    private Double scoreThreshold;

    private Integer chunkSize;

    private Integer chunkOverlap;

    private Integer maxContextTokens;

    private Integer rerankEnabled;

    private Long rerankModelConfigId;

    private Integer rerankTopN;
}
