package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationSessionCursorQuery {

    private Long agentId;
    private Long userId;
    private Long appId;
    private Long cursorId;
    private LocalDateTime cursorTime;
    private Integer limit = 20;
}
