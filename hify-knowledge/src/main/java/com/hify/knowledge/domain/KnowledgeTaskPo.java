package com.hify.knowledge.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_knowledge_task")
@EqualsAndHashCode(callSuper = false)
public class KnowledgeTaskPo extends BaseEntity {

    private Long knowledgeBaseId;

    private Long documentId;

    private String taskType;

    private String targetType;

    private Long targetId;

    private String reason;

    private String status;

    private String processStage;

    private Integer processProgress;

    private String progressMessage;

    private Integer attempt;

    private Integer maxAttempt;

    private Integer lastProcessedChunkIndex;

    private Integer cancelRequested;

    private String errorCode;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
