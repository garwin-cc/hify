package com.hify.conversation.domain;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.agent.api.AgentDetailResp;
import com.hify.agent.api.AgentService;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmApiException;
import com.hify.common.log.TraceContext;
import com.hify.common.metrics.HifyMetrics;
import com.hify.conversation.api.ConversationMessageResp;
import com.hify.conversation.api.ConversationService;
import com.hify.conversation.api.ConversationSessionResp;
import com.hify.conversation.api.ConversationSummaryResp;
import com.hify.conversation.api.ConversationTraceDetailResp;
import com.hify.conversation.infra.ChatMessageMapper;
import com.hify.conversation.infra.ChatMessagePo;
import com.hify.conversation.infra.ChatSessionMapper;
import com.hify.conversation.infra.ChatSessionPo;
import com.hify.conversation.infra.ChatSessionSummaryMapper;
import com.hify.conversation.infra.ChatSessionSummaryPo;
import com.hify.conversation.infra.ConversationLlmTraceMapper;
import com.hify.conversation.infra.ConversationLlmTracePo;
import com.hify.conversation.infra.ConversationRagTraceMapper;
import com.hify.conversation.infra.ConversationRagTracePo;
import com.hify.conversation.infra.ConversationTraceMapper;
import com.hify.conversation.infra.ConversationTracePo;
import com.hify.model.api.ChatMessage;
import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.api.ChatStreamCallback;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.LlmCallService;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import com.hify.model.api.ProviderDetailResp;
import com.hify.model.api.ProviderResp;
import com.hify.model.api.ProviderService;
import com.hify.model.api.ToolCall;
import com.hify.mcp.api.McpClientService;
import com.hify.mcp.api.McpService;
import com.hify.mcp.api.McpToolCallAuditRecord;
import com.hify.mcp.api.McpToolCallAuditResp;
import com.hify.mcp.api.McpToolCallAuditService;
import com.hify.mcp.api.McpToolResp;
import com.hify.knowledge.api.KnowledgeSearchReq;
import com.hify.knowledge.api.KnowledgeSearchResp;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int MAX_TOOL_ROUNDS = 3;
    private static final Pattern DSML_INVOKE_PATTERN =
            Pattern.compile("(?s)<[^>]*invoke\\s+name=\"([^\"]+)\"[^>]*>(.*?)</[^>]*invoke>");
    private static final Pattern DSML_PARAMETER_PATTERN =
            Pattern.compile("(?s)<[^>]*parameter\\s+name=\"([^\"]+)\"[^>]*>(.*?)</[^>]*parameter>");
    private static final Pattern DEEPSEEK_TOOL_CALL_PATTERN =
            Pattern.compile("(?s)<｜tool▁call▁begin｜>function<｜tool▁sep｜>([^\\n]+)\\s*```json\\s*(.*?)\\s*```\\s*<｜tool▁call▁end｜>");

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatSessionSummaryMapper summaryMapper;
    private final ConversationTraceMapper conversationTraceMapper;
    private final ConversationRagTraceMapper conversationRagTraceMapper;
    private final ConversationLlmTraceMapper conversationLlmTraceMapper;
    private final AgentService      agentService;
    private final LlmCallService    llmCallService;
    private final KnowledgeService  knowledgeService;
    private final EmbeddingService  embeddingService;
    private final ModelConfigService modelConfigService;
    private final ProviderService providerService;
    private final WorkflowService   workflowService;
    private final McpService        mcpService;
    private final McpClientService  mcpClientService;
    private final McpToolCallAuditService mcpToolCallAuditService;
    private final ObjectMapper      objectMapper;
    private final HifyMetrics       hifyMetrics;

    @Qualifier("llmExecutor")
    private final ThreadPoolExecutor llmExecutor;

    // SSE 超时略大于 OkHttp readTimeout（120s），确保 LLM 超时先于 emitter 超时触发
    private static final long EMITTER_TIMEOUT_MS = 130_000L;

    @Override
    public List<ConversationSessionResp> listSessions(Long agentId) {
        List<ChatSessionPo> sessions = sessionMapper.selectList(
                Wrappers.lambdaQuery(ChatSessionPo.class)
                        .eq(ChatSessionPo::getAgentId, agentId)
                        .eq(ChatSessionPo::getStatus, "ACTIVE")
                        .orderByDesc(ChatSessionPo::getLastMessageAt)
                        .orderByDesc(ChatSessionPo::getCreatedAt));
        return sessions.stream().map(this::toSessionResp).collect(Collectors.toList());
    }

    @Override
    public List<ConversationMessageResp> listMessages(Long sessionId) {
        ChatSessionPo session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        return messageMapper.selectList(
                        Wrappers.lambdaQuery(ChatMessagePo.class)
                                .eq(ChatMessagePo::getSessionId, sessionId)
                                .in(ChatMessagePo::getStatus, List.of("DONE", "ERROR"))
                                .orderByAsc(ChatMessagePo::getId))
                .stream()
                .map(this::toMessageResp)
                .collect(Collectors.toList());
    }

    @Override
    public ConversationTraceDetailResp getMessageTrace(Long messageId) {
        ChatMessagePo message = messageMapper.selectById(messageId);
        if (message == null || message.getTraceId() == null || message.getTraceId().isBlank()) {
            throw new BizException(ErrorCode.NOT_FOUND, "对话运行记录不存在: " + messageId);
        }
        ConversationTracePo trace = conversationTraceMapper.selectOne(
                Wrappers.lambdaQuery(ConversationTracePo.class)
                        .eq(ConversationTracePo::getTraceId, message.getTraceId()));
        if (trace == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "对话运行记录不存在: " + message.getTraceId());
        }
        return buildTraceDetail(trace);
    }

    @Override
    public ConversationSummaryResp getSessionSummary(Long sessionId) {
        ChatSessionPo session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        ChatSessionSummaryPo summary = latestSummary(sessionId);
        if (summary == null) {
            return null;
        }
        return toSummaryResp(summary);
    }

    @Override
    public void clearSessionSummary(Long sessionId) {
        ChatSessionPo session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        summaryMapper.delete(Wrappers.lambdaQuery(ChatSessionSummaryPo.class)
                .eq(ChatSessionSummaryPo::getSessionId, sessionId));
        log.info("cleared conversation summary sessionId={} agentId={}", sessionId, session.getAgentId());
    }

    @Override
    public void deleteSession(Long sessionId) {
        ChatSessionPo session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        messageMapper.delete(Wrappers.lambdaQuery(ChatMessagePo.class)
                .eq(ChatMessagePo::getSessionId, sessionId));
        summaryMapper.delete(Wrappers.lambdaQuery(ChatSessionSummaryPo.class)
                .eq(ChatSessionSummaryPo::getSessionId, sessionId));
        sessionMapper.deleteById(sessionId);
        log.info("deleted conversation session id={} agentId={}", sessionId, session.getAgentId());
    }

    /**
     * 不加 @Transactional：此方法立即返回 SseEmitter，LLM 调用在异步线程内完成。
     * 加 @Transactional 会在整个流式过程中持有 DB 连接（最长 130s），耗尽连接池。
     * 各写操作均为单条 SQL，MySQL 自动提交，无需显式事务边界。
     */
    @Override
    public SseEmitter sendMessage(Long agentId, Long sessionId, String content) {
        String traceId = TraceContext.ensureTraceId();
        TraceContext.put("agentId", agentId);
        AgentDetailResp agent = loadAgent(agentId);

        ChatSessionPo session = resolveSession(agentId, sessionId, content);
        TraceContext.put("conversationId", session.getId());

        // 持久化用户消息（状态直接 DONE，用户消息无需 STREAMING 过渡）
        Long userMsgId = insertMessage(session.getId(), traceId, "user", content, null, null, "DONE", null, null);

        // 创建助手消息占位符，后续流式填充
        Long assistantMsgId = insertMessage(session.getId(), traceId, "assistant", "", null, null, "PENDING", null, null);
        createConversationTrace(traceId, session, agent, userMsgId, assistantMsgId);

        // 加载历史（id < userMsgId 的已完成消息），构造摘要 + 短期上下文列表
        ConversationContext context = buildConversationContext(traceId, session.getId(), userMsgId, content, agent);
        log.info("chat request accepted traceId={} agentId={} sessionId={} userMsgId={} assistantMsgId={} contentLength={}",
                traceId, agentId, session.getId(), userMsgId, assistantMsgId, content == null ? 0 : content.length());

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        // SseEmitter 自身超时：OkHttp 未及时抛出 TIMEOUT 时的最后兜底
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout traceId={} sessionId={} msgId={}", traceId, session.getId(), assistantMsgId);
            cancelled.set(true);
            markMessageError(assistantMsgId, ErrorCode.LLM_TIMEOUT.name(), ErrorCode.LLM_TIMEOUT.getMessage(), null, false);
            finishConversationTrace(traceId, "TIMEOUT", ErrorCode.LLM_TIMEOUT.name(), ErrorCode.LLM_TIMEOUT.getMessage());
            incrementSessionCount(session.getId(), 1); // 只统计用户消息
            trySend(emitter, errorEvent(ErrorCode.LLM_TIMEOUT.getCode(), ErrorCode.LLM_TIMEOUT.getMessage(), assistantMsgId));
            emitter.complete();
        });

        emitter.onError(ex ->
            log.debug("SSE emitter error traceId={} sessionId={} msgId={}: {}",
                    traceId, session.getId(), assistantMsgId, ex.getMessage())
        );

        llmExecutor.execute(TraceContext.wrap(() ->
            doStream(emitter, cancelled, traceId, agent, session, assistantMsgId, context)
        ));

        return emitter;
    }

    @Override
    public SseEmitter sendMessageToSession(Long sessionId, String content) {
        ChatSessionPo session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
        }
        return sendMessage(session.getAgentId(), sessionId, content);
    }

    // ─── 流式驱动（运行在 llmExecutor 线程）────────────────────────────────────

    private void doStream(SseEmitter emitter, AtomicBoolean cancelled, String traceId,
                          AgentDetailResp agent, ChatSessionPo session,
                          Long assistantMsgId, ConversationContext context) {

        updateMessageStatus(assistantMsgId, "STREAMING");

        List<ChatMessage> contextMessages = context.messages();
        StringBuilder fullContent = new StringBuilder();
        long          streamStart = System.currentTimeMillis();
        log.info("chat stream start traceId={} agentId={} sessionId={} assistantMsgId={} workflowId={} modelConfigId={}",
                traceId, agent.getId(), session.getId(), assistantMsgId, agent.getWorkflowId(), agent.getModelConfigId());

        if (agent.getWorkflowId() != null) {
            try {
                String userContent = latestUserContent(contextMessages);
                WorkflowRunReq runReq = new WorkflowRunReq();
                runReq.setUserMessage(userContent);
                WorkflowRunResp workflowRun = workflowService.startAsyncRun(agent.getWorkflowId(), runReq);
                TraceContext.put("workflowRunId", workflowRun.getId());
                updateWorkflowTrace(traceId, agent.getWorkflowId(), workflowRun.getId());
                if (!cancelled.get()) {
                    trySend(emitter, workflowStartEvent(workflowRun.getId(), agent.getWorkflowId()));
                }
                String workflowResult = "工作流已开始执行，任务 ID：" + workflowRun.getId()
                        + "，可在工作流运行详情中查看进度和结果。";
                ChatResponse response = ChatResponse.builder()
                        .content(workflowResult)
                        .finishReason("stop")
                        .build();
                int latency = (int) (System.currentTimeMillis() - streamStart);
                finalizeMessage(assistantMsgId, workflowResult, response, latency);
                finishConversationTrace(traceId, "DONE", null, null);
                incrementSessionCount(session.getId(), 2);
                hifyMetrics.recordChatRequest(agent.getId(), "success", latency);
                if (!cancelled.get()) {
                    try {
                        emitter.send(SseEmitter.event()
                                .data(doneEvent(session.getId(), assistantMsgId, response)));
                        emitter.complete();
                    } catch (IOException e) {
                        log.debug("SSE workflow done send failed (client gone) traceId={} msgId={}", traceId, assistantMsgId);
                        markClientDisconnected(traceId);
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn("SSE workflow done send failed msgId={}: {}", assistantMsgId, e.getMessage());
                        emitter.complete();
                    }
                }
            } catch (BizException e) {
                log.warn("workflow execute failed traceId={} workflowId={} msgId={}: {}",
                        traceId, agent.getWorkflowId(), assistantMsgId, e.getMessage());
                markMessageError(assistantMsgId, ErrorCode.WORKFLOW_EXECUTE_FAILED.name(), e.getMessage(), null, false);
                finishConversationTrace(traceId, "ERROR", ErrorCode.WORKFLOW_EXECUTE_FAILED.name(), e.getMessage());
                incrementSessionCount(session.getId(), 1);
                hifyMetrics.recordChatRequest(agent.getId(), "failure", System.currentTimeMillis() - streamStart);
                if (!cancelled.get()) {
                    trySend(emitter, errorEvent(e.getCode(), e.getMessage(), assistantMsgId));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("workflow execute unexpected error traceId={} workflowId={} msgId={}",
                        traceId, agent.getWorkflowId(), assistantMsgId, e);
                markMessageError(assistantMsgId, ErrorCode.WORKFLOW_EXECUTE_FAILED.name(),
                        ErrorCode.WORKFLOW_EXECUTE_FAILED.getMessage(), e.getMessage(), false);
                finishConversationTrace(traceId, "BACKEND_ERROR", ErrorCode.WORKFLOW_EXECUTE_FAILED.name(),
                        ErrorCode.WORKFLOW_EXECUTE_FAILED.getMessage());
                incrementSessionCount(session.getId(), 1);
                hifyMetrics.recordChatRequest(agent.getId(), "failure", System.currentTimeMillis() - streamStart);
                if (!cancelled.get()) {
                    trySend(emitter, errorEvent(ErrorCode.WORKFLOW_EXECUTE_FAILED.getCode(),
                            ErrorCode.WORKFLOW_EXECUTE_FAILED.getMessage(), assistantMsgId));
                }
                emitter.complete();
            }
            return;
        }

        try {
            List<McpToolResp> boundTools = loadBoundTools(agent);
            List<Map<String, Object>> toolSchemas = toToolSchemas(boundTools);
            Map<String, McpToolResp> toolMap = boundTools.stream()
                    .collect(Collectors.toMap(McpToolResp::getName, tool -> tool, (left, right) -> left));
            if (!boundTools.isEmpty()) {
                markMcpTriggered(traceId);
            }
            log.info("chat tools loaded traceId={} agentId={} toolIds={} boundTools={}",
                    traceId, agent.getId(), agent.getToolIds(), boundTools.size());

            AtomicReference<Long> llmTraceId = new AtomicReference<>(createLlmTrace(traceId, agent.getModelConfigId()));
            AtomicBoolean firstTokenRecorded = new AtomicBoolean(false);
            ChatRequest request = ChatRequest.builder()
                    .systemPrompt(buildSystemPrompt(traceId, agent, contextMessages, context.summary()))
                    .messages(contextMessages)
                    .tools(toolSchemas.isEmpty() ? null : toolSchemas)
                    .temperature(agent.getTemperature())
                    .maxTokens(agent.getMaxTokens())
                    .build();

            llmCallService.streamChat(agent.getModelConfigId(), request, new ChatStreamCallback() {

                @Override
                public void onToken(String token) {
                    if (cancelled.get()) return;
                    recordFirstToken(traceId, llmTraceId.get(), streamStart, firstTokenRecorded);
                    fullContent.append(token);
                    if (!toolSchemas.isEmpty()) {
                        return;
                    }
                    try {
                        emitter.send(SseEmitter.event().data(tokenEvent(token)));
                    } catch (IOException e) {
                        // 客户端主动断开，停止写入但不中断 LLM 流（等待 onComplete 后更新 DB）
                        log.debug("SSE client disconnected traceId={} msgId={}", traceId, assistantMsgId);
                        cancelled.set(true);
                        markClientDisconnected(traceId);
                    } catch (Exception e) {
                        log.warn("SSE send failed traceId={} msgId={}: {}", traceId, assistantMsgId, e.getMessage());
                        cancelled.set(true);
                        markClientDisconnected(traceId);
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onComplete(ChatResponse response) {
                    log.info("chat first llm complete agentId={} sessionId={} assistantMsgId={} finishReason={} outputTokens={}",
                            agent.getId(), session.getId(), assistantMsgId,
                            response.getFinishReason(), response.getOutputTokens());
                    if (!toolSchemas.isEmpty() && isToolCallResponse(response)) {
                        streamAfterToolCalls(emitter, cancelled, traceId, llmTraceId.get(), firstTokenRecorded,
                                agent, session, assistantMsgId, contextMessages, request, response,
                                toolMap, fullContent, streamStart, 1);
                        return;
                    }
                    if (!toolSchemas.isEmpty()) {
                        ChatResponse inlineToolResponse = inlineToolCallResponse(response, fullContent.toString());
                        if (isToolCallResponse(inlineToolResponse)) {
                            streamAfterToolCalls(emitter, cancelled, traceId, llmTraceId.get(), firstTokenRecorded,
                                    agent, session, assistantMsgId, contextMessages, request, inlineToolResponse,
                                    toolMap, fullContent, streamStart, 1);
                            return;
                        }
                        sendBufferedContent(emitter, cancelled, assistantMsgId, fullContent.toString());
                    }
                    int latency = (int) (System.currentTimeMillis() - streamStart);
                    finishLlmTrace(llmTraceId.get(), response, latency, "DONE", null, null);
                    completeAssistantStream(emitter, cancelled, session.getId(), assistantMsgId,
                            agent, traceId, fullContent.toString(), response, latency);
                }

                @Override
                public void onError(LlmApiException e) {
                    log.warn("chat first llm error agentId={} sessionId={} assistantMsgId={} type={} status={} message={}",
                            agent.getId(), session.getId(), assistantMsgId,
                            e.getType(), e.getStatusCode(), e.getMessage());
                    handleLlmStreamError(emitter, cancelled, agent.getId(), session.getId(), assistantMsgId,
                            traceId, llmTraceId.get(), streamStart, e);
                }
            });

        } catch (LlmApiException e) {
            handleLlmStreamError(emitter, cancelled, agent.getId(), session.getId(), assistantMsgId,
                    traceId, null, streamStart, e);
        } catch (Exception e) {
            log.error("doStream unexpected error traceId={} msgId={}", traceId, assistantMsgId, e);
            boolean partial = fullContent.length() > 0;
            markMessageError(assistantMsgId, ErrorCode.INTERNAL_ERROR.name(),
                    ErrorCode.INTERNAL_ERROR.getMessage(), e.getMessage(), partial);
            finishConversationTrace(traceId, "BACKEND_ERROR", ErrorCode.INTERNAL_ERROR.name(),
                    ErrorCode.INTERNAL_ERROR.getMessage());
            incrementSessionCount(session.getId(), 1);
            hifyMetrics.recordChatRequest(agent.getId(), "failure", System.currentTimeMillis() - streamStart);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    private void handleLlmStreamError(SseEmitter emitter, AtomicBoolean cancelled,
                                      Long agentId, Long sessionId, Long assistantMsgId,
                                      String traceId, Long llmTraceId, long streamStart, LlmApiException e) {
        int code = switch (e.getType()) {
            case TIMEOUT -> ErrorCode.LLM_TIMEOUT.getCode();
            case RATE_LIMITED -> ErrorCode.TOO_MANY_REQUESTS.getCode();
            default -> ErrorCode.LLM_CALL_ERROR.getCode();
        };
        String errorName = switch (e.getType()) {
            case TIMEOUT -> ErrorCode.LLM_TIMEOUT.name();
            case RATE_LIMITED -> ErrorCode.TOO_MANY_REQUESTS.name();
            default -> ErrorCode.LLM_CALL_ERROR.name();
        };
        String traceStatus = e.getType() == LlmApiException.Type.TIMEOUT ? "TIMEOUT" : "ERROR";
        markMessageError(assistantMsgId, errorName, e.getMessage(), e.getMessage(), false);
        finishLlmTrace(llmTraceId, null, (int) (System.currentTimeMillis() - streamStart),
                traceStatus, errorName, e.getMessage());
        finishConversationTrace(traceId, traceStatus, errorName, e.getMessage());
        incrementSessionCount(sessionId, 1);
        hifyMetrics.recordChatRequest(agentId, "failure", System.currentTimeMillis() - streamStart);
        if (cancelled.get()) return;
        log.warn("chat stream error traceId={} sessionId={} assistantMsgId={} type={} status={} message={}",
                traceId, sessionId, assistantMsgId, e.getType(), e.getStatusCode(), e.getMessage());
        trySend(emitter, errorEvent(code, e.getMessage(), assistantMsgId));
        emitter.complete();
    }

    private void streamAfterToolCalls(SseEmitter emitter, AtomicBoolean cancelled,
                                      String traceId, Long llmTraceId, AtomicBoolean firstTokenRecorded,
                                      AgentDetailResp agent, ChatSessionPo session, Long assistantMsgId,
                                      List<ChatMessage> contextMessages, ChatRequest firstRequest,
                                      ChatResponse firstResponse, Map<String, McpToolResp> toolMap,
                                      StringBuilder fullContent, long streamStart, int toolRound) {
        contextMessages.add(ChatMessage.builder()
                .role("assistant")
                .content(firstResponse.getContent())
                .reasoningContent(firstResponse.getReasoningContent())
                .toolCalls(firstResponse.getToolCalls())
                .build());

        for (ToolCall toolCall : firstResponse.getToolCalls()) {
            String toolResult = executeToolCall(traceId, toolMap, toolCall, session.getId(), assistantMsgId);
            contextMessages.add(ChatMessage.builder()
                    .role("tool")
                    .content(toolResult)
                    .toolCallId(toolCall.getId())
                    .build());
        }

        fullContent.setLength(0);
        ChatRequest secondRequest = ChatRequest.builder()
                .systemPrompt(firstRequest.getSystemPrompt())
                .messages(contextMessages)
                .tools(firstRequest.getTools())
                .temperature(agent.getTemperature())
                .maxTokens(agent.getMaxTokens())
                .build();

        try {
            llmCallService.streamChat(agent.getModelConfigId(), secondRequest, new ChatStreamCallback() {
                @Override
                public void onToken(String token) {
                    if (cancelled.get()) return;
                    recordFirstToken(traceId, llmTraceId, streamStart, firstTokenRecorded);
                    fullContent.append(token);
                }

                @Override
                public void onComplete(ChatResponse response) {
                    log.info("chat tool round llm complete agentId={} sessionId={} assistantMsgId={} round={} finishReason={} outputTokens={}",
                            agent.getId(), session.getId(), assistantMsgId, toolRound,
                            response.getFinishReason(), response.getOutputTokens());
                    ChatResponse nextToolResponse = isToolCallResponse(response)
                            ? response
                            : inlineToolCallResponse(response, fullContent.toString());
                    if (toolRound < MAX_TOOL_ROUNDS && isToolCallResponse(nextToolResponse)) {
                        streamAfterToolCalls(emitter, cancelled, traceId, llmTraceId, firstTokenRecorded,
                                agent, session, assistantMsgId,
                                contextMessages, secondRequest, nextToolResponse, toolMap,
                                fullContent, streamStart, toolRound + 1);
                        return;
                    }
                    sendBufferedContent(emitter, cancelled, assistantMsgId, fullContent.toString());
                    int latency = (int) (System.currentTimeMillis() - streamStart);
                    finishLlmTrace(llmTraceId, response, latency, "DONE", null, null);
                    completeAssistantStream(emitter, cancelled, session.getId(), assistantMsgId,
                            agent, traceId, fullContent.toString(), response, latency);
                }

                @Override
                public void onError(LlmApiException e) {
                    log.warn("chat tool round llm error agentId={} sessionId={} assistantMsgId={} round={} type={} status={} message={}",
                            agent.getId(), session.getId(), assistantMsgId, toolRound,
                            e.getType(), e.getStatusCode(), e.getMessage());
                    handleLlmStreamError(emitter, cancelled, agent.getId(), session.getId(), assistantMsgId,
                            traceId, llmTraceId, streamStart, e);
                }
            });
        } catch (LlmApiException e) {
            handleLlmStreamError(emitter, cancelled, agent.getId(), session.getId(), assistantMsgId,
                    traceId, llmTraceId, streamStart, e);
        } catch (Exception e) {
            log.error("tool result stream unexpected error traceId={} msgId={}", traceId, assistantMsgId, e);
            markMessageError(assistantMsgId, ErrorCode.INTERNAL_ERROR.name(),
                    ErrorCode.INTERNAL_ERROR.getMessage(), e.getMessage(), fullContent.length() > 0);
            finishConversationTrace(traceId, "BACKEND_ERROR", ErrorCode.INTERNAL_ERROR.name(),
                    ErrorCode.INTERNAL_ERROR.getMessage());
            incrementSessionCount(session.getId(), 1);
            hifyMetrics.recordChatRequest(agent.getId(), "failure", System.currentTimeMillis() - streamStart);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    private void completeAssistantStream(SseEmitter emitter, AtomicBoolean cancelled,
                                         Long sessionId, Long assistantMsgId,
                                         AgentDetailResp agent, String traceId, String content, ChatResponse response, int latencyMs) {
        // 无论客户端是否已断开，都将最终结果写库
        finalizeMessage(assistantMsgId, content, response, latencyMs);
        incrementSessionCount(sessionId, 2);
        hifyMetrics.recordChatRequest(agent.getId(), "success", latencyMs);
        updateSummaryAsync(traceId, sessionId, agent, assistantMsgId);
        if (!cancelled.get()) {
            finishConversationTrace(traceId, "DONE", null, null);
        }

        if (cancelled.get()) return;
        try {
            emitter.send(SseEmitter.event()
                    .data(doneEvent(sessionId, assistantMsgId, response)));
            emitter.complete();
            log.info("chat stream done sessionId={} assistantMsgId={} finishReason={} latencyMs={} inputTokens={} outputTokens={}",
                    sessionId, assistantMsgId, response.getFinishReason(), latencyMs,
                    response.getInputTokens(), response.getOutputTokens());
        } catch (IOException e) {
            log.debug("SSE done send failed (client gone) traceId={} msgId={}", traceId, assistantMsgId);
            markClientDisconnected(traceId);
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private boolean isToolCallResponse(ChatResponse response) {
        return "tool_calls".equals(response.getFinishReason())
                && response.getToolCalls() != null
                && !response.getToolCalls().isEmpty();
    }

    private void sendBufferedContent(SseEmitter emitter, AtomicBoolean cancelled,
                                     Long assistantMsgId, String content) {
        if (cancelled.get() || content == null || content.isBlank()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().data(tokenEvent(content)));
        } catch (IOException e) {
            log.debug("SSE buffered content send failed msgId={}", assistantMsgId);
            cancelled.set(true);
        } catch (Exception e) {
            log.warn("SSE buffered content send failed msgId={}: {}", assistantMsgId, e.getMessage());
            cancelled.set(true);
            emitter.completeWithError(e);
        }
    }

    private ChatResponse inlineToolCallResponse(ChatResponse response, String content) {
        List<ToolCall> toolCalls = parseInlineToolCalls(content);
        if (toolCalls.isEmpty()) {
            return response;
        }
        return ChatResponse.builder()
                .content(null)
                .reasoningContent(response.getReasoningContent())
                .toolCalls(toolCalls)
                .finishReason("tool_calls")
                .inputTokens(response.getInputTokens())
                .outputTokens(response.getOutputTokens())
                .latencyMs(response.getLatencyMs())
                .build();
    }

    private List<ToolCall> parseInlineToolCalls(String content) {
        if (content == null || !content.contains("tool_call")) {
            return List.of();
        }
        List<ToolCall> dsmlToolCalls = parseDsmlToolCalls(content);
        if (!dsmlToolCalls.isEmpty()) {
            return dsmlToolCalls;
        }
        return parseDeepSeekToolCalls(content);
    }

    private List<ToolCall> parseDsmlToolCalls(String content) {
        List<ToolCall> toolCalls = new ArrayList<>();
        Matcher invokeMatcher = DSML_INVOKE_PATTERN.matcher(content);
        while (invokeMatcher.find()) {
            Map<String, Object> arguments = new HashMap<>();
            Matcher parameterMatcher = DSML_PARAMETER_PATTERN.matcher(invokeMatcher.group(2));
            while (parameterMatcher.find()) {
                arguments.put(parameterMatcher.group(1), cleanInlineToolValue(parameterMatcher.group(2)));
            }
            toolCalls.add(ToolCall.builder()
                    .id("call_" + UUID.randomUUID().toString().replace("-", ""))
                    .functionName(invokeMatcher.group(1))
                    .functionArguments(toJson(arguments))
                    .build());
        }
        return toolCalls;
    }

    private List<ToolCall> parseDeepSeekToolCalls(String content) {
        List<ToolCall> toolCalls = new ArrayList<>();
        Matcher matcher = DEEPSEEK_TOOL_CALL_PATTERN.matcher(content);
        while (matcher.find()) {
            toolCalls.add(ToolCall.builder()
                    .id("call_" + UUID.randomUUID().toString().replace("-", ""))
                    .functionName(matcher.group(1).trim())
                    .functionArguments(matcher.group(2).trim())
                    .build());
        }
        return toolCalls;
    }

    private String cleanInlineToolValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("<[^>]+>", "").trim();
    }

    private String executeToolCall(String traceId, Map<String, McpToolResp> toolMap, ToolCall toolCall,
                                   Long sessionId, Long assistantMsgId) {
        McpToolResp tool = toolMap.get(toolCall.getFunctionName());
        if (tool == null) {
            log.warn("mcp tool call skipped reason=tool_not_found name={}", toolCall.getFunctionName());
            return "工具调用失败: 未找到工具 " + toolCall.getFunctionName();
        }

        long start = System.currentTimeMillis();
        Map<String, Object> arguments = Map.of();
        try {
            arguments = parseToolArguments(toolCall.getFunctionArguments());
            log.info("mcp tool call start toolId={} serverId={} name={} argumentKeys={}",
                    tool.getId(), tool.getMcpServerId(), tool.getName(), arguments.keySet());
            String result = mcpClientService.callTool(tool.getMcpServerId(), tool.getName(), arguments);
            log.info("mcp tool call end toolId={} serverId={} name={} elapsedMs={} resultLength={}",
                    tool.getId(), tool.getMcpServerId(), tool.getName(),
                    System.currentTimeMillis() - start, result == null ? 0 : result.length());
            recordToolAudit(traceId, "CONVERSATION", sessionId, assistantMsgId, null, null,
                    tool, arguments, System.currentTimeMillis() - start, true, result, null);
            return result;
        } catch (Exception e) {
            log.warn("mcp tool call failed toolId={} serverId={} name={} elapsedMs={} message={}",
                    tool.getId(), tool.getMcpServerId(), tool.getName(),
                    System.currentTimeMillis() - start, e.getMessage());
            recordToolAudit(traceId, "CONVERSATION", sessionId, assistantMsgId, null, null,
                    tool, arguments, System.currentTimeMillis() - start, false, null, e.getMessage());
            return "工具调用失败: " + e.getMessage();
        }
    }

    private void recordToolAudit(String traceId, String sourceType, Long sessionId, Long messageId,
                                 Long workflowRunId, String workflowNodeKey,
                                 McpToolResp tool, Map<String, Object> arguments,
                                 long elapsedMs, boolean success, String result, String error) {
        try {
            mcpToolCallAuditService.record(McpToolCallAuditRecord.builder()
                    .sourceType(sourceType)
                    .traceId(traceId)
                    .conversationSessionId(sessionId)
                    .conversationMessageId(messageId)
                    .workflowRunId(workflowRunId)
                    .workflowNodeKey(workflowNodeKey)
                    .mcpServerId(tool.getMcpServerId())
                    .toolName(tool.getName())
                    .arguments(arguments)
                    .elapsedMs(elapsedMs)
                    .success(success)
                    .result(result)
                    .error(error)
                    .build());
        } catch (Exception auditError) {
            log.warn("mcp tool audit failed sourceType={} tool={} message={}",
                    sourceType, tool.getName(), auditError.getMessage());
        }
    }

    private Map<String, Object> parseToolArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return Map.of("_raw", argumentsJson);
        }
    }

    private List<McpToolResp> loadBoundTools(AgentDetailResp agent) {
        if (agent.getToolIds() == null || agent.getToolIds().isEmpty()) {
            return List.of();
        }
        return mcpService.listEnabledToolsByIds(agent.getToolIds());
    }

    private List<Map<String, Object>> toToolSchemas(List<McpToolResp> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream().map(this::toToolSchema).toList();
    }

    private Map<String, Object> toToolSchema(McpToolResp tool) {
        Map<String, Object> parameters = tool.getInputSchema();
        if (parameters == null || parameters.isEmpty()) {
            parameters = Map.of("type", "object", "properties", Map.of());
        }
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", tool.getName(),
                        "description", tool.getDescription() == null ? "" : tool.getDescription(),
                        "parameters", parameters
                )
        );
    }

    private String latestUserContent(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if ("user".equals(message.getRole()) && message.getContent() != null) {
                return message.getContent();
            }
        }
        return "";
    }

    // ─── DB 写操作（各自单条 SQL，MySQL 自动提交）─────────────────────────────

    private Long insertMessage(Long sessionId, String traceId, String role, String content,
                               List<Map<String, Object>> toolCalls, String toolCallId,
                               String status, String finishReason, Integer latencyMs) {
        ChatMessagePo po = new ChatMessagePo();
        po.setSessionId(sessionId);
        po.setTraceId(traceId);
        po.setRole(role);
        po.setContent(content);
        po.setToolCalls(toolCalls);
        po.setToolCallId(toolCallId);
        po.setStatus(status);
        po.setFinishReason(finishReason);
        po.setLatencyMs(latencyMs);
        messageMapper.insert(po);
        return po.getId();
    }

    private void createConversationTrace(String traceId, ChatSessionPo session, AgentDetailResp agent,
                                         Long userMsgId, Long assistantMsgId) {
        try {
            ModelTraceInfo model = resolveModelTrace(agent.getModelConfigId());
            ConversationTracePo po = new ConversationTracePo();
            po.setTraceId(traceId);
            po.setSessionId(session.getId());
            po.setUserMessageId(userMsgId);
            po.setAssistantMessageId(assistantMsgId);
            po.setAgentId(agent.getId());
            po.setAgentName(agent.getName());
            po.setModelConfigId(agent.getModelConfigId());
            po.setProviderId(model.providerId());
            po.setProviderName(model.providerName());
            po.setProviderType(model.providerType());
            po.setModelId(model.modelId());
            po.setWorkflowId(agent.getWorkflowId());
            po.setRagTriggered(0);
            po.setMcpTriggered(0);
            po.setMemoryEnabled(memoryEnabled(agent) ? 1 : 0);
            po.setSummaryUsed(0);
            po.setStatus("RUNNING");
            po.setStartedAt(LocalDateTime.now());
            conversationTraceMapper.insert(po);
        } catch (Exception e) {
            log.warn("conversation trace create failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private Long createLlmTrace(String traceId, Long modelConfigId) {
        try {
            ModelTraceInfo model = resolveModelTrace(modelConfigId);
            ConversationLlmTracePo po = new ConversationLlmTracePo();
            po.setTraceId(traceId);
            po.setProviderId(model.providerId());
            po.setProviderName(model.providerName());
            po.setProviderType(model.providerType());
            po.setModelConfigId(modelConfigId);
            po.setModelId(model.modelId());
            po.setStreaming(1);
            po.setStatus("RUNNING");
            conversationLlmTraceMapper.insert(po);
            return po.getId();
        } catch (Exception e) {
            log.warn("llm trace create failed traceId={} message={}", traceId, e.getMessage());
            return null;
        }
    }

    private void recordFirstToken(String traceId, Long llmTraceId, long streamStart, AtomicBoolean firstTokenRecorded) {
        if (!firstTokenRecorded.compareAndSet(false, true)) {
            return;
        }
        int latencyMs = (int) (System.currentTimeMillis() - streamStart);
        try {
            conversationTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationTracePo.class)
                    .eq(ConversationTracePo::getTraceId, traceId)
                    .set(ConversationTracePo::getFirstTokenAt, LocalDateTime.now()));
            if (llmTraceId != null) {
                conversationLlmTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationLlmTracePo.class)
                        .eq(ConversationLlmTracePo::getId, llmTraceId)
                        .set(ConversationLlmTracePo::getFirstTokenLatencyMs, latencyMs));
            }
        } catch (Exception e) {
            log.warn("first token trace update failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private void finishLlmTrace(Long llmTraceId, ChatResponse response, int latencyMs,
                                String status, String errorCode, String errorMessage) {
        if (llmTraceId == null) {
            return;
        }
        try {
            var wrapper = Wrappers.lambdaUpdate(ConversationLlmTracePo.class)
                    .eq(ConversationLlmTracePo::getId, llmTraceId)
                    .set(ConversationLlmTracePo::getStatus, status)
                    .set(ConversationLlmTracePo::getTotalLatencyMs, latencyMs)
                    .set(ConversationLlmTracePo::getErrorCode, errorCode)
                    .set(ConversationLlmTracePo::getErrorMessage, abbreviate(errorMessage, 512));
            if (response != null) {
                wrapper.set(ConversationLlmTracePo::getInputTokens, response.getInputTokens())
                        .set(ConversationLlmTracePo::getOutputTokens, response.getOutputTokens());
            }
            conversationLlmTraceMapper.update(null, wrapper);
        } catch (Exception e) {
            log.warn("llm trace finish failed llmTraceId={} message={}", llmTraceId, e.getMessage());
        }
    }

    private void finishConversationTrace(String traceId, String status, String errorCode, String errorMessage) {
        try {
            conversationTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationTracePo.class)
                    .eq(ConversationTracePo::getTraceId, traceId)
                    .set(ConversationTracePo::getStatus, status)
                    .set(ConversationTracePo::getErrorCode, errorCode)
                    .set(ConversationTracePo::getErrorMessage, abbreviate(errorMessage, 512))
                    .set(ConversationTracePo::getFinishedAt, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("conversation trace finish failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private void markClientDisconnected(String traceId) {
        try {
            conversationTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationTracePo.class)
                    .eq(ConversationTracePo::getTraceId, traceId)
                    .eq(ConversationTracePo::getStatus, "RUNNING")
                    .set(ConversationTracePo::getStatus, "CLIENT_DISCONNECTED")
                    .set(ConversationTracePo::getFinishedAt, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("conversation disconnect trace update failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private void markRagTriggered(String traceId) {
        try {
            conversationTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationTracePo.class)
                    .eq(ConversationTracePo::getTraceId, traceId)
                    .set(ConversationTracePo::getRagTriggered, 1));
        } catch (Exception e) {
            log.warn("rag trace flag update failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private void markMcpTriggered(String traceId) {
        try {
            conversationTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationTracePo.class)
                    .eq(ConversationTracePo::getTraceId, traceId)
                    .set(ConversationTracePo::getMcpTriggered, 1));
        } catch (Exception e) {
            log.warn("mcp trace flag update failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private void updateWorkflowTrace(String traceId, Long workflowId, Long workflowRunId) {
        try {
            conversationTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationTracePo.class)
                    .eq(ConversationTracePo::getTraceId, traceId)
                    .set(ConversationTracePo::getWorkflowId, workflowId)
                    .set(ConversationTracePo::getWorkflowRunId, workflowRunId));
        } catch (Exception e) {
            log.warn("workflow trace update failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private void markSummaryUsed(String traceId, Integer summaryVersion) {
        try {
            conversationTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationTracePo.class)
                    .eq(ConversationTracePo::getTraceId, traceId)
                    .set(ConversationTracePo::getSummaryUsed, 1)
                    .set(ConversationTracePo::getSummaryVersion, summaryVersion));
        } catch (Exception e) {
            log.warn("summary trace update failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private void finishSummaryTrace(String traceId, long startedAt, String errorMessage) {
        try {
            conversationTraceMapper.update(null, Wrappers.lambdaUpdate(ConversationTracePo.class)
                    .eq(ConversationTracePo::getTraceId, traceId)
                    .set(ConversationTracePo::getSummaryLatencyMs, (int) (System.currentTimeMillis() - startedAt))
                    .set(ConversationTracePo::getSummaryErrorMessage, abbreviate(errorMessage, 512)));
        } catch (Exception e) {
            log.warn("summary trace finish failed traceId={} message={}", traceId, e.getMessage());
        }
    }

    private void updateSummaryAsync(String traceId, Long sessionId, AgentDetailResp agent, Long latestMessageId) {
        if (!memoryEnabled(agent)) {
            return;
        }
        try {
            if (llmExecutor.getActiveCount() >= llmExecutor.getMaximumPoolSize()
                    && llmExecutor.getQueue().remainingCapacity() == 0) {
                log.warn("summary task skipped because llmExecutor is saturated traceId={} sessionId={}",
                        traceId, sessionId);
                finishSummaryTrace(traceId, System.currentTimeMillis(), "摘要任务跳过：LLM 线程池已满载");
                return;
            }
            llmExecutor.execute(TraceContext.wrap(() -> updateSummaryIfNeeded(traceId, sessionId, agent, latestMessageId)));
        } catch (Exception e) {
            log.warn("summary task submit failed traceId={} sessionId={} message={}", traceId, sessionId, e.getMessage());
            finishSummaryTrace(traceId, System.currentTimeMillis(), e.getMessage());
        }
    }

    private void updateSummaryIfNeeded(String traceId, Long sessionId, AgentDetailResp agent, Long latestMessageId) {
        long startedAt = System.currentTimeMillis();
        try {
            int threshold = effectiveSummaryTriggerMessageCount(agent);
            Long doneCount = messageMapper.selectCount(Wrappers.lambdaQuery(ChatMessagePo.class)
                    .eq(ChatMessagePo::getSessionId, sessionId)
                    .eq(ChatMessagePo::getStatus, "DONE"));
            if (doneCount == null || doneCount < threshold) {
                return;
            }

            ChatSessionSummaryPo currentSummary = latestSummary(sessionId);
            if (currentSummary != null
                    && currentSummary.getSourceMessageEndId() != null
                    && currentSummary.getSourceMessageEndId() >= latestMessageId) {
                return;
            }

            Long afterMessageId = currentSummary == null ? null : currentSummary.getSourceMessageEndId();
            List<ChatMessagePo> newMessages = messageMapper.selectList(Wrappers.lambdaQuery(ChatMessagePo.class)
                    .eq(ChatMessagePo::getSessionId, sessionId)
                    .eq(ChatMessagePo::getStatus, "DONE")
                    .gt(afterMessageId != null, ChatMessagePo::getId, afterMessageId)
                    .le(ChatMessagePo::getId, latestMessageId)
                    .orderByAsc(ChatMessagePo::getId));
            if (newMessages.isEmpty()) {
                return;
            }

            String prompt = buildSummaryPrompt(currentSummary, newMessages);
            ChatResponse response = llmCallService.chat(effectiveSummaryModelConfigId(agent), ChatRequest.builder()
                    .messages(List.of(ChatMessage.builder().role("user").content(prompt).build()))
                    .maxTokens(effectiveSummaryMaxTokens(agent))
                    .temperature(BigDecimal.ZERO)
                    .build());
            String summaryText = response == null ? null : response.getContent();
            if (!StringUtils.hasText(summaryText)) {
                throw new BizException(ErrorCode.LLM_CALL_ERROR, "摘要模型返回为空");
            }
            saveSessionSummary(sessionId, agent.getId(), currentSummary, newMessages, summaryText.trim());
            finishSummaryTrace(traceId, startedAt, null);
            log.info("conversation summary updated traceId={} sessionId={} messages={} latencyMs={}",
                    traceId, sessionId, newMessages.size(), System.currentTimeMillis() - startedAt);
        } catch (Exception e) {
            saveFailedSummary(sessionId, agent.getId(), latestMessageId, e.getMessage());
            finishSummaryTrace(traceId, startedAt, e.getMessage());
            log.warn("conversation summary update failed traceId={} sessionId={} message={}",
                    traceId, sessionId, e.getMessage());
        }
    }

    private void saveSessionSummary(Long sessionId, Long agentId, ChatSessionSummaryPo currentSummary,
                                    List<ChatMessagePo> sourceMessages, String summaryText) {
        ChatSessionSummaryPo po = currentSummary == null ? new ChatSessionSummaryPo() : currentSummary;
        po.setSessionId(sessionId);
        po.setAgentId(agentId);
        po.setSummary(summaryText);
        po.setVersion(currentSummary == null || currentSummary.getVersion() == null
                ? 1 : currentSummary.getVersion() + 1);
        po.setSourceMessageStartId(currentSummary != null && currentSummary.getSourceMessageStartId() != null
                ? currentSummary.getSourceMessageStartId()
                : sourceMessages.get(0).getId());
        po.setSourceMessageEndId(sourceMessages.get(sourceMessages.size() - 1).getId());
        po.setSourceMessageCount(currentSummary == null || currentSummary.getSourceMessageCount() == null
                ? sourceMessages.size()
                : currentSummary.getSourceMessageCount() + sourceMessages.size());
        po.setStatus("DONE");
        po.setErrorMessage(null);
        po.setSummarizedAt(LocalDateTime.now());
        if (currentSummary == null) {
            summaryMapper.insert(po);
        } else {
            summaryMapper.updateById(po);
        }
    }

    private void saveFailedSummary(Long sessionId, Long agentId, Long latestMessageId, String errorMessage) {
        try {
            ChatSessionSummaryPo po = new ChatSessionSummaryPo();
            po.setSessionId(sessionId);
            po.setAgentId(agentId);
            po.setSummary("");
            po.setVersion(1);
            po.setSourceMessageEndId(latestMessageId);
            po.setSourceMessageCount(0);
            po.setStatus("FAILED");
            po.setErrorMessage(abbreviate(errorMessage, 512));
            po.setSummarizedAt(LocalDateTime.now());
            summaryMapper.insert(po);
        } catch (Exception e) {
            log.warn("failed summary record insert failed sessionId={} message={}", sessionId, e.getMessage());
        }
    }

    private String buildSummaryPrompt(ChatSessionSummaryPo currentSummary, List<ChatMessagePo> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("请将以下对话压缩成后续对话需要保留的摘要。\n")
                .append("只保留用户目标、明确偏好、关键事实、未完成任务、重要约束。\n")
                .append("不要写入知识库参考资料原文，不要引入对话中没有的信息。\n")
                .append("请用以下结构输出：\n")
                .append("【用户目标】\n【关键事实】\n【偏好和约束】\n【未完成事项】\n\n");
        if (currentSummary != null && StringUtils.hasText(currentSummary.getSummary())) {
            builder.append("【已有摘要】\n").append(currentSummary.getSummary()).append("\n\n");
        }
        builder.append("【新增对话】\n");
        for (ChatMessagePo message : messages) {
            builder.append(message.getRole()).append(": ")
                    .append(abbreviate(message.getContent(), 2000))
                    .append("\n");
        }
        return builder.toString();
    }

    private ChatSessionSummaryPo latestSummary(Long sessionId) {
        return summaryMapper.selectOne(Wrappers.lambdaQuery(ChatSessionSummaryPo.class)
                .eq(ChatSessionSummaryPo::getSessionId, sessionId)
                .eq(ChatSessionSummaryPo::getStatus, "DONE")
                .orderByDesc(ChatSessionSummaryPo::getId)
                .last("LIMIT 1"));
    }

    private ConversationSummaryResp toSummaryResp(ChatSessionSummaryPo po) {
        ConversationSummaryResp resp = new ConversationSummaryResp();
        resp.setId(po.getId());
        resp.setSessionId(po.getSessionId());
        resp.setAgentId(po.getAgentId());
        resp.setSummary(po.getSummary());
        resp.setVersion(po.getVersion());
        resp.setSourceMessageStartId(po.getSourceMessageStartId());
        resp.setSourceMessageEndId(po.getSourceMessageEndId());
        resp.setSourceMessageCount(po.getSourceMessageCount());
        resp.setStatus(po.getStatus());
        resp.setErrorMessage(po.getErrorMessage());
        resp.setSummarizedAt(po.getSummarizedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private boolean memoryEnabled(AgentDetailResp agent) {
        return agent != null && Integer.valueOf(1).equals(agent.getMemoryEnabled());
    }

    private int effectiveSummaryTriggerMessageCount(AgentDetailResp agent) {
        return agent.getSummaryTriggerMessageCount() == null ? 20 : agent.getSummaryTriggerMessageCount();
    }

    private int effectiveSummaryMaxTokens(AgentDetailResp agent) {
        return agent.getSummaryMaxTokens() == null ? 800 : agent.getSummaryMaxTokens();
    }

    private Long effectiveSummaryModelConfigId(AgentDetailResp agent) {
        return agent.getSummaryModelConfigId() == null ? agent.getModelConfigId() : agent.getSummaryModelConfigId();
    }

    private void saveRagTrace(String traceId, List<KnowledgeSearchResp> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        for (KnowledgeSearchResp chunk : chunks) {
            try {
                ConversationRagTracePo po = new ConversationRagTracePo();
                po.setTraceId(traceId);
                po.setKnowledgeBaseId(chunk.getKnowledgeBaseId());
                po.setKnowledgeBaseName(null);
                po.setDocumentId(chunk.getDocumentId());
                po.setDocumentName(chunk.getDocumentName());
                po.setChunkId(chunk.getId());
                po.setChunkIndex(chunk.getChunkIndex());
                Double score = chunk.getFinalScore() != null ? chunk.getFinalScore() : chunk.getScore();
                po.setScore(score == null ? null : BigDecimal.valueOf(score));
                po.setContentPreview(abbreviate(chunk.getContent(), 512));
                conversationRagTraceMapper.insert(po);
            } catch (Exception e) {
                log.warn("rag trace insert failed traceId={} chunkId={} message={}",
                        traceId, chunk.getId(), e.getMessage());
            }
        }
    }

    private void updateMessageStatus(Long msgId, String status) {
        messageMapper.update(null, Wrappers.lambdaUpdate(ChatMessagePo.class)
                .eq(ChatMessagePo::getId, msgId)
                .set(ChatMessagePo::getStatus, status));
    }

    private void finalizeMessage(Long msgId, String content, ChatResponse response, int latencyMs) {
        messageMapper.update(null, Wrappers.lambdaUpdate(ChatMessagePo.class)
                .eq(ChatMessagePo::getId, msgId)
                .set(ChatMessagePo::getContent,      content)
                .set(ChatMessagePo::getStatus,       "DONE")
                .set(ChatMessagePo::getFinishReason, response.getFinishReason())
                .set(ChatMessagePo::getTokens,       response.getOutputTokens())
                .set(ChatMessagePo::getLatencyMs,    latencyMs)
                .set(ChatMessagePo::getTotalLatencyMs, latencyMs)
                .set(ChatMessagePo::getPartial, 0)
                .set(ChatMessagePo::getErrorCode, null)
                .set(ChatMessagePo::getErrorMessage, null)
                .set(ChatMessagePo::getDebugError, null)
                .set(ChatMessagePo::getToolCalls,    toStoredToolCalls(response.getToolCalls())));
    }

    private void markMessageError(Long msgId, String errorCode, String errorMessage,
                                  String debugError, boolean partial) {
        var wrapper = Wrappers.lambdaUpdate(ChatMessagePo.class)
                .eq(ChatMessagePo::getId, msgId)
                .set(ChatMessagePo::getStatus, "ERROR")
                .set(ChatMessagePo::getFinishReason, "error")
                .set(ChatMessagePo::getErrorCode, errorCode)
                .set(ChatMessagePo::getErrorMessage, abbreviate(errorMessage, 512))
                .set(ChatMessagePo::getDebugError, abbreviate(debugError, 2000))
                .set(ChatMessagePo::getPartial, partial ? 1 : 0);
        if (!partial) {
            wrapper.set(ChatMessagePo::getContent, "");
        }
        messageMapper.update(null, wrapper);
    }

    /** message_count 原子自增，避免乐观锁并发冲突 */
    private void incrementSessionCount(Long sessionId, int delta) {
        LambdaUpdateWrapper<ChatSessionPo> wrapper = Wrappers.lambdaUpdate(ChatSessionPo.class)
                .eq(ChatSessionPo::getId, sessionId)
                .set(ChatSessionPo::getLastMessageAt, LocalDateTime.now())
                .setSql("message_count = message_count + " + delta);
        sessionMapper.update(null, wrapper);
    }

    // ─── 业务辅助──────────────────────────────────────────────────────────────

    private AgentDetailResp loadAgent(Long agentId) {
        AgentDetailResp agent = agentService.getDetail(agentId);
        if (agent.getEnabled() == null || agent.getEnabled() != 1) {
            throw new BizException(ErrorCode.AGENT_MODEL_UNAVAILABLE, "Agent 已禁用: " + agentId);
        }
        return agent;
    }

    private ChatSessionPo resolveSession(Long agentId, Long sessionId, String firstContent) {
        if (sessionId != null) {
            ChatSessionPo session = sessionMapper.selectOne(
                    Wrappers.lambdaQuery(ChatSessionPo.class)
                            .eq(ChatSessionPo::getId, sessionId));
            if (session == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "会话不存在: " + sessionId);
            }
            if (!session.getAgentId().equals(agentId)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "会话不属于当前 Agent");
            }
            return session;
        }
        ChatSessionPo session = new ChatSessionPo();
        session.setAgentId(agentId);
        session.setUserId(0L);
        String title = firstContent.length() > 20 ? firstContent.substring(0, 20) : firstContent;
        session.setTitle(title);
        session.setStatus("ACTIVE");
        session.setMessageCount(0);
        sessionMapper.insert(session);
        return session;
    }

    /**
     * 加载对话历史，组装成 LLM 输入列表。
     * 取 id < userMsgId 的已完成消息，按时间升序，受 maxContextTurns 滑动窗口限制。
     */
    private ConversationContext buildConversationContext(String traceId, Long sessionId, Long userMsgId,
                                                         String currentContent, AgentDetailResp agent) {
        ChatSessionSummaryPo summary = memoryEnabled(agent) ? latestSummary(sessionId) : null;
        if (summary != null && StringUtils.hasText(summary.getSummary())) {
            markSummaryUsed(traceId, summary.getVersion());
            log.info("conversation summary used traceId={} sessionId={} version={} sourceEndMessageId={}",
                    traceId, sessionId, summary.getVersion(), summary.getSourceMessageEndId());
        }
        List<ChatMessage> messages = buildContextMessages(sessionId, userMsgId, currentContent,
                agent.getMaxContextTurns());
        return new ConversationContext(messages, summary);
    }

    private List<ChatMessage> buildContextMessages(Long sessionId, Long userMsgId,
                                                   String currentContent, Integer maxContextTurns) {
        var wrapper = Wrappers.lambdaQuery(ChatMessagePo.class)
                .eq(ChatMessagePo::getSessionId, sessionId)
                .eq(ChatMessagePo::getStatus,    "DONE")
                .lt(ChatMessagePo::getId,         userMsgId)
                .orderByAsc(ChatMessagePo::getId);

        if (maxContextTurns != null && maxContextTurns > 0) {
            // 每 turn = 1 user + 1 assistant，取最近 N 轮，DESC 取完再反转
            wrapper = Wrappers.lambdaQuery(ChatMessagePo.class)
                    .eq(ChatMessagePo::getSessionId, sessionId)
                    .eq(ChatMessagePo::getStatus,    "DONE")
                    .lt(ChatMessagePo::getId,         userMsgId)
                    .orderByDesc(ChatMessagePo::getId)
                    .last("LIMIT " + (maxContextTurns * 2));
            List<ChatMessagePo> rows = messageMapper.selectList(wrapper);
            Collections.reverse(rows);
            List<ChatMessage> result = rows.stream().map(this::toApiMessage).collect(Collectors.toList());
            result.add(ChatMessage.builder().role("user").content(currentContent).build());
            return result;
        }

        List<ChatMessage> result = messageMapper.selectList(wrapper)
                .stream().map(this::toApiMessage).collect(Collectors.toList());
        result.add(ChatMessage.builder().role("user").content(currentContent).build());
        return result;
    }

    private String buildSystemPrompt(String traceId, AgentDetailResp agent, List<ChatMessage> messages,
                                     ChatSessionSummaryPo summary) {
        String systemPrompt = agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt();
        List<Long> knowledgeBaseIds = agent.getKnowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return appendConversationSummary(systemPrompt, summary);
        }

        String userMessage = "";
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if ("user".equals(message.getRole()) && message.getContent() != null) {
                userMessage = message.getContent();
                break;
            }
        }
        if (userMessage.isBlank()) {
            return appendConversationSummary(systemPrompt, summary);
        }

        markRagTriggered(traceId);
        KnowledgeSearchReq req = new KnowledgeSearchReq();
        req.setKnowledgeBaseIds(knowledgeBaseIds);
        req.setQueryText(userMessage);
        req.setSourceType("CONVERSATION");
        req.setSourceId("agent:" + agent.getId());
        req.setIncludeTrace(true);
        List<KnowledgeSearchResp> chunks = knowledgeService.searchSimilar(req);
        saveRagTrace(traceId, chunks);
        log.info("rag candidates agentId={} knowledgeBaseIds={} candidates={}",
                agent.getId(), knowledgeBaseIds, chunks.stream()
                        .map(chunk -> "chunkId=" + chunk.getId()
                                + ",documentId=" + chunk.getDocumentId()
                                + ",chunkIndex=" + chunk.getChunkIndex()
                                + ",score=" + chunk.getScore())
                        .toList());
        if (chunks.isEmpty()) {
            log.info("rag no hit agentId={} knowledgeBaseIds={} userMessage={}",
                    agent.getId(), knowledgeBaseIds, abbreviate(userMessage, 80));
            return appendConversationSummary(systemPrompt, summary);
        }
        log.info("rag hit agentId={} knowledgeBaseIds={} hits={}",
                agent.getId(), knowledgeBaseIds, chunks.stream()
                        .map(chunk -> "chunkId=" + chunk.getId()
                                + ",documentId=" + chunk.getDocumentId()
                                + ",chunkIndex=" + chunk.getChunkIndex()
                                + ",score=" + chunk.getScore())
                        .toList());

        StringBuilder builder = new StringBuilder(systemPrompt);
        builder.append("\n\n请基于以下参考资料回答用户问题。\n")
                .append("如果资料中没有相关信息，直接说\"我没有找到相关资料\"，不要编造。\n\n")
                .append("【参考资料】\n");
        for (int i = 0; i < chunks.size(); i++) {
            builder.append('[')
                    .append(i + 1)
                    .append("] ")
                    .append(chunks.get(i).getContent())
                    .append('\n');
        }
        return appendConversationSummary(builder.toString().trim(), summary);
    }

    private String appendConversationSummary(String systemPrompt, ChatSessionSummaryPo summary) {
        if (summary == null || !StringUtils.hasText(summary.getSummary())) {
            return systemPrompt;
        }
        return (systemPrompt == null ? "" : systemPrompt)
                + "\n\n【会话摘要 / 记忆】\n"
                + "以下摘要来自当前会话较早消息，仅用于保持上下文连续。"
                + "如果摘要与用户当前消息冲突，以当前消息为准。\n"
                + summary.getSummary();
    }

    private static String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    private ConversationTraceDetailResp buildTraceDetail(ConversationTracePo trace) {
        ConversationTraceDetailResp resp = new ConversationTraceDetailResp();
        resp.setTraceId(trace.getTraceId());
        resp.setStatus(trace.getStatus());
        resp.setErrorCode(trace.getErrorCode());
        resp.setErrorMessage(trace.getErrorMessage());
        resp.setStartedAt(trace.getStartedAt());
        resp.setFirstTokenAt(trace.getFirstTokenAt());
        resp.setFinishedAt(trace.getFinishedAt());

        ConversationTraceDetailResp.AgentTrace agent = new ConversationTraceDetailResp.AgentTrace();
        agent.setId(trace.getAgentId());
        agent.setName(trace.getAgentName());
        resp.setAgent(agent);

        ConversationTraceDetailResp.ModelTrace model = new ConversationTraceDetailResp.ModelTrace();
        model.setModelConfigId(trace.getModelConfigId());
        model.setProviderId(trace.getProviderId());
        model.setProviderName(trace.getProviderName());
        model.setProviderType(trace.getProviderType());
        model.setModelId(trace.getModelId());
        resp.setModel(model);

        ConversationTraceDetailResp.WorkflowTrace workflow = new ConversationTraceDetailResp.WorkflowTrace();
        workflow.setTriggered(trace.getWorkflowId() != null);
        workflow.setWorkflowId(trace.getWorkflowId());
        workflow.setWorkflowRunId(trace.getWorkflowRunId());
        resp.setWorkflow(workflow);

        ConversationTraceDetailResp.RagTrace rag = new ConversationTraceDetailResp.RagTrace();
        rag.setTriggered(trace.getRagTriggered() != null && trace.getRagTriggered() == 1);
        rag.setHits(listRagHits(trace.getTraceId()));
        resp.setRag(rag);

        ConversationTraceDetailResp.MemoryTrace memory = new ConversationTraceDetailResp.MemoryTrace();
        memory.setEnabled(trace.getMemoryEnabled() != null && trace.getMemoryEnabled() == 1);
        memory.setSummaryUsed(trace.getSummaryUsed() != null && trace.getSummaryUsed() == 1);
        memory.setSummaryVersion(trace.getSummaryVersion());
        memory.setSummaryLatencyMs(trace.getSummaryLatencyMs());
        memory.setSummaryErrorMessage(trace.getSummaryErrorMessage());
        resp.setMemory(memory);

        ConversationTraceDetailResp.McpTrace mcp = new ConversationTraceDetailResp.McpTrace();
        mcp.setTriggered(trace.getMcpTriggered() != null && trace.getMcpTriggered() == 1);
        mcp.setToolCalls(listToolCalls(trace.getTraceId()));
        resp.setMcp(mcp);

        resp.setLlm(getLlmTrace(trace.getTraceId()));
        return resp;
    }

    private List<ConversationTraceDetailResp.RagHit> listRagHits(String traceId) {
        return conversationRagTraceMapper.selectList(Wrappers.lambdaQuery(ConversationRagTracePo.class)
                        .eq(ConversationRagTracePo::getTraceId, traceId)
                        .orderByDesc(ConversationRagTracePo::getScore)
                        .orderByAsc(ConversationRagTracePo::getId))
                .stream()
                .map(po -> {
                    ConversationTraceDetailResp.RagHit hit = new ConversationTraceDetailResp.RagHit();
                    hit.setKnowledgeBaseId(po.getKnowledgeBaseId());
                    hit.setKnowledgeBaseName(po.getKnowledgeBaseName());
                    hit.setDocumentId(po.getDocumentId());
                    hit.setDocumentName(po.getDocumentName());
                    hit.setChunkId(po.getChunkId());
                    hit.setChunkIndex(po.getChunkIndex());
                    hit.setScore(po.getScore() == null ? null : po.getScore().doubleValue());
                    hit.setContentPreview(po.getContentPreview());
                    return hit;
                })
                .toList();
    }

    private List<ConversationTraceDetailResp.ToolCallTrace> listToolCalls(String traceId) {
        List<McpToolCallAuditResp> audits = mcpToolCallAuditService.listByTraceId(traceId);
        return audits.stream().map(audit -> {
            ConversationTraceDetailResp.ToolCallTrace call = new ConversationTraceDetailResp.ToolCallTrace();
            call.setToolName(audit.getToolName());
            call.setArgumentKeys(audit.getArgumentKeys());
            call.setElapsedMs(audit.getElapsedMs());
            call.setSuccess(audit.getSuccess());
            call.setErrorMessage(audit.getErrorSummary());
            return call;
        }).toList();
    }

    private ConversationTraceDetailResp.LlmTrace getLlmTrace(String traceId) {
        ConversationLlmTracePo po = conversationLlmTraceMapper.selectOne(
                Wrappers.lambdaQuery(ConversationLlmTracePo.class)
                        .eq(ConversationLlmTracePo::getTraceId, traceId)
                        .orderByDesc(ConversationLlmTracePo::getId)
                        .last("LIMIT 1"));
        if (po == null) {
            return null;
        }
        ConversationTraceDetailResp.LlmTrace llm = new ConversationTraceDetailResp.LlmTrace();
        llm.setProviderId(po.getProviderId());
        llm.setProviderName(po.getProviderName());
        llm.setProviderType(po.getProviderType());
        llm.setModelConfigId(po.getModelConfigId());
        llm.setModelId(po.getModelId());
        llm.setInputTokens(po.getInputTokens());
        llm.setOutputTokens(po.getOutputTokens());
        llm.setFirstTokenLatencyMs(po.getFirstTokenLatencyMs());
        llm.setTotalLatencyMs(po.getTotalLatencyMs());
        llm.setStatus(po.getStatus());
        llm.setErrorCode(po.getErrorCode());
        llm.setErrorMessage(po.getErrorMessage());
        return llm;
    }

    private ModelTraceInfo resolveModelTrace(Long modelConfigId) {
        if (modelConfigId == null) {
            return ModelTraceInfo.empty();
        }
        try {
            ModelConfigResp model = modelConfigService.getById(modelConfigId);
            if (model == null) {
                return ModelTraceInfo.empty();
            }
            ProviderDetailResp detail = providerService.getById(model.getProviderId());
            ProviderResp provider = detail == null ? null : detail.getProvider();
            return new ModelTraceInfo(
                    model.getProviderId(),
                    provider == null ? null : provider.getName(),
                    provider == null ? null : provider.getType(),
                    model.getModelId()
            );
        } catch (Exception e) {
            log.warn("resolve model trace failed modelConfigId={} message={}", modelConfigId, e.getMessage());
            return ModelTraceInfo.empty();
        }
    }

    private record ModelTraceInfo(Long providerId, String providerName, String providerType, String modelId) {
        private static ModelTraceInfo empty() {
            return new ModelTraceInfo(null, null, null, null);
        }
    }

    private record ConversationContext(List<ChatMessage> messages, ChatSessionSummaryPo summary) {
    }

    @SuppressWarnings("unchecked")
    private ChatMessage toApiMessage(ChatMessagePo po) {
        List<ToolCall> toolCalls = null;
        if (po.getToolCalls() != null && !po.getToolCalls().isEmpty()) {
            toolCalls = po.getToolCalls().stream()
                    .map(tc -> {
                        Map<String, Object> fn = (Map<String, Object>) tc.get("function");
                        return ToolCall.builder()
                                .id((String) tc.get("id"))
                                .functionName((String) fn.get("name"))
                                .functionArguments((String) fn.get("arguments"))
                                .build();
                    })
                    .collect(Collectors.toList());
        }
        String content = po.getContent();
        return ChatMessage.builder()
                .role(po.getRole())
                .content(content == null || content.isBlank() ? null : content)
                .toolCalls(toolCalls)
                .toolCallId(po.getToolCallId())
                .build();
    }

    private ConversationSessionResp toSessionResp(ChatSessionPo po) {
        ConversationSessionResp resp = new ConversationSessionResp();
        resp.setId(po.getId());
        resp.setAgentId(po.getAgentId());
        resp.setTitle(po.getTitle());
        resp.setMessageCount(po.getMessageCount());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setLastMessageAt(po.getLastMessageAt());
        return resp;
    }

    private ConversationMessageResp toMessageResp(ChatMessagePo po) {
        ConversationMessageResp resp = new ConversationMessageResp();
        resp.setId(po.getId());
        resp.setSessionId(po.getSessionId());
        resp.setTraceId(po.getTraceId());
        resp.setRole(po.getRole());
        resp.setContent(po.getContent());
        resp.setStatus(po.getStatus());
        resp.setErrorCode(po.getErrorCode());
        resp.setErrorMessage(po.getErrorMessage());
        resp.setPartial(po.getPartial());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private static List<Map<String, Object>> toStoredToolCalls(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) return null;
        return toolCalls.stream()
                .map(tc -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id",   tc.getId());
                    m.put("type", "function");
                    m.put("function", Map.of(
                            "name",      tc.getFunctionName(),
                            "arguments", tc.getFunctionArguments()
                    ));
                    return m;
                })
                .collect(Collectors.toList());
    }

    // ─── SSE 事件构造──────────────────────────────────────────────────────────

    private String tokenEvent(String token) {
        return toJson(Map.of("type", "token", "content", token,
                "traceId", blankToEmpty(TraceContext.currentTraceId())));
    }

    private String workflowStartEvent(Long workflowRunId, Long workflowId) {
        return toJson(Map.of("type", "workflow_start",
                "workflowRunId", workflowRunId,
                "workflowId", workflowId,
                "traceId", blankToEmpty(TraceContext.currentTraceId())));
    }

    private String doneEvent(Long sessionId, Long messageId, ChatResponse response) {
        Map<String, Object> ev = new HashMap<>();
        ev.put("type",         "done");
        ev.put("sessionId",    sessionId);
        ev.put("messageId",    messageId);
        ev.put("finishReason", response.getFinishReason());
        ev.put("inputTokens",  response.getInputTokens());
        ev.put("outputTokens", response.getOutputTokens());
        ev.put("traceId",      blankToEmpty(TraceContext.currentTraceId()));
        return toJson(ev);
    }

    private String errorEvent(int code, String message, Long messageId) {
        return toJson(Map.of("type", "error", "code", code, "message", message,
                "messageId", messageId == null ? 0L : messageId,
                "traceId", blankToEmpty(TraceContext.currentTraceId())));
    }

    private void trySend(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
        } catch (Exception ignored) {}
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
