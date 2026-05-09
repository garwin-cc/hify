package com.hify.model.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.model.api.EmbeddingService;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper;

    @Override
    public List<List<Double>> embed(Long modelConfigId, List<String> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        ModelConfigPo config = modelConfigId != null ? requireModelConfig(modelConfigId) : findDefaultEmbeddingConfig();
        ProviderPo provider = requireProvider(config.getProviderId());
        String responseJson = llmHttpClient.post(embeddingsUrl(provider), bearerHeaders(provider),
                toJson(Map.of("model", config.getModelId(), "input", inputs)));
        List<IndexedEmbedding> embeddings = parseEmbeddings(responseJson);
        embeddings.sort(Comparator.comparingInt(IndexedEmbedding::index));
        if (embeddings.size() != inputs.size()) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED,
                    "Embedding 返回数量不匹配，expected=" + inputs.size() + " actual=" + embeddings.size());
        }
        log.info("embedding modelConfigId={} model={} count={}", config.getId(), config.getModelId(), inputs.size());
        return embeddings.stream().map(IndexedEmbedding::embedding).toList();
    }

    private ModelConfigPo findDefaultEmbeddingConfig() {
        ModelConfigPo config = modelConfigMapper.selectOne(Wrappers.<ModelConfigPo>lambdaQuery()
                .eq(ModelConfigPo::getEnabled, 1)
                .eq(ModelConfigPo::getModelType, "EMBEDDING")
                .orderByAsc(ModelConfigPo::getSortOrder)
                .orderByAsc(ModelConfigPo::getId)
                .last("LIMIT 1"));
        if (config == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED,
                    "未找到可用的 embedding 模型配置，请先在模型管理中添加 embedding 模型");
        }
        return config;
    }

    private ModelConfigPo requireModelConfig(Long id) {
        ModelConfigPo config = modelConfigMapper.selectOne(Wrappers.<ModelConfigPo>lambdaQuery()
                .eq(ModelConfigPo::getId, id)
                .eq(ModelConfigPo::getEnabled, 1)
                .eq(ModelConfigPo::getModelType, "EMBEDDING"));
        if (config == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED,
                    "Embedding 模型配置不存在、已禁用或类型不是 EMBEDDING: " + id);
        }
        return config;
    }

    private ProviderPo requireProvider(Long providerId) {
        ProviderPo provider = providerMapper.selectOne(Wrappers.<ProviderPo>lambdaQuery()
                .eq(ProviderPo::getId, providerId)
                .eq(ProviderPo::getEnabled, 1));
        if (provider == null) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "Embedding 提供商不存在或已禁用: " + providerId);
        }
        return provider;
    }

    private List<IndexedEmbedding> parseEmbeddings(String responseJson) {
        try {
            JsonNode data = objectMapper.readTree(responseJson).path("data");
            List<IndexedEmbedding> result = new ArrayList<>();
            for (JsonNode item : data) {
                List<Double> vector = new ArrayList<>();
                for (JsonNode value : item.path("embedding")) {
                    vector.add(value.asDouble());
                }
                result.add(new IndexedEmbedding(item.path("index").asInt(), vector));
            }
            return result;
        } catch (Exception e) {
            throw new BizException(ErrorCode.KNOWLEDGE_VECTORIZE_FAILED, "Embedding 响应解析失败", e);
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_ERROR, "Embedding 请求序列化失败", e);
        }
    }

    private static String embeddingsUrl(ProviderPo provider) {
        String baseUrl = provider.getBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "ALIBABA".equals(provider.getType())
                    ? "https://dashscope.aliyuncs.com/compatible-mode"
                    : "https://api.openai.com";
        }
        baseUrl = baseUrl.replaceAll("/+$", "");
        return (baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1") + "/embeddings";
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

    private record IndexedEmbedding(int index, List<Double> embedding) {
    }
}
