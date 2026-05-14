package com.hify.conversation.api;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageFeedbackReq {

    private Long userId;

    private String rating;

    @Size(max = 64)
    private String issueType;

    @Size(max = 1000)
    private String comment;

    private String correctedAnswer;
}
