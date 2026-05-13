package com.hify.conversation.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.agent.api.AgentService;
import com.hify.conversation.api.ConversationMessageResp;
import com.hify.conversation.api.ConversationSessionResp;
import com.hify.conversation.infra.ChatMessageMapper;
import com.hify.conversation.infra.ChatMessagePo;
import com.hify.conversation.infra.ChatSessionMapper;
import com.hify.conversation.infra.ChatSessionPo;
import com.hify.conversation.infra.ChatSessionSummaryMapper;
import com.hify.conversation.infra.ChatSessionSummaryPo;
import com.hify.model.api.ChatMessage;
import com.hify.model.api.LlmCallService;
import com.hify.mcp.api.McpToolCallAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private ChatSessionSummaryMapper summaryMapper;

    @Mock
    private AgentService agentService;

    @Mock
    private LlmCallService llmCallService;

    @Mock
    private ThreadPoolExecutor llmExecutor;

    @Mock
    private McpToolCallAuditService mcpToolCallAuditService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
}
