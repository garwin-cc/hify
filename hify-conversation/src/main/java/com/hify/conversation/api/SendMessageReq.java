package com.hify.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageReq {

    @NotNull
    private Long agentId;

    /** null 时自动创建新会话 */
    private Long sessionId;

    @NotBlank
    @Size(max = 10000)
    private String content;
}
