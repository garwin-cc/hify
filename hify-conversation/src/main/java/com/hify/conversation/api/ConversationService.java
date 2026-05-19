package com.hify.conversation.api;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/** 对话引擎服务，跨模块调用的统一入口。 */
public interface ConversationService {

    List<ConversationSessionResp> listSessions(Long agentId);

    CursorPageResp<ConversationSessionResp> listSessionsCursor(ConversationSessionCursorQuery query);

    List<ConversationMessageResp> listMessages(Long sessionId);

    CursorPageResp<ConversationMessageResp> listMessagesCursor(Long sessionId, ConversationMessageCursorQuery query);

    CursorPageResp<ConversationLogResp> listConversationLogs(ConversationLogQuery query);

    ConversationTraceDetailResp getMessageTrace(Long messageId);

    ConversationTraceDetailResp getTraceDetail(String traceId);

    ConversationSummaryResp getSessionSummary(Long sessionId);

    void clearSessionSummary(Long sessionId);

    void deleteSession(Long sessionId);

    MessageFeedbackResp upsertFeedback(Long messageId, MessageFeedbackReq req);

    /**
     * 发送消息，返回 SSE 流。
     *
     * <p>若 {@code sessionId} 为 null，则自动创建新会话。
     * 调用方（Controller）返回此 SseEmitter 即可，Spring 负责保持 HTTP 连接。
     *
     * <p><b>SSE 事件格式（data 字段均为 JSON）：</b>
     * <ul>
     *   <li>{@code {"type":"delta","content":"Hello"}} — 每个文本增量</li>
     *   <li>{@code {"type":"done","sessionId":1,"messageId":2,"finishReason":"stop","inputTokens":10,"outputTokens":50}} — 完成</li>
     *   <li>{@code {"type":"error","code":1003,"message":"LLM 响应超时"}} — 错误</li>
     * </ul>
     */
    SseEmitter sendMessage(Long agentId, Long sessionId, String content);

    SseEmitter sendMessage(Long agentId, Long sessionId, String content, Long userId, Long appId, Long apiKeyId);

    SseEmitter sendMessageByApiKey(String endpointPath, String apiKey, Long sessionId, String content);

    SseEmitter sendMessageToSession(Long sessionId, String content);
}
