package com.hify.conversation.domain;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.agent.api.AgentDetailResp;
import com.hify.agent.api.AgentApiKeyAuthResp;
import com.hify.agent.api.AgentService;
import com.hify.common.metrics.HifyMetrics;
import com.hify.common.ratelimit.RateLimitDimension;
import com.hify.common.ratelimit.RateLimitQuotaService;
import com.hify.common.ratelimit.RateLimitResult;
import com.hify.common.ratelimit.RateLimitRule;
import com.hify.common.ratelimit.RateLimitService;
import com.hify.conversation.api.ConversationLogQuery;
import com.hify.conversation.api.ConversationLogResp;
import com.hify.conversation.api.ConversationMessageCursorQuery;
import com.hify.conversation.api.ConversationMessageResp;
import com.hify.conversation.api.ConversationSessionCursorQuery;
import com.hify.conversation.api.ConversationSessionResp;
import com.hify.conversation.api.CursorPageResp;
import com.hify.conversation.api.MessageFeedbackReq;
import com.hify.conversation.api.MessageFeedbackResp;
import com.hify.conversation.infra.ChatMessageMapper;
import com.hify.conversation.infra.ChatMessagePo;
import com.hify.conversation.infra.ChatSessionMapper;
import com.hify.conversation.infra.ChatSessionPo;
import com.hify.conversation.infra.ChatSessionSummaryMapper;
import com.hify.conversation.infra.ChatSessionSummaryPo;
import com.hify.conversation.infra.ConversationTraceMapper;
import com.hify.conversation.infra.ConversationTracePo;
import com.hify.conversation.infra.MessageFeedbackMapper;
import com.hify.conversation.infra.MessageFeedbackPo;
import com.hify.model.api.ChatMessage;
import com.hify.model.api.LlmCallService;
import com.hify.mcp.api.McpToolCallAuditService;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ChatMessagePo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ChatSessionPo.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ConversationTracePo.class);
    }

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private ChatSessionSummaryMapper summaryMapper;

    @Mock
    private ConversationTraceMapper conversationTraceMapper;

    @Mock
    private MessageFeedbackMapper messageFeedbackMapper;

    @Mock
    private AgentService agentService;

    @Mock
    private LlmCallService llmCallService;

    @Mock
    private WorkflowService workflowService;

    @Mock
    private RateLimitService rateLimitService;

    @Mock
    private RateLimitQuotaService rateLimitQuotaService;

    @Mock
    private HifyMetrics hifyMetrics;

    @Mock
    private ThreadPoolExecutor llmExecutor;

    @Mock
    private McpToolCallAuditService mcpToolCallAuditService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ConversationServiceImpl conversationService;

    @Test
    void listSessionsReturnsPersistedSessionsForAgent() {
        ChatSessionPo first = new ChatSessionPo();
        first.setId(11L);
        first.setAgentId(3L);
        first.setTitle("dna是什么");
        first.setMessageCount(2);
        first.setCreatedAt(LocalDateTime.of(2026, 5, 8, 10, 0));
        first.setLastMessageAt(LocalDateTime.of(2026, 5, 8, 10, 1));

        ChatSessionPo second = new ChatSessionPo();
        second.setId(12L);
        second.setAgentId(3L);
        second.setTitle("heelo");
        second.setMessageCount(1);
        second.setCreatedAt(LocalDateTime.of(2026, 5, 8, 11, 0));
        when(sessionMapper.selectList(any())).thenReturn(List.of(first, second));

        List<ConversationSessionResp> sessions = conversationService.listSessions(3L);

        assertThat(sessions)
                .extracting(ConversationSessionResp::getId, ConversationSessionResp::getAgentId,
                        ConversationSessionResp::getTitle, ConversationSessionResp::getMessageCount)
                .containsExactly(
                        tuple(11L, 3L, "dna是什么", 2),
                        tuple(12L, 3L, "heelo", 1)
                );
    }

    @Test
    void listMessagesReturnsDoneMessagesInConversationOrder() {
        ChatSessionPo session = new ChatSessionPo();
        session.setId(11L);
        session.setAgentId(3L);
        when(sessionMapper.selectById(11L)).thenReturn(session);

        ChatMessagePo user = new ChatMessagePo();
        user.setId(101L);
        user.setSessionId(11L);
        user.setRole("user");
        user.setContent("dna是什么");
        user.setStatus("DONE");
        user.setCreatedAt(LocalDateTime.of(2026, 5, 8, 10, 0));

        ChatMessagePo assistant = new ChatMessagePo();
        assistant.setId(102L);
        assistant.setSessionId(11L);
        assistant.setRole("assistant");
        assistant.setContent("DNA 是脱氧核糖核酸。");
        assistant.setStatus("DONE");
        assistant.setCreatedAt(LocalDateTime.of(2026, 5, 8, 10, 1));
        when(messageMapper.selectList(any())).thenReturn(List.of(user, assistant));

        List<ConversationMessageResp> messages = conversationService.listMessages(11L);

        assertThat(messages)
                .extracting(ConversationMessageResp::getId, ConversationMessageResp::getRole,
                        ConversationMessageResp::getContent)
                .containsExactly(
                        tuple(101L, "user", "dna是什么"),
                        tuple(102L, "assistant", "DNA 是脱氧核糖核酸。")
                );
    }

    @Test
    void listSessionsCursorReturnsCursorMetadataAndNewIdentityFields() {
        LocalDateTime firstTime = LocalDateTime.of(2026, 5, 14, 10, 2);
        LocalDateTime secondTime = LocalDateTime.of(2026, 5, 14, 10, 1);
        ChatSessionPo first = session(21L, firstTime);
        first.setUserId(5L);
        first.setAppId(6L);
        first.setApiKeyId(7L);
        ChatSessionPo second = session(20L, secondTime);
        ChatSessionPo extra = session(19L, LocalDateTime.of(2026, 5, 14, 10, 0));
        when(sessionMapper.selectList(any())).thenReturn(List.of(first, second, extra));

        ConversationSessionCursorQuery query = new ConversationSessionCursorQuery();
        query.setAgentId(3L);
        query.setLimit(2);

        CursorPageResp<ConversationSessionResp> page = conversationService.listSessionsCursor(query);

        assertThat(page.getHasMore()).isTrue();
        assertThat(page.getNextCursorId()).isEqualTo(20L);
        assertThat(page.getNextCursorTime()).isEqualTo(secondTime);
        assertThat(page.getRecords())
                .extracting(ConversationSessionResp::getId, ConversationSessionResp::getUserId,
                        ConversationSessionResp::getAppId, ConversationSessionResp::getApiKeyId)
                .containsExactly(
                        tuple(21L, 5L, 6L, 7L),
                        tuple(20L, 0L, null, null)
                );
    }

    @Test
    void listMessagesCursorRequiresSessionAndReturnsNextCursor() {
        ChatSessionPo session = new ChatSessionPo();
        session.setId(11L);
        session.setAgentId(3L);
        when(sessionMapper.selectById(11L)).thenReturn(session);

        LocalDateTime firstTime = LocalDateTime.of(2026, 5, 14, 10, 0);
        LocalDateTime secondTime = LocalDateTime.of(2026, 5, 14, 10, 1);
        ChatMessagePo first = message(101L, "user", "你好", firstTime);
        ChatMessagePo second = message(102L, "assistant", "你好，有什么可以帮你", secondTime);
        ChatMessagePo extra = message(103L, "user", "继续", LocalDateTime.of(2026, 5, 14, 10, 2));
        when(messageMapper.selectList(any())).thenReturn(List.of(first, second, extra));

        ConversationMessageCursorQuery query = new ConversationMessageCursorQuery();
        query.setLimit(2);

        CursorPageResp<ConversationMessageResp> page = conversationService.listMessagesCursor(11L, query);

        assertThat(page.getHasMore()).isTrue();
        assertThat(page.getNextCursorId()).isEqualTo(102L);
        assertThat(page.getNextCursorTime()).isEqualTo(secondTime);
        assertThat(page.getRecords())
                .extracting(ConversationMessageResp::getId, ConversationMessageResp::getRole,
                        ConversationMessageResp::getContent)
                .containsExactly(
                        tuple(101L, "user", "你好"),
                        tuple(102L, "assistant", "你好，有什么可以帮你")
                );
    }

    @Test
    void listConversationLogsMapsTraceFieldsForLogCenter() {
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 14, 10, 0);
        ConversationTracePo trace = new ConversationTracePo();
        trace.setId(31L);
        trace.setTraceId("trace-1");
        trace.setSessionId(11L);
        trace.setUserId(5L);
        trace.setAppId(6L);
        trace.setApiKeyId(7L);
        trace.setAgentId(3L);
        trace.setAgentName("客服助手");
        trace.setModelConfigId(9L);
        trace.setModelId("gpt-4o");
        trace.setRagTriggered(1);
        trace.setMcpTriggered(0);
        trace.setSummaryUsed(1);
        trace.setStatus("DONE");
        trace.setStartedAt(startedAt);
        when(conversationTraceMapper.selectList(any())).thenReturn(List.of(trace));

        ConversationLogQuery query = new ConversationLogQuery();
        query.setUserId(5L);
        query.setAgentId(3L);

        CursorPageResp<ConversationLogResp> page = conversationService.listConversationLogs(query);

        assertThat(page.getHasMore()).isFalse();
        assertThat(page.getNextCursorId()).isEqualTo(31L);
        assertThat(page.getNextCursorTime()).isEqualTo(startedAt);
        assertThat(page.getRecords()).singleElement()
                .satisfies(resp -> {
                    assertThat(resp.getTraceId()).isEqualTo("trace-1");
                    assertThat(resp.getUserId()).isEqualTo(5L);
                    assertThat(resp.getAppId()).isEqualTo(6L);
                    assertThat(resp.getApiKeyId()).isEqualTo(7L);
                    assertThat(resp.getRagTriggered()).isTrue();
                    assertThat(resp.getMcpTriggered()).isFalse();
                    assertThat(resp.getSummaryUsed()).isTrue();
                });
    }

    @Test
    void upsertFeedbackCreatesFeedbackForAssistantMessage() {
        ChatMessagePo assistant = message(102L, "assistant", "原始回答", LocalDateTime.of(2026, 5, 14, 10, 1));
        when(messageMapper.selectById(102L)).thenReturn(assistant);
        ChatSessionPo session = session(11L, LocalDateTime.of(2026, 5, 14, 10, 1));
        session.setAgentId(3L);
        when(sessionMapper.selectById(11L)).thenReturn(session);
        when(messageFeedbackMapper.selectOne(any())).thenReturn(null);

        MessageFeedbackReq req = new MessageFeedbackReq();
        req.setUserId(5L);
        req.setRating("dislike");
        req.setIssueType("wrong_fact");
        req.setComment("答案不准确");
        req.setCorrectedAnswer("修正后的答案");

        MessageFeedbackResp resp = conversationService.upsertFeedback(102L, req);

        assertThat(resp.getMessageId()).isEqualTo(102L);
        assertThat(resp.getSessionId()).isEqualTo(11L);
        assertThat(resp.getAgentId()).isEqualTo(3L);
        assertThat(resp.getUserId()).isEqualTo(5L);
        assertThat(resp.getRating()).isEqualTo("DISLIKE");
        assertThat(resp.getCorrectedAnswer()).isEqualTo("修正后的答案");
        verify(messageFeedbackMapper).insert(any(MessageFeedbackPo.class));
        verify(messageFeedbackMapper, never()).updateById(any(MessageFeedbackPo.class));
    }

    @Test
    void sendMessageReturnsControlledErrorWhenRateLimited() {
        AgentDetailResp agent = enabledAgent(3L);
        when(agentService.getDetail(3L)).thenReturn(agent);
        when(rateLimitService.check(any())).thenReturn(RateLimitResult.rejected(3));
        conversationService.setRateLimitService(rateLimitService);

        conversationService.sendMessage(3L, null, "你好", 5L, 6L, 7L);

        verify(sessionMapper, never()).insert(any(ChatSessionPo.class));
        verify(messageMapper, never()).insert(any(ChatMessagePo.class));
        verify(llmExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void sendMessageUsesQuotaResolverForAgentLimit() {
        AgentDetailResp agent = enabledAgent(3L);
        RateLimitRule agentRule = RateLimitRule.builder()
                .dimension(RateLimitDimension.AGENT)
                .key("3")
                .limit(5)
                .window(Duration.ofSeconds(10))
                .failOpen(false)
                .build();
        when(agentService.getDetail(3L)).thenReturn(agent);
        when(rateLimitQuotaService.resolve(eq(RateLimitDimension.AGENT), eq("3"), eq(120),
                any(Duration.class), eq(true))).thenReturn(agentRule);
        when(rateLimitService.check(agentRule)).thenReturn(RateLimitResult.rejected(10));
        conversationService.setRateLimitService(rateLimitService);
        conversationService.setRateLimitQuotaService(rateLimitQuotaService);

        conversationService.sendMessage(3L, null, "你好", 5L, 6L, 7L);

        verify(rateLimitService).check(agentRule);
        verify(sessionMapper, never()).insert(any(ChatSessionPo.class));
        verify(messageMapper, never()).insert(any(ChatMessagePo.class));
    }

    @Test
    void should_authenticate_api_key_and_use_published_snapshot_when_public_stream_called() {
        AgentDetailResp publishedAgent = enabledAgent(3L);
        publishedAgent.setSystemPrompt("published prompt");
        publishedAgent.setPublishedVersionId(200L);
        publishedAgent.setDraftVersionNo(3);
        AgentApiKeyAuthResp auth = new AgentApiKeyAuthResp();
        auth.setAgentId(3L);
        auth.setAppId(6L);
        auth.setApiKeyId(7L);
        auth.setAgentVersionId(200L);
        auth.setAgentVersionNo(3);
        auth.setAgent(publishedAgent);
        when(agentService.authenticateApiKey("/app/support", "hify_test_key")).thenReturn(auth);
        when(rateLimitService.check(any())).thenReturn(RateLimitResult.rejected(3));
        conversationService.setRateLimitService(rateLimitService);

        conversationService.sendMessageByApiKey("/app/support", "hify_test_key", null, "你好");

        verify(agentService, never()).getDetail(3L);
        verify(sessionMapper, never()).insert(any(ChatSessionPo.class));
        verify(messageMapper, never()).insert(any(ChatMessagePo.class));
        verify(llmExecutor, never()).execute(any(Runnable.class));
    }

    @Test
    void sendMessageRunsWorkflowInsteadOfOrdinaryLlmWhenAgentBindsWorkflow() {
        AgentDetailResp agent = enabledAgent(3L);
        agent.setWorkflowId(77L);
        when(agentService.getDetail(3L)).thenReturn(agent);
        ChatSessionPo session = session(11L, LocalDateTime.of(2026, 5, 14, 10, 0));
        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(List.of());
        AtomicLong ids = new AtomicLong(101L);
        doAnswer(invocation -> {
            ChatMessagePo po = invocation.getArgument(0);
            po.setId(ids.getAndIncrement());
            return 1;
        }).when(messageMapper).insert(any(ChatMessagePo.class));
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(llmExecutor).execute(any(Runnable.class));
        WorkflowRunResp run = new WorkflowRunResp();
        run.setId(900L);
        when(workflowService.startAsyncRun(eq(77L), any())).thenReturn(run);

        conversationService.sendMessage(3L, 11L, "启动审批流程", 5L, 6L, 7L);

        verify(workflowService).startAsyncRun(eq(77L), any());
        verify(llmCallService, never()).streamChat(any(), any(), any());
    }

    @Test
    void deleteSessionDeletesSessionAndMessages() {
        ChatSessionPo session = new ChatSessionPo();
        session.setId(11L);
        session.setAgentId(3L);
        when(sessionMapper.selectById(11L)).thenReturn(session);

        conversationService.deleteSession(11L);

        verify(messageMapper).delete(any());
        verify(sessionMapper).deleteById(11L);
    }

    @Test
    void deleteSessionThrowsWhenSessionNotFound() {
        when(sessionMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> conversationService.deleteSession(404L))
                .isInstanceOf(com.hify.common.exception.BizException.class)
                .hasMessageContaining("会话不存在");
        verify(messageMapper, never()).delete(any());
        verify(sessionMapper, never()).deleteById(eq(404L));
    }

    @Test
    void abbreviateDoesNotExceedRequestedLength() throws Exception {
        Method abbreviate = ConversationServiceImpl.class.getDeclaredMethod("abbreviate", String.class, int.class);
        abbreviate.setAccessible(true);
        String content = "右手第四掌骨骨折".repeat(80);

        String preview = (String) abbreviate.invoke(null, content, 512);

        assertThat(preview).hasSizeLessThanOrEqualTo(512);
        assertThat(preview).endsWith("...");
    }

    @Test
    void buildSystemPromptAppendsSummaryAfterBasePrompt() throws Exception {
        Method buildSystemPrompt = ConversationServiceImpl.class.getDeclaredMethod(
                "buildSystemPrompt",
                String.class,
                com.hify.agent.api.AgentDetailResp.class,
                List.class,
                ChatSessionSummaryPo.class);
        buildSystemPrompt.setAccessible(true);
        com.hify.agent.api.AgentDetailResp agent = new com.hify.agent.api.AgentDetailResp();
        agent.setId(7L);
        agent.setSystemPrompt("你是医疗助手");
        agent.setKnowledgeBaseIds(List.of());
        ChatSessionSummaryPo summary = new ChatSessionSummaryPo();
        summary.setSummary("用户目标：持续跟进右手第四掌骨骨折恢复。");

        String prompt = (String) buildSystemPrompt.invoke(nullSafeService(), "trace-1", agent,
                List.of(ChatMessage.builder().role("user").content("现在可以训练吗").build()), summary);

        assertThat(prompt).contains("你是医疗助手");
        assertThat(prompt).contains("【会话摘要 / 记忆】");
        assertThat(prompt).contains("用户目标：持续跟进右手第四掌骨骨折恢复。");
        assertThat(prompt.indexOf("你是医疗助手")).isLessThan(prompt.indexOf("【会话摘要 / 记忆】"));
    }

    private ConversationServiceImpl nullSafeService() {
        return conversationService;
    }

    private static AgentDetailResp enabledAgent(Long id) {
        AgentDetailResp agent = new AgentDetailResp();
        agent.setId(id);
        agent.setName("客服助手");
        agent.setEnabled(1);
        agent.setSystemPrompt("你是客服助手");
        return agent;
    }

    private static ChatSessionPo session(Long id, LocalDateTime lastMessageAt) {
        ChatSessionPo session = new ChatSessionPo();
        session.setId(id);
        session.setAgentId(3L);
        session.setUserId(0L);
        session.setTitle("会话 " + id);
        session.setStatus("ACTIVE");
        session.setMessageCount(2);
        session.setCreatedAt(lastMessageAt.minusMinutes(1));
        session.setLastMessageAt(lastMessageAt);
        return session;
    }

    private static ChatMessagePo message(Long id, String role, String content, LocalDateTime createdAt) {
        ChatMessagePo message = new ChatMessagePo();
        message.setId(id);
        message.setSessionId(11L);
        message.setRole(role);
        message.setContent(content);
        message.setStatus("DONE");
        message.setCreatedAt(createdAt);
        return message;
    }
}
