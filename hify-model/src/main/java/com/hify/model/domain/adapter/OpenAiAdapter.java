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

/** OPENAI / DEEPSEEK：Bearer Token，/v1/chat/completions，标准 OpenAI 格式。 */
@Component
public class OpenAiAdapter extends AbstractProviderAdapter {

    public OpenAiAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    // ------------------------------------------------------------------ 连通性 / 模型列表

    @Override
    public ConnectivityTestResult testConnection(ProviderPo provider) {
        return doTest(modelsUrl(provider), bearerHeaders(provider), "data", "id");
    }

    @Override
    public List<String> listModels(ProviderPo provider) {
        return doListModels(modelsUrl(provider), bearerHeaders(provider), "data", "id");
    }

    // ------------------------------------------------------------------ 非流式

    @Override
    public ChatResponse chat(ProviderPo provider, ChatRequest request) {
        return chat(provider, request, 0);
    }

    @Override
    public ChatResponse chat(ProviderPo provider, ChatRequest request, int timeoutSeconds) {
        String url  = chatUrl(provider);
        String body = toJson(buildBody(request, false));
        long start  = System.currentTimeMillis();

        String responseJson = timeoutSeconds > 0
                ? llmHttpClient.post(url, bearerHeaders(provider), body, timeoutSeconds)
                : llmHttpClient.post(url, bearerHeaders(provider), body);
        return parseResponse(parseJson(responseJson), elapsed(start));
    }

    // ------------------------------------------------------------------ 流式

    @Override
    public void streamChat(ProviderPo provider, ChatRequest request, ChatStreamCallback callback) {
        String url  = chatUrl(provider);
        String body = toJson(buildBody(request, true));

        // 工具调用 delta 按 index 累积：index → {id, name, arguments}
        Map<Integer, ToolCallBuilder> toolBuilders = new HashMap<>();
        StringBuilder contentBuf = new StringBuilder();
        StringBuilder reasoningBuf = new StringBuilder();
        String[] finishReason    = {null};
        int[]    inputTokens     = {0};
        int[]    outputTokens    = {0};
        long start               = System.currentTimeMillis();

        try {
            llmHttpClient.stream(url, bearerHeaders(provider), body, line -> {
                if (!line.startsWith("data:")) return;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) return;

                JsonNode root = parseJson(data);
                JsonNode choices = root.path("choices");
                if (choices.isEmpty()) return;

                JsonNode delta = choices.get(0).path("delta");
                JsonNode finishNode = choices.get(0).path("finish_reason");
                if (!finishNode.isNull() && finishNode.isTextual()) {
                    finishReason[0] = finishNode.asText();
                }

                // 文本 delta
                JsonNode contentNode = delta.path("content");
                if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                    String token = contentNode.asText();
                    contentBuf.append(token);
                    callback.onToken(token);
                }

                JsonNode reasoningNode = delta.path("reasoning_content");
                if (!reasoningNode.isMissingNode() && !reasoningNode.isNull()) {
                    reasoningBuf.append(reasoningNode.asText());
                }

                // 工具调用 delta
                JsonNode toolCallsNode = delta.path("tool_calls");
                if (toolCallsNode.isArray()) {
                    for (JsonNode tc : toolCallsNode) {
                        int idx = tc.path("index").asInt(0);
                        ToolCallBuilder b = toolBuilders.computeIfAbsent(idx, i -> new ToolCallBuilder());
                        if (tc.has("id"))   b.id   = tc.get("id").asText();
                        JsonNode fn = tc.path("function");
                        if (fn.has("name"))      b.name      = fn.get("name").asText();
                        if (fn.has("arguments")) b.arguments += fn.get("arguments").asText();
                    }
                }

                // usage（某些供应商在最后一个 chunk 里携带）
                JsonNode usage = root.path("usage");
                if (!usage.isMissingNode()) {
                    inputTokens[0]  = usage.path("prompt_tokens").asInt(0);
                    outputTokens[0] = usage.path("completion_tokens").asInt(0);
                }
            });

