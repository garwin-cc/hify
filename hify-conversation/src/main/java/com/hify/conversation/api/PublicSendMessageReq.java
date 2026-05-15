package com.hify.conversation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublicSendMessageReq {

    /** Agent App 发布路径，如 /app/support。 */
    @NotBlank
    private String endpointPath;

    /** null 时自动创建新会话 */
    private Long sessionId;

    @NotBlank
    @Size(max = 10000)
    private String content;
}
