package com.hify.conversation.infra;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_chat_session_summary")
@EqualsAndHashCode(callSuper = false)
public class ChatSessionSummaryPo extends BaseEntity {

    private Long sessionId;
    private Long agentId;
    private String summary;
    private Integer version;
    private Long sourceMessageStartId;
    private Long sourceMessageEndId;
    private Integer sourceMessageCount;
    private String status;
    private String errorMessage;
    private LocalDateTime summarizedAt;
}
