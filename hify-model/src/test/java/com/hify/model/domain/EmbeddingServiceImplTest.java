package com.hify.model.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingServiceImplTest {

    @Test
    void embedSortsResponseDataByIndexBeforeMappingInputs() {
        ModelConfigMapper modelConfigMapper = mock(ModelConfigMapper.class);
        ProviderMapper providerMapper = mock(ProviderMapper.class);
        LlmHttpClient llmHttpClient = mock(LlmHttpClient.class);
        EmbeddingServiceImpl service = new EmbeddingServiceImpl(
                modelConfigMapper, providerMapper, llmHttpClient, new ObjectMapper());

        ModelConfigPo config = new ModelConfigPo();
        config.setId(11L);
        config.setProviderId(22L);
        config.setModelId("text-embedding-3-small");
        config.setEnabled(1);
        when(modelConfigMapper.selectOne(any())).thenReturn(config);

        ProviderPo provider = new ProviderPo();
        provider.setId(22L);
        provider.setBaseUrl("https://example.test");
        provider.setAuthConfig(Map.of("apiKey", "sk-test"));
        provider.setEnabled(1);
        when(providerMapper.selectOne(any())).thenReturn(provider);

        when(llmHttpClient.post(eq("https://example.test/v1/embeddings"), anyMap(), any()))
                .thenReturn("""
                        {
                          "data": [
                            {"index": 1, "embedding": [3.0, 4.0]},
                            {"index": 0, "embedding": [1.0, 2.0]}
                          ]
                        }
                        """);

        List<List<Double>> result = service.embed(11L, List.of("first", "second"));

        assertThat(result).containsExactly(List.of(1.0, 2.0), List.of(3.0, 4.0));
    }

    @Test
    void embedDoesNotDuplicateVersionPathWhenBaseUrlAlreadyEndsWithV1() {
        ModelConfigMapper modelConfigMapper = mock(ModelConfigMapper.class);
        ProviderMapper providerMapper = mock(ProviderMapper.class);
        LlmHttpClient llmHttpClient = mock(LlmHttpClient.class);
        EmbeddingServiceImpl service = new EmbeddingServiceImpl(
                modelConfigMapper, providerMapper, llmHttpClient, new ObjectMapper());

        ModelConfigPo config = new ModelConfigPo();
        config.setId(12L);
        config.setProviderId(23L);
        config.setModelId("text-embedding-v4");
        config.setEnabled(1);
        when(modelConfigMapper.selectOne(any())).thenReturn(config);

        ProviderPo provider = new ProviderPo();
        provider.setId(23L);
        provider.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        provider.setAuthConfig(Map.of("apiKey", "sk-test"));
        provider.setEnabled(1);
        when(providerMapper.selectOne(any())).thenReturn(provider);

        when(llmHttpClient.post(eq("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"),
                anyMap(), any()))
                .thenReturn("""
                        {
                          "data": [
                            {"index": 0, "embedding": [1.0, 2.0]}
                          ]
                        }
                        """);

        service.embed(12L, List.of("probe"));

        verify(llmHttpClient).post(eq("https://dashscope.aliyuncs.com/compatible-mode/v1/embeddings"),
                anyMap(), any());
    }
}