            List<ToolCall> toolCalls = buildToolCalls(toolBuilders);
            callback.onComplete(ChatResponse.builder()
                    .content(contentBuf.isEmpty() ? null : contentBuf.toString())
                    .reasoningContent(reasoningBuf.isEmpty() ? null : reasoningBuf.toString())
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
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
            messages.add(toOpenAiMessage(msg));
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModelId());
        body.put("messages", messages);
        body.put("stream", stream);
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        if (request.getMaxTokens()   != null) body.put("max_tokens",   request.getMaxTokens());
        if (request.getTools()       != null && !request.getTools().isEmpty()) {
            body.put("tools",      request.getTools());
            body.put("tool_choice", "auto");
        }
        // 流式时请求 usage 信息
        if (stream) body.put("stream_options", Map.of("include_usage", true));
        return body;
    }

    private static Map<String, Object> toOpenAiMessage(ChatMessage msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("role", msg.getRole());
        if (msg.getContent() != null) m.put("content", msg.getContent());
        if (msg.getReasoningContent() != null && !msg.getReasoningContent().isBlank()) {
            m.put("reasoning_content", msg.getReasoningContent());
        }
        if (msg.getToolCallId() != null) m.put("tool_call_id", msg.getToolCallId());
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            List<Map<String, Object>> tcs = new ArrayList<>();
            for (ToolCall tc : msg.getToolCalls()) {
                tcs.add(Map.of(
                        "id", tc.getId(),
                        "type", "function",
                        "function", Map.of(
                                "name", tc.getFunctionName(),
                                "arguments", tc.getFunctionArguments()
                        )
                ));
            }
            m.put("tool_calls", tcs);
        }
        return m;
    }

    private static ChatResponse parseResponse(JsonNode root, int latencyMs) {
        JsonNode choice  = root.path("choices").get(0);
        JsonNode message = choice.path("message");
        String   finish  = choice.path("finish_reason").asText("stop");

        String content = null;
        JsonNode contentNode = message.path("content");
        if (!contentNode.isNull() && !contentNode.isMissingNode()) {
            content = contentNode.asText();
        }
        String reasoningContent = null;
        JsonNode reasoningNode = message.path("reasoning_content");
        if (!reasoningNode.isNull() && !reasoningNode.isMissingNode()) {
            reasoningContent = reasoningNode.asText();
        }

        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode tcNode = message.path("tool_calls");
        if (tcNode.isArray()) {
            for (JsonNode tc : tcNode) {
                JsonNode fn = tc.path("function");
                toolCalls.add(ToolCall.builder()
                        .id(tc.path("id").asText())
                        .functionName(fn.path("name").asText())
                        .functionArguments(fn.path("arguments").asText())
                        .build());
            }
        }

        JsonNode usage = root.path("usage");
        return ChatResponse.builder()
                .content(content)
                .reasoningContent(reasoningContent)
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .finishReason(finish)
                .inputTokens(usage.path("prompt_tokens").asInt(0))
                .outputTokens(usage.path("completion_tokens").asInt(0))
                .latencyMs(latencyMs)
                .build();
    }

    private static List<ToolCall> buildToolCalls(Map<Integer, ToolCallBuilder> builders) {
        List<ToolCall> result = new ArrayList<>();
        builders.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> result.add(ToolCall.builder()
                        .id(e.getValue().id)
                        .functionName(e.getValue().name)
                        .functionArguments(e.getValue().arguments)
                        .build()));
        return result;
    }

    private static class ToolCallBuilder {
        String id   = "";
        String name = "";
        String arguments = "";
    }

    private static String chatUrl(ProviderPo provider) {
        return openAiBaseUrl(provider) + "/chat/completions";
    }

    private static String modelsUrl(ProviderPo provider) {
        return openAiBaseUrl(provider) + "/models";
    }

    private static String openAiBaseUrl(ProviderPo provider) {
        String baseUrl = resolveBaseUrl(provider);
        return baseUrl.endsWith("/v1") ? baseUrl : baseUrl + "/v1";
    }

    private static Map<String, String> bearerHeaders(ProviderPo provider) {
        String apiKey = extractApiKey(provider.getAuthConfig());
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        if (!apiKey.isBlank()) headers.put("Authorization", "Bearer " + apiKey);
        return headers;
    }
}
