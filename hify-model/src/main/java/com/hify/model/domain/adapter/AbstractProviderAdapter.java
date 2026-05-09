package com.hify.model.domain.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.model.api.ConnectivityTestResult;
import com.hify.model.infra.ProviderPo;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
abstract class AbstractProviderAdapter implements ProviderAdapter {

    protected static final int TIMEOUT_SECONDS = 10;

    protected final LlmHttpClient llmHttpClient;
    protected final ObjectMapper  objectMapper;

    protected AbstractProviderAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        this.llmHttpClient = llmHttpClient;
        this.objectMapper  = objectMapper;
    }

    /** 执行 GET 并返回 ConnectivityTestResult，含延迟和模型列表。 */
    protected ConnectivityTestResult doTest(String url, Map<String, String> headers, String arrayKey, String idField) {
        long start = System.currentTimeMillis();
        try {
            String body   = llmHttpClient.get(url, headers, TIMEOUT_SECONDS);
            int latencyMs = elapsed(start);
            return ConnectivityTestResult.success(latencyMs, parseModelIds(body, arrayKey, idField));
        } catch (LlmApiException e) {
            return ConnectivityTestResult.failure(elapsed(start), toErrorMessage(e));
        } catch (Exception e) {
            log.warn("connectivity test unexpected error url={}: {}", url, e.getMessage());
            return ConnectivityTestResult.failure(elapsed(start), "未知错误: " + e.getMessage());
        }
    }

    /** 执行 GET 并返回指定数组字段中 idField 的所有值。 */
    protected List<String> doListModels(String url, Map<String, String> headers,
                                        String arrayKey, String idField) {
        try {
            String body = llmHttpClient.get(url, headers, TIMEOUT_SECONDS);
            JsonNode array = objectMapper.readTree(body).get(arrayKey);
            if (array == null || !array.isArray()) return List.of();
            List<String> result = new ArrayList<>(array.size());
            array.forEach(node -> {
                JsonNode id = node.get(idField);
                if (id != null) result.add(id.asText());
            });
            return result;
        } catch (Exception e) {
            log.warn("listModels error url={}: {}", url, e.getMessage());
            return List.of();
        }
    }

    protected static String resolveBaseUrl(ProviderPo po) {
        String baseUrl = po.getBaseUrl();
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl.stripTrailing().replaceAll("/$", "");
        }
        return switch (po.getType()) {
            case "OPENAI"    -> "https://api.openai.com";
            case "ANTHROPIC" -> "https://api.anthropic.com";
            case "DEEPSEEK"  -> "https://api.deepseek.com";
            case "ALIBABA"   -> "https://dashscope.aliyuncs.com/compatible-mode";
            case "OLLAMA"    -> "http://localhost:11434";
            default          -> "";
        };
    }

    protected static String extractApiKey(Map<String, Object> authConfig) {
        if (authConfig == null) return "";
        Object key = authConfig.get("apiKey");
        return key instanceof String s ? s : "";
    }

    private List<String> parseModelIds(String json, String arrayKey, String idField) {
        try {
            JsonNode array = objectMapper.readTree(json).get(arrayKey);
            if (array == null || !array.isArray()) return List.of();
            List<String> result = new ArrayList<>(array.size());
            array.forEach(node -> {
                JsonNode id = node.get(idField);
                if (id != null && !id.asText().isBlank()) {
                    result.add(id.asText());
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("failed to parse model ids, arrayKey={}, idField={}: {}", arrayKey, idField, e.getMessage());
            return List.of();
        }
    }

    private static String toErrorMessage(LlmApiException e) {
        return switch (e.getType()) {
            case AUTH_FAILED  -> "认证失败，请检查 API Key（HTTP " + e.getStatusCode() + "）";
            case TIMEOUT      -> "连接超时（" + TIMEOUT_SECONDS + "s），请确认 Base URL 和网络";
            case RATE_LIMITED -> "请求频率超限（HTTP 429）";
            default           -> e.getMessage();
        };
    }

    protected String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    protected com.fasterxml.jackson.databind.JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException("JSON 解析失败: " + json, e);
        }
    }

    protected static int elapsed(long start) {
        return (int) (System.currentTimeMillis() - start);
    }
}
