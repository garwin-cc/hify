package com.hify.agent.domain;

import com.hify.agent.api.AgentWorkflowResourceService;
import com.hify.agent.api.UpdateAgentReq;
import com.hify.agent.infra.*;
import com.hify.common.exception.BizException;
import com.hify.knowledge.api.KnowledgeBaseResp;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.model.api.ModelConfigService;
import com.hify.mcp.api.McpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentResourcePermissionTest {

    @Mock private AgentMapper agentMapper;
    @Mock private AgentToolMapper agentToolMapper;
    @Mock private AgentVersionMapper agentVersionMapper;
    @Mock private AgentAppMapper agentAppMapper;
    @Mock private AgentApiKeyMapper agentApiKeyMapper;
    @Mock private ModelConfigService modelConfigService;
    @Mock private McpService mcpService;
    @Mock private KnowledgeService knowledgeService;
    @Mock private AgentWorkflowResourceService workflowResourceService;

    private AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentServiceImpl(agentMapper, agentToolMapper, agentVersionMapper,
                agentAppMapper, agentApiKeyMapper, modelConfigService, mcpService, knowledgeService);
        service.setWorkflowResourceService(workflowResourceService);
    }

    @Test
    void updateRejectsCrossProjectKnowledgeWithoutPermissionService() {
        when(agentMapper.selectById(100L)).thenReturn(agentPo());
        KnowledgeBaseResp kb = new KnowledgeBaseResp();
        kb.setId(20L);
        kb.setEnabled(1);
        kb.setProjectId(2L);
        when(knowledgeService.getKnowledgeBase(20L)).thenReturn(kb);
        when(agentToolMapper.selectList(any())).thenReturn(List.of());

        UpdateAgentReq req = new UpdateAgentReq();
        req.setKnowledgeBaseIds(List.of(20L));

        assertThatThrownBy(() -> service.update(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("跨项目");
    }

    private static AgentPo agentPo() {
        AgentPo po = new AgentPo();
        po.setId(100L);
        po.setWorkspaceId(1L);
        po.setProjectId(1L);
        po.setName("assistant");
        po.setDescription("");
        po.setSystemPrompt("prompt");
        po.setModelConfigId(10L);
        po.setKnowledgeBaseIds(List.of());
        po.setMemoryEnabled(0);
        po.setSummaryTriggerMessageCount(20);
        po.setSummaryMaxTokens(800);
        po.setEnabled(1);
        po.setDraftVersionNo(1);
        po.setPublishStatus("DRAFT");
        po.setMaxToolRounds(2);
        return po;
    }
}
