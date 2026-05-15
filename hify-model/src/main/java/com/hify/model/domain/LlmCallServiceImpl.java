package com.hify.model.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.log.TraceContext;
import com.hify.common.metrics.HifyMetrics;
import com.hify.common.ratelimit.RateLimitDimension;
import com.hify.common.ratelimit.RateLimitQuotaService;
import com.hify.common.ratelimit.RateLimitResult;
import com.hify.common.ratelimit.RateLimitRule;
import com.hify.common.ratelimit.RateLimitService;
import com.hify.common.resilience.CircuitBreakerService;
import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.api.ChatStreamCallback;
import com.hify.model.api.LlmCallService;
import com.hify.model.api.LlmCallStatRecord;
import com.hify.model.api.LlmUsageStatsService;
import com.hify.model.api.ModelDefaultPolicyResp;
import com.hify.model.api.ModelDefaultPolicyService;
import com.hify.model.domain.adapter.ProviderAdapter;
import com.hify.model.domain.adapter.ProviderAdapterFactory;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

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
    private ModelDefaultPolicyService modelDefaultPolicyService;
    private LlmUsageStatsService llmUsageStatsService;
    private RateLimitService rateLimitService;
    private RateLimitQuotaService rateLimitQuotaService;

    private static final int DEFAULT_PROVIDER_LIMIT_PER_MINUTE = 600;
    private static final int DEFAULT_MODEL_LIMIT_PER_MINUTE = 600;

    @Autowired(required = false)
    public void setModelDefaultPolicyService(ModelDefaultPolicyService modelDefaultPolicyService) {
        this.modelDefaultPolicyService = modelDefaultPolicyService;
    }

    @Autowired(required = false)
    public void setLlmUsageStatsService(LlmUsageStatsService llmUsageStatsService) {
        this.llmUsageStatsService = llmUsageStatsService;
    }

    @Autowired(required = false)
    public void setRateLimitService(RateLimitService rateLimitService) {
        this.rateLimitService = rateLimitService;
    }

    @Autowired(required = false)
    public void setRateLimitQuotaService(RateLimitQuotaService rateLimitQuotaService) {
        this.rateLimitQuotaService = rateLimitQuotaService;
    }

    @Override
    public ChatResponse chat(Long modelConfigId, ChatRequest request) {
        ModelConfigPo config   = requireModelConfig(modelConfigId);
        ProviderPo    provider = requireProvider(config.getProviderId());
        checkLlmRateLimit(provider, config);
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
            recordStat(request, provider, config, "CHAT", response, true, false,
                    System.currentTimeMillis() - start, null);
            hifyMetrics.recordLlmCall(provider.getType(), config.getModelId(), "success",
                    System.currentTimeMillis() - start);
            recordTokens(provider, config, response);
            return response;
        } catch (Exception e) {
            recordStat(request, provider, config, "CHAT", null, false, false,
                    System.currentTimeMillis() - start, e);
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

        checkLlmRateLimit(provider, config);
        applyModelDefaults(request, config);
        long start = System.currentTimeMillis();
        log.info("llm stream start modelConfigId={} model={} provider={}",
                modelConfigId, config.getModelId(), provider.getType());
        AtomicReference<ChatResponse> completed = new AtomicReference<>();
        try {
            streamWithResilience(provider, request, recordingCallback(callback, completed));
            log.info("llm stream end modelConfigId={} model={} provider={} elapsedMs={}",
                    modelConfigId, config.getModelId(), provider.getType(),
                    System.currentTimeMillis() - start);
            recordStat(request, provider, config, "STREAM", completed.get(), true, false,
                    System.currentTimeMillis() - start, null);
            hifyMetrics.recordLlmCall(provider.getType(), config.getModelId(), "success",
                    System.currentTimeMillis() - start);
            recordTokens(provider, config, completed.get());
        } catch (Exception e) {
            log.warn("llm stream failed modelConfigId={} model={} provider={} elapsedMs={} message={}",
                    modelConfigId, config.getModelId(), provider.getType(),
                    System.currentTimeMillis() - start, e.getMessage());
            recordStat(request, provider, config, "STREAM", completed.get(), false, false,
                    System.currentTimeMillis() - start, e);
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
        FallbackTarget fallback = resolveFallback(primaryProvider, request);
        if (fallback == null) {
            return null;
        }
        checkLlmRateLimit(fallback.provider(), fallback.modelConfig());
        applyModelDefaults(request, fallback.modelConfig());
        ProviderAdapter fallbackAdapter = adapterFactory.getAdapter(fallback.provider().getType());
        log.warn("llm chat fallback primaryProvider={} fallbackProvider={} message={}",
                primaryProvider.getType(), fallback.provider().getType(), primaryError.getMessage());
        long start = System.currentTimeMillis();
        try {
            ChatResponse response = circuitBreakerService.execute(fallback.provider().getType(),
                    () -> fallbackAdapter.chat(fallback.provider(), request));
            recordStat(request, fallback.provider(), fallback.modelConfig(), "CHAT", response, true, true,
                    System.currentTimeMillis() - start, null);
            hifyMetrics.recordLlmCall(fallback.provider().getType(), fallback.modelConfig().getModelId(),
                    "fallback_success", System.currentTimeMillis() - start);
            recordTokens(fallback.provider(), fallback.modelConfig(), response);
            return response;
        } catch (Exception e) {
            recordStat(request, fallback.provider(), fallback.modelConfig(), "CHAT", null, false, true,
                    System.currentTimeMillis() - start, e);
            throw e;
        }
    }

    private boolean tryFallbackStream(ProviderPo primaryProvider, ChatRequest request, ChatStreamCallback callback,
                                      Exception primaryError) {
        FallbackTarget fallback = resolveFallback(primaryProvider, request);
        if (fallback == null) {
            return false;
        }
        checkLlmRateLimit(fallback.provider(), fallback.modelConfig());
        applyModelDefaults(request, fallback.modelConfig());
        ProviderAdapter fallbackAdapter = adapterFactory.getAdapter(fallback.provider().getType());
        log.warn("llm stream fallback primaryProvider={} fallbackProvider={} message={}",
                primaryProvider.getType(), fallback.provider().getType(), primaryError.getMessage());
        AtomicReference<ChatResponse> completed = new AtomicReference<>();
        long start = System.currentTimeMillis();
        try {
            circuitBreakerService.execute(fallback.provider().getType(), () -> {
                fallbackAdapter.streamChat(fallback.provider(), request, recordingCallback(callback, completed));
                return Boolean.TRUE;
            });
            recordStat(request, fallback.provider(), fallback.modelConfig(), "STREAM", completed.get(), true, true,
                    System.currentTimeMillis() - start, null);
            hifyMetrics.recordLlmCall(fallback.provider().getType(), fallback.modelConfig().getModelId(),
                    "fallback_success", System.currentTimeMillis() - start);
            recordTokens(fallback.provider(), fallback.modelConfig(), completed.get());
        } catch (Exception e) {
            recordStat(request, fallback.provider(), fallback.modelConfig(), "STREAM", completed.get(), false, true,
                    System.currentTimeMillis() - start, e);
            throw e;
        }
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

    private void checkLlmRateLimit(ProviderPo provider, ModelConfigPo config) {
        if (rateLimitService == null) {
            return;
        }
        checkRateLimit(rule(RateLimitDimension.PROVIDER, String.valueOf(provider.getId()),
                DEFAULT_PROVIDER_LIMIT_PER_MINUTE));
        checkRateLimit(rule(RateLimitDimension.MODEL, config.getModelId(), DEFAULT_MODEL_LIMIT_PER_MINUTE));
    }

    private void checkRateLimit(RateLimitRule rule) {
        RateLimitResult result = rateLimitService.check(rule);
        if (!result.isAllowed()) {
            throw new BizException(ErrorCode.TOO_MANY_REQUESTS,
                    ErrorCode.TOO_MANY_REQUESTS.getMessage() + "，请 "
                            + result.getRetryAfterSeconds() + " 秒后重试");
        }
    }

    private RateLimitRule rule(RateLimitDimension dimension, String key, int limit) {
        if (rateLimitQuotaService != null) {
            return rateLimitQuotaService.resolve(dimension, key, limit, Duration.ofMinutes(1), true);
        }
        return RateLimitRule.builder()
                .dimension(dimension)
                .key(key)
                .limit(limit)
                .window(Duration.ofMinutes(1))
                .failOpen(true)
                .build();
    }

    private FallbackTarget resolveFallback(ProviderPo primaryProvider, ChatRequest request) {
        String fallbackType = fallbackProperties.fallbackType(primaryProvider.getType());
        FallbackTarget policyTarget = resolvePolicyFallback(primaryProvider, request, fallbackType);
        if (policyTarget != null) {
            return policyTarget;
        }
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

    private FallbackTarget resolvePolicyFallback(ProviderPo primaryProvider, ChatRequest request, String fallbackType) {
        if (modelDefaultPolicyService == null) {
            return null;
        }
        ModelDefaultPolicyResp policy = modelDefaultPolicyService.resolve("CHAT",
                request == null ? null : request.getContext(), fallbackType);
        if (policy == null || policy.getModelConfigId() == null) {
            return null;
        }
        ModelConfigPo modelConfig = modelConfigMapper.selectById(policy.getModelConfigId());
        if (modelConfig == null || !Integer.valueOf(1).equals(modelConfig.getEnabled())) {
            return null;
        }
        ProviderPo provider = requireProvider(modelConfig.getProviderId());
        if (provider.getId().equals(primaryProvider.getId())) {
            return null;
        }
        return new FallbackTarget(modelConfig, provider);
    }

    private ChatStreamCallback recordingCallback(ChatStreamCallback callback, AtomicReference<ChatResponse> completed) {
        return new ChatStreamCallback() {
            @Override
            public void onToken(String token) {
                callback.onToken(token);
            }

            @Override
            public void onComplete(ChatResponse response) {
                completed.set(response);
                callback.onComplete(response);
            }

            @Override
            public void onError(com.hify.common.http.LlmApiException e) {
                callback.onError(e);
            }
        };
    }

    private void recordStat(ChatRequest request, ProviderPo provider, ModelConfigPo config, String callType,
                            ChatResponse response, boolean success, boolean fallbackUsed,
                            long elapsedMs, Exception error) {
        if (llmUsageStatsService == null) {
            return;
        }
        llmUsageStatsService.record(LlmCallStatRecord.builder()
                .traceId(TraceContext.ensureTraceId())
                .context(request == null ? null : request.getContext())
                .providerId(provider.getId())
                .providerType(provider.getType())
                .modelConfigId(config.getId())
                .modelId(config.getModelId())
                .callType(callType)
                .success(success)
                .fallbackUsed(fallbackUsed)
                .inputTokens(response == null ? 0 : response.getInputTokens())
                .outputTokens(response == null ? 0 : response.getOutputTokens())
                .latencyMs((int) Math.min(Integer.MAX_VALUE, elapsedMs))
                .errorCode(errorCode(error))
                .errorMessage(error == null ? "" : error.getMessage())
                .build());
    }

    private void recordTokens(ProviderPo provider, ModelConfigPo config, ChatResponse response) {
        if (response == null) {
            return;
        }
        hifyMetrics.recordLlmTokens(provider.getType(), config.getModelId(),
                response.getInputTokens(), response.getOutputTokens());
    }

    private static String errorCode(Exception error) {
        if (error == null) {
            return "";
        }
        if (error instanceof BizException bizException) {
            return bizException.getErrorCode().name();
        }
        if (error instanceof com.hify.common.http.LlmApiException llmApiException) {
            return llmApiException.getType().name();
        }
        return error.getClass().getSimpleName();
    }

    private record FallbackTarget(ModelConfigPo modelConfig, ProviderPo provider) {
    }
}
