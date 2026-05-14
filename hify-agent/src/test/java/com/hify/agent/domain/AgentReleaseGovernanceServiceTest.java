package com.hify.agent.domain;

import com.hify.agent.api.*;
import com.hify.agent.infra.*;
import com.hify.common.exception.BizException;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import com.hify.mcp.api.McpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentReleaseGovernanceServiceTest {

    @Mock private AgentMapper agentMapper;
    @Mock private AgentToolMapper agentToolMapper;
    @Mock private AgentVersionMapper agentVersionMapper;
    @Mock private AgentAppMapper agentAppMapper;
    @Mock private AgentApiKeyMapper agentApiKeyMapper;
    @Mock private ModelConfigService modelConfigService;
    @Mock private McpService mcpService;
    @Mock private KnowledgeService knowledgeService;

    private AgentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AgentServiceImpl(agentMapper, agentToolMapper, agentVersionMapper,
                agentAppMapper, agentApiKeyMapper, modelConfigService, mcpService, knowledgeService);
    }

    @Test
    void createCreatesInitialDraftVersion() {
        when(modelConfigService.getById(10L)).thenReturn(modelConfig());
        when(agentMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> {
            AgentPo po = invocation.getArgument(0);
            po.setId(100L);
            return 1;
        }).when(agentMapper).insert(any(AgentPo.class));
        when(agentVersionMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            AgentVersionPo po = invocation.getArgument(0);
            po.setId(200L);
            return 1;
        }).when(agentVersionMapper).insert(any(AgentVersionPo.class));

        AgentDetailResp resp = service.create(createReq());

        assertThat(resp.getId()).isEqualTo(100L);
        assertThat(resp.getPublishStatus()).isEqualTo("DRAFT");
        assertThat(resp.getMaxToolRounds()).isEqualTo(2);
        ArgumentCaptor<AgentVersionPo> versionCaptor = ArgumentCaptor.forClass(AgentVersionPo.class);
        verify(agentVersionMapper).insert(versionCaptor.capture());
        assertThat(versionCaptor.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(versionCaptor.getValue().getVersionNo()).isEqualTo(1);
    }

    @Test
    void publishVersionCreatesPublishedSnapshotAndUpdatesAgent() {
        AgentPo agent = agentPo();
        when(agentMapper.selectById(100L)).thenReturn(agent);
        when(agentToolMapper.selectList(any())).thenReturn(List.of());
        when(agentVersionMapper.selectOne(any())).thenReturn(version(1, "DRAFT"));
        doAnswer(invocation -> {
            AgentVersionPo po = invocation.getArgument(0);
            po.setId(300L);
            return 1;
        }).when(agentVersionMapper).insert(any(AgentVersionPo.class));

        AgentVersionResp resp = service.publishVersion(100L, new AgentPublishReq());

        assertThat(resp.getStatus()).isEqualTo("PUBLISHED");
        assertThat(resp.getVersionNo()).isEqualTo(2);
        assertThat(agent.getPublishedVersionId()).isEqualTo(300L);
        assertThat(agent.getPublishStatus()).isEqualTo("PUBLISHED");
    }

    @Test
    void createAppRejectsNonPublishedVersion() {
        when(agentMapper.selectById(100L)).thenReturn(agentPo());
        AgentVersionPo testVersion = version(2, "TEST");
        testVersion.setId(200L);
        when(agentVersionMapper.selectById(200L)).thenReturn(testVersion);

        AgentAppReq req = new AgentAppReq();
        req.setPublishedVersionId(200L);
        req.setName("internal app");

        assertThatThrownBy(() -> service.createApp(100L, req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已发布");
    }

    private static CreateAgentReq createReq() {
        CreateAgentReq req = new CreateAgentReq();
        req.setName("assistant");
        req.setSystemPrompt("You are helpful.");
        req.setModelConfigId(10L);
        req.setTemperature(new BigDecimal("0.70"));
        return req;
    }

    private static AgentPo agentPo() {
        AgentPo po = new AgentPo();
        po.setId(100L);
        po.setWorkspaceId(1L);
        po.setProjectId(1L);
        po.setName("assistant");
        po.setDescription("");
        po.setSystemPrompt("You are helpful.");
        po.setModelConfigId(10L);
        po.setKnowledgeBaseIds(List.of());
        po.setTemperature(new BigDecimal("0.70"));
        po.setMemoryEnabled(0);
        po.setSummaryTriggerMessageCount(20);
        po.setSummaryMaxTokens(800);
        po.setEnabled(1);
        po.setDraftVersionNo(1);
        po.setPublishStatus("DRAFT");
        po.setMaxToolRounds(2);
        return po;
    }

    private static AgentVersionPo version(Integer versionNo, String status) {
        AgentVersionPo po = new AgentVersionPo();
        po.setId(1000L + versionNo);
        po.setAgentId(100L);
        po.setVersionNo(versionNo);
        po.setStatus(status);
        po.setName("assistant");
        po.setDescription("");
        po.setSystemPrompt("You are helpful.");
        po.setModelConfigId(10L);
        po.setKnowledgeBaseIdsJson(List.of());
        po.setToolIdsJson(List.of());
        po.setMaxToolRounds(2);
        return po;
    }

    private static ModelConfigResp modelConfig() {
        ModelConfigResp resp = new ModelConfigResp();
        resp.setId(10L);
        resp.setEnabled(1);
        return resp;
    }
}
