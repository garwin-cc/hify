package com.hify.model.domain.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmApiException;
import com.hify.common.http.LlmHttpClient;
import com.hify.model.api.ChatMessage;
import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.api.ChatStreamCallback;
import com.hify.model.api.ConnectivityTestResult;
import com.hify.model.api.ToolCall;
import com.hify.model.infra.ProviderPo;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OLLAMA：无认证，/api/chat（对话）和 /api/tags（模型列表）。
 * 流式格式：每行是独立 JSON 对象，非 SSE data: 格式。
 */
@Component
public class OllamaAdapter extends AbstractProviderAdapter {

    public OllamaAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    // ------------------------------------------------------------------ 连通性 / 模型列表

    @Override
    public ConnectivityTestResult testConnection(ProviderPo provider) {
        return doTest(tagsUrl(provider), Map.of(), "models", "name");
    }

    @Override
    public List<String> listModels(ProviderPo provider) {
        return doListModels(tagsUrl(provider), Map.of(), "models", "name");
    }

    // ------------------------------------------------------------------ 非流式

    @Override
    public ChatResponse chat(ProviderPo provider, ChatRequest request) {
        String url  = chatUrl(provider);
        String body = toJson(buildBody(request, false));
        long start  = System.currentTimeMillis();

        String responseJson = llmHttpClient.post(url, jsonContentType(), body);
        return parseResponse(parseJson(responseJson), elapsed(start));
    }

    // ------------------------------------------------------------------ 流式

    @Override
    public void streamChat(ProviderPo provider, ChatRequest request, ChatStreamCallback callback) {
        String url  = chatUrl(provider);
        String body = toJson(buildBody(request, true));

        StringBuilder contentBuf  = new StringBuilder();
        String[]      finishReason = {"stop"};
        int[]         inputTokens  = {0};
        int[]         outputTokens = {0};
        long          start        = System.currentTimeMillis();

        try {
            // Ollama 流式格式：每行是 {"message":{"content":"..."},"done":false/true,...}
            llmHttpClient.stream(url, jsonContentType(), body, line -> {
                JsonNode root = parseJson(line);
                JsonNode msgContent = root.path("message").path("content");
                if (!msgContent.isMissingNode() && !msgContent.isNull()) {
                    String token = msgContent.asText();
                    if (!token.isEmpty()) {
                        contentBuf.append(token);
                        callback.onToken(token);
                    }
                }
                if (root.path("done").asBoolean(false)) {
                    String reason = root.path("done_reason").asText("stop");
                    finishReason[0] = "stop".equals(reason) ? "stop" : reason;
                    inputTokens[0]  = root.path("prompt_eval_count").asInt(0);
                    outputTokens[0] = root.path("eval_count").asInt(0);
                }
            });

            callback.onComplete(ChatResponse.builder()
                    .content(contentBuf.isEmpty() ? null : contentBuf.toString())
                    .toolCalls(null)
                    .finishReason(finishReason[0])
                    .inputTokens(inputTokens[0])
                    .outputTokens(outputTokens[0])
                    .latencyMs(elapsed(start))
                    .build());

        } catch (LlmApiException e) {
            callback.onError(e);
        }
    }

    // ------------------------------------------------------------------ 私有辅助

    private Map<String, Object> buildBody(ChatRequest request, boolean stream) {
        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        for (ChatMessage msg : request.getMessages()) {
            messages.add(toOllamaMessage(msg));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model",    request.getModelId());
        body.put("messages", messages);
        body.put("stream",   stream);

        Map<String, Object> options = new HashMap<>();
        if (request.getTemperature() != null) options.put("temperature", request.getTemperature());
        if (request.getMaxTokens()   != null) options.put("num_predict", request.getMaxTokens());
        if (!options.isEmpty()) body.put("options", options);

        return body;
    }

    private static Map<String, Object> toOllamaMessage(ChatMessage msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", "tool".equals(msg.getRole()) ? "user" : msg.getRole());
        m.put("content", msg.getContent() != null ? msg.getContent() : "");
        return m;
    }

    private static ChatResponse parseResponse(JsonNode root, int latencyMs) {
        String content = root.path("message").path("content").asText("");
        String reason  = root.path("done_reason").asText("stop");
        return ChatResponse.builder()
                .content(content.isBlank() ? null : content)
                .toolCalls(null)
                .finishReason("stop".equals(reason) ? "stop" : reason)
                .inputTokens(root.path("prompt_eval_count").asInt(0))
                .outputTokens(root.path("eval_count").asInt(0))
                .latencyMs(latencyMs)
                .build();
    }

    private static String chatUrl(ProviderPo provider) {
        return resolveBaseUrl(provider) + "/api/chat";
    }

    private static String tagsUrl(ProviderPo provider) {
        return resolveBaseUrl(provider) + "/api/tags";
    }

    private static Map<String, String> jsonContentType() {
        return Map.of("Content-Type", "application/json");
    }
}
