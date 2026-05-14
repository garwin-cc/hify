package com.hify.model.domain;

import com.hify.model.api.LlmCallContext;
import com.hify.model.api.ModelDefaultPolicyReq;
import com.hify.model.api.ModelDefaultPolicyResp;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ModelDefaultPolicyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelDefaultPolicyServiceImplTest {

    @Mock
    private ModelDefaultPolicyMapper policyMapper;
    @Mock
    private ModelConfigMapper modelConfigMapper;

    private ModelDefaultPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModelDefaultPolicyServiceImpl(policyMapper, modelConfigMapper);
    }

    @Test
    void resolvePrefersAppOverProjectAndGlobal() {
        ModelDefaultPolicyPo appPolicy = policy(1L, "APP", 20L, 100L);
        when(policyMapper.selectOne(any())).thenReturn(appPolicy);
        when(modelConfigMapper.selectById(100L)).thenReturn(model(100L, "CHAT", 1));

        ModelDefaultPolicyResp resp = service.resolve("CHAT",
                LlmCallContext.builder().projectId(10L).appId(20L).build(), null);

        assertThat(resp.getScopeType()).isEqualTo("APP");
        assertThat(resp.getScopeId()).isEqualTo(20L);
        assertThat(resp.getModelConfigId()).isEqualTo(100L);
    }

    @Test
    void resolveIgnoresPolicyWhenModelIsDisabled() {
        when(policyMapper.selectOne(any()))
                .thenReturn(policy(1L, "GLOBAL", 0L, 100L))
                .thenReturn(null);
        when(modelConfigMapper.selectById(100L)).thenReturn(model(100L, "CHAT", 0));

        ModelDefaultPolicyResp resp = service.resolve("CHAT", null, "OLLAMA");

        assertThat(resp).isNull();
    }

    @Test
    void upsertRequiresProviderTypeForProviderFallbackScope() {
        ModelDefaultPolicyReq req = new ModelDefaultPolicyReq();
        req.setScopeType("PROVIDER_FALLBACK");
        req.setModelType("CHAT");
        req.setModelConfigId(100L);

        assertThatThrownBy(() -> service.upsert(req))
                .hasMessageContaining("providerType");
    }

    private static ModelDefaultPolicyPo policy(Long id, String scopeType, Long scopeId, Long modelConfigId) {
        ModelDefaultPolicyPo po = new ModelDefaultPolicyPo();
        po.setId(id);
        po.setScopeType(scopeType);
        po.setScopeId(scopeId);
        po.setModelType("CHAT");
        po.setProviderType("");
        po.setModelConfigId(modelConfigId);
        po.setEnabled(1);
        return po;
    }

    private static ModelConfigPo model(Long id, String modelType, Integer enabled) {
        ModelConfigPo po = new ModelConfigPo();
        po.setId(id);
        po.setModelType(modelType);
        po.setEnabled(enabled);
        return po;
    }
}
