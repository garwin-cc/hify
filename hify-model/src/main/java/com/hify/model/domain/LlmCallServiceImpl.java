package com.hify.model.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.metrics.HifyMetrics;
import com.hify.common.resilience.CircuitBreakerService;
import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.api.ChatStreamCallback;
import com.hify.model.api.LlmCallService;
import com.hify.model.domain.adapter.ProviderAdapter;
import com.hify.model.domain.adapter.ProviderAdapterFactory;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCallServiceImpl implements LlmCallService {

    private final ModelConfigMapper     modelConfigMapper;
    private final ProviderMapper        providerMapper;
    private final ProviderAdapterFactory adapterFactory;
    private final HifyMetrics           hifyMetrics;
    private final CircuitBreakerService circuitBreakerService;
    private final LlmFallbackProperties fallbackProperties;

    @Override
    public ChatResponse chat(Long modelConfigId, ChatRequest request) {
        ModelConfigPo config   = requireModelConfig(modelConfigId);
        ProviderPo    provider = requireProvider(config.getProviderId());
        ProviderAdapter adapter = adapterFactory.getAdapter(provider.getType());
        applyModelDefaults(request, config);
        long start = System.currentTimeMillis();
        log.info("llm chat start modelConfigId={} model={} provider={}",
                modelConfigId, config.getModelId(), provider.getType());
        try {
            ChatResponse response = circuitBreakerService.execute(provider.getType(),
                    () -> adapter.chat(provider, request));
            log.info("llm chat end modelConfigId={} model={} provider={} finishReason={} inputTokens={} outputTokens={} elapsedMs={}",
                    modelConfigId, config.getModelId(), provider.getType(),
                    response.getFinishReason(), response.getInputTokens(), response.getOutputTokens(),
                    System.currentTimeMillis() - start);
            hifyMetrics.recordLlmCall(provider.getType(), config.getModelId(), "success",
                    System.currentTimeMillis() - start);
            return response;
        } catch (Exception e) {
            ChatResponse fallbackResponse = tryFallbackChat(provider, request, e);
            if (fallbackResponse != null) {
                return fallbackResponse;
            }
            log.warn("llm chat failed modelConfigId={} model={} provider={} elapsedMs={} message={}",
                    modelConfigId, config.getModelId(), provider.getType(),
                    System.currentTimeMillis() - start, e.getMessage());
            hifyMetrics.recordLlmCall(provider.getType(), config.getModelId(), "failure",
                    System.currentTimeMillis() - start);
            throw e;
        }
    }

    @Override
    public void streamChat(Long modelConfigId, ChatRequest request, ChatStreamCallback callback) {
        ModelConfigPo config   = requireModelConfig(modelConfigId);
        ProviderPo    provider = requireProvider(config.getProviderId());

        applyModelDefaults(request, config);
        long start = System.currentTimeMillis();
        log.info("llm stream start modelConfigId={} model={} provider={}",
                modelConfigId, config.getModelId(), provider.getType());
        try {
            streamWithResilience(provider, request, callback);
            log.info("llm stream end modelConfigId={} model={} provider={} elapsedMs={}",
                    modelConfigId, config.getModelId(), provider.getType(),
                    System.currentTimeMillis() - start);
            hifyMetrics.recordLlmCall(provider.getType(), config.getModelId(), "success",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("llm stream failed modelConfigId={} model={} provider={} elapsedMs={} message={}",
                    modelConfigId, config.getModelId(), provider.getType(),
                    System.currentTimeMillis() - start, e.getMessage());
            hifyMetrics.recordLlmCall(provider.getType(), config.getModelId(), "failure",
                    System.currentTimeMillis() - start);
            if (!tryFallbackStream(provider, request, callback, e)) {
                throw e;
            }
        }
    }

    // ------------------------------------------------------------------ 私有辅助

    /**
     * 将模型配置的 modelId 写入请求，并用 extraParams 填充调用方未显式设置的参数。
     * 调用方显式传入的参数（temperature / maxTokens）优先级更高。
     */
    @SuppressWarnings("unchecked")
    private static void applyModelDefaults(ChatRequest request, ModelConfigPo config) {
        request.setModelId(config.getModelId());

        Map<String, Object> extra = config.getExtraParams();
        if (extra == null) return;

        if (request.getTemperature() == null && extra.containsKey("temperature")) {
            Object val = extra.get("temperature");
            if (val instanceof Number n) request.setTemperature(BigDecimal.valueOf(n.doubleValue()));
        }
        if (request.getMaxTokens() == null && extra.containsKey("max_tokens")) {
            Object val = extra.get("max_tokens");
            if (val instanceof Number n) request.setMaxTokens(n.intValue());
        }
    }

    private ModelConfigPo requireModelConfig(Long id) {
        ModelConfigPo config = modelConfigMapper.selectOne(
                Wrappers.<ModelConfigPo>lambdaQuery()
                        .eq(ModelConfigPo::getId, id)
                        .eq(ModelConfigPo::getDeleted, 0)
                        .eq(ModelConfigPo::getEnabled, 1));
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在或已禁用: " + id);
        }
        return config;
    }

    private ProviderPo requireProvider(Long providerId) {
        ProviderPo provider = providerMapper.selectOne(
                Wrappers.<ProviderPo>lambdaQuery()
                        .eq(ProviderPo::getId, providerId)
                        .eq(ProviderPo::getDeleted, 0)
                        .eq(ProviderPo::getEnabled, 1));
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在或已禁用: " + providerId);
        }
        return provider;
    }

    private ChatResponse tryFallbackChat(ProviderPo primaryProvider, ChatRequest request, Exception primaryError) {
        FallbackTarget fallback = resolveFallback(primaryProvider);
        if (fallback == null) {
            return null;
        }
        applyModelDefaults(request, fallback.modelConfig());
        ProviderAdapter fallbackAdapter = adapterFactory.getAdapter(fallback.provider().getType());
        log.warn("llm chat fallback primaryProvider={} fallbackProvider={} message={}",
                primaryProvider.getType(), fallback.provider().getType(), primaryError.getMessage());
        return circuitBreakerService.execute(fallback.provider().getType(),
                () -> fallbackAdapter.chat(fallback.provider(), request));
    }

    private boolean tryFallbackStream(ProviderPo primaryProvider, ChatRequest request, ChatStreamCallback callback,
                                      Exception primaryError) {
        FallbackTarget fallback = resolveFallback(primaryProvider);
        if (fallback == null) {
            return false;
        }
        applyModelDefaults(request, fallback.modelConfig());
        ProviderAdapter fallbackAdapter = adapterFactory.getAdapter(fallback.provider().getType());
        log.warn("llm stream fallback primaryProvider={} fallbackProvider={} message={}",
                primaryProvider.getType(), fallback.provider().getType(), primaryError.getMessage());
        circuitBreakerService.execute(fallback.provider().getType(), () -> {
            fallbackAdapter.streamChat(fallback.provider(), request, callback);
            return Boolean.TRUE;
        });
        return true;
    }

    private void streamWithResilience(ProviderPo provider, ChatRequest request, ChatStreamCallback callback) {
        ProviderAdapter adapter = adapterFactory.getAdapter(provider.getType());
        circuitBreakerService.execute(provider.getType(), () -> {
            adapter.streamChat(provider, request, new ChatStreamCallback() {
                @Override
                public void onToken(String token) {
                    callback.onToken(token);
                }

                @Override
                public void onComplete(ChatResponse response) {
                    callback.onComplete(response);
                }

                @Override
                public void onError(com.hify.common.http.LlmApiException e) {
                    throw e;
                }
            });
            return Boolean.TRUE;
        });
    }

    private FallbackTarget resolveFallback(ProviderPo primaryProvider) {
        String fallbackType = fallbackProperties.fallbackType(primaryProvider.getType());
        if (fallbackType == null || fallbackType.isBlank()
                || fallbackType.equalsIgnoreCase(primaryProvider.getType())) {
            return null;
        }
        List<ModelConfigPo> candidates = modelConfigMapper.selectList(
                Wrappers.<ModelConfigPo>lambdaQuery()
                        .eq(ModelConfigPo::getDeleted, 0)
                        .eq(ModelConfigPo::getEnabled, 1)
                        .eq(ModelConfigPo::getModelType, "CHAT")
                        .orderByAsc(ModelConfigPo::getSortOrder)
                        .orderByAsc(ModelConfigPo::getId));
        for (ModelConfigPo candidate : candidates) {
            ProviderPo provider = requireProvider(candidate.getProviderId());
            if (fallbackType.equalsIgnoreCase(provider.getType())) {
                return new FallbackTarget(candidate, provider);
            }
        }
        return null;
    }

    private record FallbackTarget(ModelConfigPo modelConfig, ProviderPo provider) {
    }
}
