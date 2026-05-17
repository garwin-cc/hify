package com.hify.model.domain;

import com.hify.model.api.CreateModelConfigReq;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ModelConfigServiceImplTest {

    @Test
    void createAcceptsRerankModelType() {
        ModelConfigMapper mapper = mock(ModelConfigMapper.class);
        ProviderMapper providerMapper = mock(ProviderMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        when(providerMapper.selectById(1L)).thenReturn(new ProviderPo());
        doAnswer(invocation -> {
            ModelConfigPo po = invocation.getArgument(0);
            po.setId(99L);
            return 1;
        }).when(mapper).insert(any(ModelConfigPo.class));
        ModelConfigServiceImpl service = new ModelConfigServiceImpl(mapper, providerMapper);
        CreateModelConfigReq req = new CreateModelConfigReq();
        req.setProviderId(1L);
        req.setName("bge-reranker");
        req.setModelId("bge-reranker-v2");
        req.setModelType("RERANK");

        ModelConfigResp resp = service.create(req);

        assertThat(resp.getModelType()).isEqualTo("RERANK");
    }

    @Test
    void getByIdIncludesProviderTypeForProviderAwareRuntimeTuning() {
        ModelConfigMapper mapper = mock(ModelConfigMapper.class);
        ProviderMapper providerMapper = mock(ProviderMapper.class);
        ModelConfigPo po = new ModelConfigPo();
        po.setId(11L);
        po.setProviderId(1L);
        po.setName("nomic embed");
        po.setModelId("nomic-embed-text");
        po.setModelType("EMBEDDING");
        po.setEnabled(1);
        when(mapper.selectById(11L)).thenReturn(po);
        ProviderPo provider = new ProviderPo();
        provider.setId(1L);
        provider.setType("OLLAMA");
        when(providerMapper.selectById(1L)).thenReturn(provider);
        ModelConfigServiceImpl service = new ModelConfigServiceImpl(mapper, providerMapper);

        ModelConfigResp resp = service.getById(11L);

        assertThat(resp.getProviderType()).isEqualTo("OLLAMA");
    }
}
