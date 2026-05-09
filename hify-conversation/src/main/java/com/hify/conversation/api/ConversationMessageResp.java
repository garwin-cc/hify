package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationMessageResp {

    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private String status;

    private LocalDateTime createdAt;
}
