package com.hify.conversation.web;

import com.hify.conversation.api.ConversationService;
import com.hify.conversation.api.SendMessageReq;
import com.hify.common.web.Result;
import com.hify.conversation.api.ConversationMessageResp;
import com.hify.conversation.api.ConversationSessionResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public Result<List<ConversationSessionResp>> listSessions(@RequestParam Long agentId) {
        return Result.ok(conversationService.listSessions(agentId));
    }

    @GetMapping("/{sessionId}/messages")
    public Result<List<ConversationMessageResp>> listMessages(@PathVariable Long sessionId) {
        return Result.ok(conversationService.listMessages(sessionId));
    }

    @DeleteMapping("/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        conversationService.deleteSession(sessionId);
        return Result.ok();
    }

    /**
     * 发送消息，响应为 SSE 流（text/event-stream）。
     * sessionId 为空时自动创建新会话。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody SendMessageReq req) {
        return conversationService.sendMessage(req.getAgentId(), req.getSessionId(), req.getContent());
    }
}
