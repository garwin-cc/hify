package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationSessionResp {

    private Long id;

    private Long agentId;

    private String title;

    private Integer messageCount;

    private LocalDateTime createdAt;

    private LocalDateTime lastMessageAt;
}
