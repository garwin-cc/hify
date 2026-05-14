package com.hify.conversation.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConversationMessageCursorQuery {

    private Long cursorId;
    private LocalDateTime cursorTime;
    private Integer limit = 50;
}
