package com.hify.conversation.web;

import com.hify.auth.api.PermissionAction;
import com.hify.auth.api.RequireProjectPermission;
import com.hify.conversation.api.ConversationService;
import com.hify.conversation.api.SendMessageReq;
import com.hify.common.web.Result;
import com.hify.conversation.api.ConversationMessageResp;
import com.hify.conversation.api.ConversationMessageCursorQuery;
import com.hify.conversation.api.ConversationLogQuery;
import com.hify.conversation.api.ConversationLogResp;
import com.hify.conversation.api.ConversationSessionResp;
import com.hify.conversation.api.ConversationSessionCursorQuery;
import com.hify.conversation.api.ConversationSummaryResp;
import com.hify.conversation.api.ConversationTraceDetailResp;
import com.hify.conversation.api.CursorPageResp;
import com.hify.conversation.api.MessageFeedbackReq;
import com.hify.conversation.api.MessageFeedbackResp;
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

    @GetMapping("/cursor")
    public Result<CursorPageResp<ConversationSessionResp>> listSessionsCursor(ConversationSessionCursorQuery query) {
        return Result.ok(conversationService.listSessionsCursor(query));
    }

    @GetMapping("/{sessionId}/messages")
    public Result<List<ConversationMessageResp>> listMessages(@PathVariable Long sessionId) {
        return Result.ok(conversationService.listMessages(sessionId));
    }

    @GetMapping("/{sessionId}/messages/cursor")
    public Result<CursorPageResp<ConversationMessageResp>> listMessagesCursor(@PathVariable Long sessionId,
                                                                              ConversationMessageCursorQuery query) {
        return Result.ok(conversationService.listMessagesCursor(sessionId, query));
    }

    @GetMapping("/logs")
    @RequireProjectPermission(action = PermissionAction.READ)
    public Result<CursorPageResp<ConversationLogResp>> listConversationLogs(ConversationLogQuery query) {
        return Result.ok(conversationService.listConversationLogs(query));
    }

    @GetMapping("/messages/{messageId}/trace")
    public Result<ConversationTraceDetailResp> getMessageTrace(@PathVariable Long messageId) {
        return Result.ok(conversationService.getMessageTrace(messageId));
    }

    @GetMapping("/{sessionId}/summary")
    public Result<ConversationSummaryResp> getSessionSummary(@PathVariable Long sessionId) {
        return Result.ok(conversationService.getSessionSummary(sessionId));
    }

    @DeleteMapping("/{sessionId}/summary")
    public Result<Void> clearSessionSummary(@PathVariable Long sessionId) {
        conversationService.clearSessionSummary(sessionId);
        return Result.ok();
    }

    @DeleteMapping("/{sessionId}")
    public Result<Void> deleteSession(@PathVariable Long sessionId) {
        conversationService.deleteSession(sessionId);
        return Result.ok();
    }

    @PostMapping("/messages/{messageId}/feedback")
    public Result<MessageFeedbackResp> upsertFeedback(@PathVariable Long messageId,
                                                      @Valid @RequestBody MessageFeedbackReq req) {
        return Result.ok(conversationService.upsertFeedback(messageId, req));
    }

    /**
     * 发送消息，响应为 SSE 流（text/event-stream）。
     * sessionId 为空时自动创建新会话。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@Valid @RequestBody SendMessageReq req) {
        return conversationService.sendMessage(req.getAgentId(), req.getSessionId(), req.getContent(),
                req.getUserId(), req.getAppId(), req.getApiKeyId());
    }
}
