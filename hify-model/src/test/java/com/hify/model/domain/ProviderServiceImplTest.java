package com.hify.model.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.model.api.ConnectivityTestResult;
import com.hify.model.api.CreateProviderReq;
import com.hify.model.api.ProviderResp;
import com.hify.model.api.UpdateProviderReq;
import com.hify.model.domain.adapter.ProviderAdapter;
import com.hify.model.domain.adapter.ProviderAdapterFactory;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderHealthMapper;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ProviderServiceImpl.class)
class ProviderServiceImplTest {

    @MockBean
    private ProviderMapper providerMapper;

    @MockBean
    private ModelConfigMapper modelConfigMapper;

    @MockBean
    private ProviderHealthMapper providerHealthMapper;

    @MockBean
    private ProviderAdapterFactory providerAdapterFactory;

    @MockBean
    private ProviderAdapter providerAdapter;

    @Autowired
    private ProviderServiceImpl providerService;

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            ProviderPo po = invocation.getArgument(0);
            po.setId(100L);
            return 1;
        }).when(providerMapper).insert(any(ProviderPo.class));
    }

    @Test
    void should_createProviderAndReturnId_when_providerNameIsUnique() {
        CreateProviderReq req = createProviderReq("OpenAI", "openai", "sk-valid-token");
        when(providerMapper.selectCount(any())).thenReturn(0L);

        ProviderResp resp = providerService.create(req);

        ArgumentCaptor<ProviderPo> captor = ArgumentCaptor.forClass(ProviderPo.class);
        verify(providerMapper).insert(captor.capture());
        ProviderPo inserted = captor.getValue();
        assertThat(resp.getId()).isEqualTo(100L);
        assertThat(resp.getName()).isEqualTo("OpenAI");
        assertThat(resp.getType()).isEqualTo("OPENAI");
        assertThat(inserted.getName()).isEqualTo("OpenAI");
        assertThat(inserted.getType()).isEqualTo("OPENAI");
        assertThat(inserted.getEnabled()).isEqualTo(1);
        assertThat(inserted.getAuthConfig()).containsEntry("apiKey", "sk-valid-token");
    }

    @Test
    void should_throwProviderNameDuplicate_when_providerNameAlreadyExists() {
        CreateProviderReq req = createProviderReq("OpenAI", "openai", "sk-valid-token");
        when(providerMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> providerService.create(req))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROVIDER_NAME_DUPLICATE);
        verify(providerMapper, never()).insert(any(ProviderPo.class));
    }

    @Test
    void should_throwConstraintViolationException_when_nameIsNull() {
        CreateProviderReq req = createProviderReq(null, "openai", "sk-valid-token");

        assertThatThrownBy(() -> providerService.create(req))
                .isInstanceOf(ConstraintViolationException.class)
                .hasMessageContaining("名称不能为空");
        verify(providerMapper, never()).insert(any(ProviderPo.class));
    }

    @Test
    void should_throwBizException_when_apiKeyFormatIsInvalid() {
        CreateProviderReq req = createProviderReq("OpenAI", "openai", "invalid-key");
        when(providerMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> providerService.create(req))
                .isInstanceOf(BizException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PARAM_ERROR);
        verify(providerMapper, never()).insert(any(ProviderPo.class));
    }

    @Test
    void updateChangesTypeAndAllowsClearingBaseUrl() {
        ProviderPo existing = new ProviderPo();
        existing.setId(1L);
        existing.setName("Local Ollama");
        existing.setType("OLLAMA");
        existing.setBaseUrl("http://localhost:11434");
        existing.setEnabled(1);
        existing.setSortOrder(0);
        when(providerMapper.selectById(1L)).thenReturn(existing);
        when(providerMapper.selectCount(any())).thenReturn(0L);

        UpdateProviderReq req = new UpdateProviderReq();
        req.setName("OpenAI Compatible");
        req.setType("OPENAI_COMPATIBLE");
        req.setBaseUrl("");

        ProviderResp resp = providerService.update(1L, req);

        ArgumentCaptor<ProviderPo> captor = ArgumentCaptor.forClass(ProviderPo.class);
        verify(providerMapper).updateById(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(captor.getValue().getBaseUrl()).isEmpty();
        assertThat(resp.getType()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(resp.getBaseUrl()).isEmpty();
    }

    @Test
    void testConnectionSyncsDiscoveredModelsIntoModelConfig() {
        ProviderPo existing = new ProviderPo();
        existing.setId(1L);
        existing.setName("OpenAI");
        existing.setType("OPENAI");
        existing.setEnabled(1);
        existing.setSortOrder(0);
        when(providerMapper.selectById(1L)).thenReturn(existing);
        when(providerAdapterFactory.getAdapter("OPENAI")).thenReturn(providerAdapter);
        when(providerAdapter.testConnection(existing))
                .thenReturn(ConnectivityTestResult.success(120, List.of("gpt-4o", "gpt-4o-mini")));
        when(modelConfigMapper.selectList(any())).thenReturn(List.of());

        providerService.test(1L);

        ArgumentCaptor<ModelConfigPo> captor = ArgumentCaptor.forClass(ModelConfigPo.class);
        verify(modelConfigMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(ModelConfigPo::getProviderId, ModelConfigPo::getName,
                        ModelConfigPo::getModelId, ModelConfigPo::getEnabled)
                .containsExactly(
                        tuple(1L, "gpt-4o", "gpt-4o", 1),
                        tuple(1L, "gpt-4o-mini", "gpt-4o-mini", 1)
                );
    }

    private static CreateProviderReq createProviderReq(String name, String type, String apiKey) {
        CreateProviderReq req = new CreateProviderReq();
        req.setName(name);
        req.setType(type);
        req.setApiKey(apiKey);
        return req;
    }
}
