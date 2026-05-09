package com.hify.conversation.infra;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@TableName(value = "t_chat_message", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class ChatMessagePo extends BaseEntity {

    private Long sessionId;

    /** user / assistant / tool */
    private String role;

    private String content;

    /** PENDING / STREAMING / DONE / ERROR */
    private String status;

    /** OpenAI tool_calls format; only populated when role=assistant and LLM issued tool calls */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> toolCalls;

    /** Populated when role=tool; references the tool_call.id from the preceding assistant message */
    private String toolCallId;

    private Integer tokens;

    /** stop / length / tool_calls / error */
    private String finishReason;

    /** Time-to-first-token in ms; only set for assistant messages */
    private Integer latencyMs;
}
