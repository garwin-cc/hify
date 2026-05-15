package com.hify.model.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.model.api.RerankRequest;
import com.hify.model.api.RerankResult;
import com.hify.model.api.RerankService;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RerankServiceImpl implements RerankService {

    private static final int RERANK_TIMEOUT_SECONDS = 30;

    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<RerankResult> rerank(RerankRequest req) {
        if (req == null || req.getDocuments() == null || req.getDocuments().isEmpty()) {
            return List.of();
        }
        ModelConfigPo config = requireRerankModel(req.getModelConfigId());
        ProviderPo provider = requireProvider(config.getProviderId());
        Map<String, Object> body = new HashMap<>();
        body.put("model", config.getModelId());
        body.put("query", req.getQuery() == null ? "" : req.getQuery());
        body.put("documents", req.getDocuments());
        if (req.getTopN() != null) {
            body.put("top_n", req.getTopN());
        }
        String responseJson = llmHttpClient.post(rerankUrl(provider), bearerHeaders(provider),
                toJson(body), RERANK_TIMEOUT_SECONDS);
        return parseResults(responseJson);
    }

    private ModelConfigPo requireRerankModel(Long modelConfigId) {
        ModelConfigPo config = modelConfigMapper.selectOne(Wrappers.<ModelConfigPo>lambdaQuery()
                .eq(ModelConfigPo::getId, modelConfigId)
                .eq(ModelConfigPo::getEnabled, 1)
                .eq(ModelConfigPo::getModelType, "RERANK"));
        if (config == null) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "Rerank 模型配置不存在、已禁用或类型不是 RERANK: " + modelConfigId);
        }
        return config;
    }

    private ProviderPo requireProvider(Long providerId) {
        ProviderPo provider = providerMapper.selectOne(Wrappers.<ProviderPo>lambdaQuery()
                .eq(ProviderPo::getId, providerId)
                .eq(ProviderPo::getEnabled, 1));
        if (provider == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "Rerank 提供商不存在或已禁用: " + providerId);
        }
        return provider;
    }

    private List<RerankResult> parseResults(String responseJson) {
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode array = root.has("results") ? root.path("results") : root.path("data");
            List<RerankResult> results = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode item : array) {
                    int index = item.has("index") ? item.path("index").asInt() : item.path("document_index").asInt();
                    double score = item.has("relevance_score")
                            ? item.path("relevance_score").asDouble()
                            : item.path("score").asDouble();
                    results.add(new RerankResult(index, score));
                }
            }
            return results;
        } catch (Exception e) {
            throw new BizException(ErrorCode.THIRD_PARTY_ERROR, "Rerank 响应解析失败", e);
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Rerank 请求序列化失败", e);
        }
    }

    private static String rerankUrl(ProviderPo provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.openai.com";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        return (baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1") + "/rerank";
    }

    private static Map<String, String> bearerHeaders(ProviderPo provider) {
        String apiKey = extractApiKey(provider.getAuthConfig());
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (!apiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + apiKey);
        }
        return headers;
    }

    private static String extractApiKey(Map<String, Object> authConfig) {
        if (authConfig == null) {
            return "";
        }
        Object key = authConfig.get("apiKey");
        return key instanceof String s ? s : "";
    }
}
