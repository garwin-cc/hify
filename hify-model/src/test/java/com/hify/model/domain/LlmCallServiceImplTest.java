package com.hify.model.domain;

import com.hify.common.http.LlmApiException;
import com.hify.common.metrics.HifyMetrics;
import com.hify.common.resilience.CircuitBreakerService;
import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.api.ChatStreamCallback;
import com.hify.model.domain.adapter.ProviderAdapter;
import com.hify.model.domain.adapter.ProviderAdapterFactory;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmCallServiceImplTest {

    @Mock
    private ModelConfigMapper modelConfigMapper;
    @Mock
    private ProviderMapper providerMapper;
    @Mock
    private ProviderAdapterFactory adapterFactory;
    @Mock
    private ProviderAdapter primaryAdapter;
    @Mock
    private ProviderAdapter fallbackAdapter;
    @Mock
    private CircuitBreakerService circuitBreakerService;
    @Mock
    private HifyMetrics hifyMetrics;

    private LlmCallServiceImpl llmCallService;

    @BeforeEach
    void setUp() {
        when(circuitBreakerService.execute(any(), any())).thenAnswer(invocation -> {
            Supplier<?> supplier = invocation.getArgument(1);
            return supplier.get();
        });
        llmCallService = new LlmCallServiceImpl(
                modelConfigMapper,
                providerMapper,
                adapterFactory,
                hifyMetrics,
                circuitBreakerService,
                new LlmFallbackProperties(Map.of("OPENAI", "OLLAMA")));
    }

    @Test
    void chatFallsBackToConfiguredProviderTypeWhenPrimaryFails() {
        ModelConfigPo primaryModel = model(10L, 1L, "gpt-4o", "CHAT");
        ProviderPo primaryProvider = provider(1L, "OPENAI");
        ModelConfigPo fallbackModel = model(20L, 2L, "llama3", "CHAT");
        ProviderPo fallbackProvider = provider(2L, "OLLAMA");
        ChatRequest request = ChatRequest.builder().messages(List.of()).build();
        ChatResponse fallbackResponse = ChatResponse.builder().content("fallback ok").finishReason("stop").build();

        when(modelConfigMapper.selectOne(any())).thenReturn(primaryModel);
        when(providerMapper.selectOne(any())).thenReturn(primaryProvider, fallbackProvider);
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(fallbackModel));
        when(adapterFactory.getAdapter("OPENAI")).thenReturn(primaryAdapter);
        when(adapterFactory.getAdapter("OLLAMA")).thenReturn(fallbackAdapter);
        when(primaryAdapter.chat(eq(primaryProvider), eq(request))).thenThrow(
                new LlmApiException(LlmApiException.Type.TIMEOUT, "timeout"));
        when(fallbackAdapter.chat(eq(fallbackProvider), eq(request))).thenReturn(fallbackResponse);

        ChatResponse response = llmCallService.chat(10L, request);

        assertThat(response.getContent()).isEqualTo("fallback ok");
        assertThat(request.getModelId()).isEqualTo("llama3");
        verify(circuitBreakerService).execute(eq("OPENAI"), any());
        verify(circuitBreakerService).execute(eq("OLLAMA"), any());
    }

    @Test
    void streamChatFallsBackWhenPrimaryStreamThrowsBeforeCallback() {
        ModelConfigPo primaryModel = model(10L, 1L, "gpt-4o", "CHAT");
        ProviderPo primaryProvider = provider(1L, "OPENAI");
        ModelConfigPo fallbackModel = model(20L, 2L, "llama3", "CHAT");
        ProviderPo fallbackProvider = provider(2L, "OLLAMA");
        ChatRequest request = ChatRequest.builder().messages(List.of()).build();
        AtomicReference<String> completedContent = new AtomicReference<>();

        when(modelConfigMapper.selectOne(any())).thenReturn(primaryModel);
        when(providerMapper.selectOne(any())).thenReturn(primaryProvider, fallbackProvider);
        when(modelConfigMapper.selectList(any())).thenReturn(List.of(fallbackModel));
        when(adapterFactory.getAdapter("OPENAI")).thenReturn(primaryAdapter);
        when(adapterFactory.getAdapter("OLLAMA")).thenReturn(fallbackAdapter);
        org.mockito.Mockito.doThrow(new LlmApiException(LlmApiException.Type.TIMEOUT, "timeout"))
                .when(primaryAdapter).streamChat(eq(primaryProvider), eq(request), any());
        org.mockito.Mockito.doAnswer(invocation -> {
            ChatStreamCallback callback = invocation.getArgument(2);
            callback.onToken("fallback ");
            callback.onComplete(ChatResponse.builder().content("fallback ok").finishReason("stop").build());
            return null;
        }).when(fallbackAdapter).streamChat(eq(fallbackProvider), eq(request), any());

        llmCallService.streamChat(10L, request, new ChatStreamCallback() {
            @Override
            public void onToken(String token) {
            }

            @Override
            public void onComplete(ChatResponse response) {
                completedContent.set(response.getContent());
            }

            @Override
            public void onError(LlmApiException e) {
                completedContent.set("error");
            }
        });

        assertThat(completedContent.get()).isEqualTo("fallback ok");
        assertThat(request.getModelId()).isEqualTo("llama3");
    }

    private static ModelConfigPo model(Long id, Long providerId, String modelId, String type) {
        ModelConfigPo model = new ModelConfigPo();
        model.setId(id);
        model.setProviderId(providerId);
        model.setModelId(modelId);
        model.setModelType(type);
        model.setEnabled(1);
        return model;
    }

    private static ProviderPo provider(Long id, String type) {
        ProviderPo provider = new ProviderPo();
        provider.setId(id);
        provider.setType(type);
        provider.setEnabled(1);
        return provider;
    }
}
