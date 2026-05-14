package com.hify.conversation.infra;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_message_feedback")
@EqualsAndHashCode(callSuper = false)
public class MessageFeedbackPo extends BaseEntity {

    private Long messageId;
    private Long sessionId;
    private Long agentId;
    private Long userId;
    private String rating;
    private String issueType;
    private String comment;
    private String correctedAnswer;
    private String status;
}
