package com.hify.conversation.infra;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@TableName("t_conversation_rag_trace")
@EqualsAndHashCode(callSuper = false)
public class ConversationRagTracePo extends BaseEntity {

    private String traceId;
    private Long knowledgeBaseId;
    private String knowledgeBaseName;
    private String documentId;
    private String documentName;
    private Long chunkId;
    private Integer chunkIndex;
    private BigDecimal score;
    private String contentPreview;
}
