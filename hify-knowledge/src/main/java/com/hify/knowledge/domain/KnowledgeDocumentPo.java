package com.hify.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_knowledge_document")
@EqualsAndHashCode(callSuper = false)
public class KnowledgeDocumentPo extends BaseEntity {

    private Long knowledgeBaseId;

    private String name;

    private String fileKey;

    private String fileType;

    private Long fileSize;

    private String parseStatus;

    private String processStage;

    private Integer processProgress;

    private Integer processedChunkCount;

    private Integer chunkCount;

    private String errorMessage;
}
