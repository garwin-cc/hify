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
import com.hify.conversation.infra.ChatMessageMapper;
import com.hify.conversation.infra.ChatMessagePo;
import com.hify.conversation.infra.ChatSessionMapper;
import com.hify.conversation.infra.ChatSessionPo;
import com.hify.model.api.ChatMessage;
import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.api.ChatStreamCallback;
import com.hify.model.api.EmbeddingService;
import com.hify.model.api.LlmCallService;
import com.hify.model.api.ToolCall;
import com.hify.mcp.api.McpClientService;
import com.hify.mcp.api.McpService;
import com.hify.mcp.api.McpToolCallAuditRecord;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private final AgentService      agentService;
    private final LlmCallService    llmCallService;
    private final KnowledgeService  knowledgeService;
    private final EmbeddingService  embeddingService;
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
                                .eq(ChatMessagePo::getStatus, "DONE")
                                .orderByAsc(ChatMessagePo::getId))
                .stream()
                .map(this::toMessageResp)
                .collect(Collectors.toList());
    }

    /**
     * 不加 @Transactional：此方法立即返回 SseEmitter，LLM 调用在异步线程内完成。
     * 加 @Transactional 会在整个流式过程中持有 DB 连接（最长 130s），耗尽连接池。
     * 各写操作均为单条 SQL，MySQL 自动提交，无需显式事务边界。
     */
    @Override
    public SseEmitter sendMessage(Long agentId, Long sessionId, String content) {
        AgentDetailResp agent = loadAgent(agentId);

        ChatSessionPo session = resolveSession(agentId, sessionId, content);

        // 持久化用户消息（状态直接 DONE，用户消息无需 STREAMING 过渡）
        Long userMsgId = insertMessage(session.getId(), "user", content, null, null, "DONE", null, null);

        // 加载历史（id < userMsgId 的已完成消息），构造上下文列表
        List<ChatMessage> contextMessages = buildContextMessages(session.getId(), userMsgId, content,
                agent.getMaxContextTurns());

        // 创建助手消息占位符，后续流式填充
        Long assistantMsgId = insertMessage(session.getId(), "assistant", "", null, null, "PENDING", null, null);
        log.info("chat request accepted agentId={} sessionId={} userMsgId={} assistantMsgId={} contentLength={}",
                agentId, session.getId(), userMsgId, assistantMsgId, content == null ? 0 : content.length());

        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        AtomicBoolean cancelled = new AtomicBoolean(false);

        // SseEmitter 自身超时：OkHttp 未及时抛出 TIMEOUT 时的最后兜底
        emitter.onTimeout(() -> {
            log.warn("SSE emitter timeout sessionId={} msgId={}", session.getId(), assistantMsgId);
            cancelled.set(true);
            markMessageError(assistantMsgId);
            incrementSessionCount(session.getId(), 1); // 只统计用户消息
            trySend(emitter, errorEvent(ErrorCode.LLM_TIMEOUT.getCode(), ErrorCode.LLM_TIMEOUT.getMessage()));
            emitter.complete();
        });

        emitter.onError(ex ->
            log.debug("SSE emitter error sessionId={} msgId={}: {}", session.getId(), assistantMsgId, ex.getMessage())
        );

        llmExecutor.execute(TraceContext.wrap(() ->
            doStream(emitter, cancelled, agent, session, assistantMsgId, contextMessages)
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

    private void doStream(SseEmitter emitter, AtomicBoolean cancelled,
                          AgentDetailResp agent, ChatSessionPo session,
                          Long assistantMsgId, List<ChatMessage> contextMessages) {

        updateMessageStatus(assistantMsgId, "STREAMING");

        StringBuilder fullContent = new StringBuilder();
        long          streamStart = System.currentTimeMillis();
        log.info("chat stream start agentId={} sessionId={} assistantMsgId={} workflowId={} modelConfigId={}",
                agent.getId(), session.getId(), assistantMsgId, agent.getWorkflowId(), agent.getModelConfigId());

        if (agent.getWorkflowId() != null) {
            try {
                String userContent = latestUserContent(contextMessages);
                WorkflowRunReq runReq = new WorkflowRunReq();
                runReq.setUserMessage(userContent);
                WorkflowRunResp workflowRun = workflowService.startAsyncRun(agent.getWorkflowId(), runReq);
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
                incrementSessionCount(session.getId(), 2);
                hifyMetrics.recordChatRequest(agent.getId(), "success", latency);
                if (!cancelled.get()) {
                    try {
                        emitter.send(SseEmitter.event()
                                .data(doneEvent(session.getId(), assistantMsgId, response)));
                        emitter.complete();
                    } catch (IOException e) {
                        log.debug("SSE workflow done send failed (client gone) msgId={}", assistantMsgId);
                        emitter.complete();
                    } catch (Exception e) {
                        log.warn("SSE workflow done send failed msgId={}: {}", assistantMsgId, e.getMessage());
                        emitter.complete();
                    }
                }
            } catch (BizException e) {
                log.warn("workflow execute failed workflowId={} msgId={}: {}",
                        agent.getWorkflowId(), assistantMsgId, e.getMessage());
                markMessageError(assistantMsgId);
                incrementSessionCount(session.getId(), 1);
                hifyMetrics.recordChatRequest(agent.getId(), "failure", System.currentTimeMillis() - streamStart);
                if (!cancelled.get()) {
                    trySend(emitter, errorEvent(e.getCode(), e.getMessage()));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("workflow execute unexpected error workflowId={} msgId={}",
                        agent.getWorkflowId(), assistantMsgId, e);
                markMessageError(assistantMsgId);
                incrementSessionCount(session.getId(), 1);
                hifyMetrics.recordChatRequest(agent.getId(), "failure", System.currentTimeMillis() - streamStart);
                if (!cancelled.get()) {
                    trySend(emitter, errorEvent(ErrorCode.WORKFLOW_EXECUTE_FAILED.getCode(),
                            ErrorCode.WORKFLOW_EXECUTE_FAILED.getMessage()));
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
            log.info("chat tools loaded agentId={} toolIds={} boundTools={}",
                    agent.getId(), agent.getToolIds(), boundTools.size());

            ChatRequest request = ChatRequest.builder()
                    .systemPrompt(buildSystemPrompt(agent, contextMessages))
                    .messages(contextMessages)
                    .tools(toolSchemas.isEmpty() ? null : toolSchemas)
                    .temperature(agent.getTemperature())
                    .maxTokens(agent.getMaxTokens())
                    .build();

            llmCallService.streamChat(agent.getModelConfigId(), request, new ChatStreamCallback() {

                @Override
                public void onToken(String token) {
                    if (cancelled.get()) return;
                    fullContent.append(token);
                    if (!toolSchemas.isEmpty()) {
                        return;
                    }
                    try {
                        emitter.send(SseEmitter.event().data(tokenEvent(token)));
                    } catch (IOException e) {
                        // 客户端主动断开，停止写入但不中断 LLM 流（等待 onComplete 后更新 DB）
                        log.debug("SSE client disconnected msgId={}", assistantMsgId);
                        cancelled.set(true);
                    } catch (Exception e) {
                        log.warn("SSE send failed msgId={}: {}", assistantMsgId, e.getMessage());
                        cancelled.set(true);
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onComplete(ChatResponse response) {
                    log.info("chat first llm complete agentId={} sessionId={} assistantMsgId={} finishReason={} outputTokens={}",
                            agent.getId(), session.getId(), assistantMsgId,
                            response.getFinishReason(), response.getOutputTokens());
                    if (!toolSchemas.isEmpty() && isToolCallResponse(response)) {
                        streamAfterToolCalls(emitter, cancelled, agent, session, assistantMsgId,
                                contextMessages, request, response, toolMap, fullContent, streamStart, 1);
                        return;
                    }
                    if (!toolSchemas.isEmpty()) {
                        ChatResponse inlineToolResponse = inlineToolCallResponse(response, fullContent.toString());
                        if (isToolCallResponse(inlineToolResponse)) {
                            streamAfterToolCalls(emitter, cancelled, agent, session, assistantMsgId,
                                    contextMessages, request, inlineToolResponse, toolMap, fullContent, streamStart, 1);
                            return;
                        }
                        sendBufferedContent(emitter, cancelled, assistantMsgId, fullContent.toString());
                    }
                    int latency = (int) (System.currentTimeMillis() - streamStart);
                    completeAssistantStream(emitter, cancelled, session.getId(), assistantMsgId,
                            agent.getId(), fullContent.toString(), response, latency);
                }

                @Override
                public void onError(LlmApiException e) {
                    log.warn("chat first llm error agentId={} sessionId={} assistantMsgId={} type={} status={} message={}",
                            agent.getId(), session.getId(), assistantMsgId,
                            e.getType(), e.getStatusCode(), e.getMessage());
                    handleLlmStreamError(emitter, cancelled, agent.getId(), session.getId(), assistantMsgId,
                            streamStart, e);
                }
            });

        } catch (LlmApiException e) {
            handleLlmStreamError(emitter, cancelled, agent.getId(), session.getId(), assistantMsgId,
                    streamStart, e);
        } catch (Exception e) {
            log.error("doStream unexpected error msgId={}", assistantMsgId, e);
            markMessageError(assistantMsgId);
            incrementSessionCount(session.getId(), 1);
            hifyMetrics.recordChatRequest(agent.getId(), "failure", System.currentTimeMillis() - streamStart);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    private void handleLlmStreamError(SseEmitter emitter, AtomicBoolean cancelled,
                                      Long agentId, Long sessionId, Long assistantMsgId,
                                      long streamStart, LlmApiException e) {
        markMessageError(assistantMsgId);
        incrementSessionCount(sessionId, 1);
        hifyMetrics.recordChatRequest(agentId, "failure", System.currentTimeMillis() - streamStart);
        if (cancelled.get()) return;
        log.warn("chat stream error sessionId={} assistantMsgId={} type={} status={} message={}",
                sessionId, assistantMsgId, e.getType(), e.getStatusCode(), e.getMessage());

        int code = switch (e.getType()) {
            case TIMEOUT -> ErrorCode.LLM_TIMEOUT.getCode();
            case RATE_LIMITED -> ErrorCode.TOO_MANY_REQUESTS.getCode();
            default -> ErrorCode.LLM_CALL_ERROR.getCode();
        };
        trySend(emitter, errorEvent(code, e.getMessage()));
        emitter.complete();
    }

    private void streamAfterToolCalls(SseEmitter emitter, AtomicBoolean cancelled,
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
            String toolResult = executeToolCall(toolMap, toolCall, session.getId(), assistantMsgId);
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
                        streamAfterToolCalls(emitter, cancelled, agent, session, assistantMsgId,
                                contextMessages, secondRequest, nextToolResponse, toolMap,
                                fullContent, streamStart, toolRound + 1);
                        return;
                    }
                    sendBufferedContent(emitter, cancelled, assistantMsgId, fullContent.toString());
                    int latency = (int) (System.currentTimeMillis() - streamStart);
                    completeAssistantStream(emitter, cancelled, session.getId(), assistantMsgId,
                            agent.getId(), fullContent.toString(), response, latency);
                }

                @Override
                public void onError(LlmApiException e) {
                    log.warn("chat tool round llm error agentId={} sessionId={} assistantMsgId={} round={} type={} status={} message={}",
                            agent.getId(), session.getId(), assistantMsgId, toolRound,
                            e.getType(), e.getStatusCode(), e.getMessage());
                    handleLlmStreamError(emitter, cancelled, agent.getId(), session.getId(), assistantMsgId,
                            streamStart, e);
                }
            });
        } catch (LlmApiException e) {
            handleLlmStreamError(emitter, cancelled, agent.getId(), session.getId(), assistantMsgId,
                    streamStart, e);
        } catch (Exception e) {
            log.error("tool result stream unexpected error msgId={}", assistantMsgId, e);
            markMessageError(assistantMsgId);
            incrementSessionCount(session.getId(), 1);
            hifyMetrics.recordChatRequest(agent.getId(), "failure", System.currentTimeMillis() - streamStart);
            try { emitter.completeWithError(e); } catch (Exception ignored) {}
        }
    }

    private void completeAssistantStream(SseEmitter emitter, AtomicBoolean cancelled,
                                         Long sessionId, Long assistantMsgId,
                                         Long agentId, String content, ChatResponse response, int latencyMs) {
        // 无论客户端是否已断开，都将最终结果写库
        finalizeMessage(assistantMsgId, content, response, latencyMs);
        incrementSessionCount(sessionId, 2);
        hifyMetrics.recordChatRequest(agentId, "success", latencyMs);

        if (cancelled.get()) return;
        try {
            emitter.send(SseEmitter.event()
                    .data(doneEvent(sessionId, assistantMsgId, response)));
            emitter.complete();
            log.info("chat stream done sessionId={} assistantMsgId={} finishReason={} latencyMs={} inputTokens={} outputTokens={}",
                    sessionId, assistantMsgId, response.getFinishReason(), latencyMs,
                    response.getInputTokens(), response.getOutputTokens());
        } catch (IOException e) {
            log.debug("SSE done send failed (client gone) msgId={}", assistantMsgId);
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

    private String executeToolCall(Map<String, McpToolResp> toolMap, ToolCall toolCall,
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
            recordToolAudit("CONVERSATION", sessionId, assistantMsgId, null, null,
                    tool, arguments, System.currentTimeMillis() - start, true, result, null);
            return result;
        } catch (Exception e) {
            log.warn("mcp tool call failed toolId={} serverId={} name={} elapsedMs={} message={}",
                    tool.getId(), tool.getMcpServerId(), tool.getName(),
                    System.currentTimeMillis() - start, e.getMessage());
            recordToolAudit("CONVERSATION", sessionId, assistantMsgId, null, null,
                    tool, arguments, System.currentTimeMillis() - start, false, null, e.getMessage());
            return "工具调用失败: " + e.getMessage();
        }
    }

    private void recordToolAudit(String sourceType, Long sessionId, Long messageId,
                                 Long workflowRunId, String workflowNodeKey,
                                 McpToolResp tool, Map<String, Object> arguments,
                                 long elapsedMs, boolean success, String result, String error) {
        try {
            mcpToolCallAuditService.record(McpToolCallAuditRecord.builder()
                    .sourceType(sourceType)
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

    private Long insertMessage(Long sessionId, String role, String content,
                               List<Map<String, Object>> toolCalls, String toolCallId,
                               String status, String finishReason, Integer latencyMs) {
        ChatMessagePo po = new ChatMessagePo();
        po.setSessionId(sessionId);
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
                .set(ChatMessagePo::getToolCalls,    toStoredToolCalls(response.getToolCalls())));
    }

    private void markMessageError(Long msgId) {
        messageMapper.update(null, Wrappers.lambdaUpdate(ChatMessagePo.class)
                .eq(ChatMessagePo::getId, msgId)
                .set(ChatMessagePo::getStatus, "ERROR")
                .set(ChatMessagePo::getFinishReason, "error"));
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

    private String buildSystemPrompt(AgentDetailResp agent, List<ChatMessage> messages) {
        String systemPrompt = agent.getSystemPrompt() == null ? "" : agent.getSystemPrompt();
        List<Long> knowledgeBaseIds = agent.getKnowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return systemPrompt;
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
            return systemPrompt;
        }

        KnowledgeSearchReq req = new KnowledgeSearchReq();
        req.setKnowledgeBaseIds(knowledgeBaseIds);
        req.setQueryText(userMessage);
        req.setSourceType("CONVERSATION");
        req.setSourceId("agent:" + agent.getId());
        req.setIncludeTrace(true);
        List<KnowledgeSearchResp> chunks = knowledgeService.searchSimilar(req);
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
            return systemPrompt;
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
        return builder.toString().trim();
    }

    private static String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
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
        resp.setRole(po.getRole());
        resp.setContent(po.getContent());
        resp.setStatus(po.getStatus());
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
        return toJson(Map.of("type", "token", "content", token));
    }

    private String workflowStartEvent(Long workflowRunId, Long workflowId) {
        return toJson(Map.of("type", "workflow_start",
                "workflowRunId", workflowRunId,
                "workflowId", workflowId));
    }

    private String doneEvent(Long sessionId, Long messageId, ChatResponse response) {
        Map<String, Object> ev = new HashMap<>();
        ev.put("type",         "done");
        ev.put("sessionId",    sessionId);
        ev.put("messageId",    messageId);
        ev.put("finishReason", response.getFinishReason());
        ev.put("inputTokens",  response.getInputTokens());
        ev.put("outputTokens", response.getOutputTokens());
        return toJson(ev);
    }

    private String errorEvent(int code, String message) {
        return toJson(Map.of("type", "error", "code", code, "message", message));
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
}
