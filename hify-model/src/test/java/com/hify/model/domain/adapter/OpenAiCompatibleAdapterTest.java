package com.hify.model.domain.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import com.hify.model.infra.ProviderPo;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiCompatibleAdapterTest {

    @Test
    void alibabaDefaultBaseUrlDoesNotDuplicateVersionPath() {
        LlmHttpClient llmHttpClient = mock(LlmHttpClient.class);
        when(llmHttpClient.get(eq("https://dashscope.aliyuncs.com/compatible-mode/v1/models"),
                anyMap(), eq(10))).thenReturn("{\"data\":[{\"id\":\"qwen-plus\"}]}");

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(llmHttpClient, new ObjectMapper());
        ProviderPo provider = new ProviderPo();
        provider.setType("ALIBABA");
        provider.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        provider.setAuthConfig(Map.of("apiKey", "sk-test"));

        adapter.testConnection(provider);

        verify(llmHttpClient).get(eq("https://dashscope.aliyuncs.com/compatible-mode/v1/models"),
                anyMap(), eq(10));
    }
}
