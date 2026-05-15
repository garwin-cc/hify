package com.hify.conversation.web;

import com.hify.conversation.api.ConversationService;
import com.hify.conversation.api.PublicSendMessageReq;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/public/agent-apps")
@RequiredArgsConstructor
public class PublicConversationController {

    private final ConversationService conversationService;

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader("X-Api-Key") String apiKey,
                             @Valid @RequestBody PublicSendMessageReq req) {
        return conversationService.sendMessageByApiKey(req.getEndpointPath(), apiKey,
                req.getSessionId(), req.getContent());
    }
}
