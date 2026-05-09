package com.hify.conversation.infra;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_chat_session")
@EqualsAndHashCode(callSuper = false)
public class ChatSessionPo extends BaseEntity {

    private Long agentId;

    private Long userId;

    private String title;

    /** ACTIVE / ARCHIVED */
    private String status;

    private Integer messageCount;

    private LocalDateTime lastMessageAt;
}
