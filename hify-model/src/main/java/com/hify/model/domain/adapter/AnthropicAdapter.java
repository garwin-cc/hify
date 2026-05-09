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

/** ANTHROPIC：x-api-key + anthropic-version，/v1/messages，内容块格式。 */
@Component
public class AnthropicAdapter extends AbstractProviderAdapter {

    public AnthropicAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }

    // ------------------------------------------------------------------ 连通性 / 模型列表

    @Override
    public ConnectivityTestResult testConnection(ProviderPo provider) {
        return doTest(modelsUrl(provider), anthropicHeaders(provider), "data", "id");
    }

    @Override
    public List<String> listModels(ProviderPo provider) {
        return doListModels(modelsUrl(provider), anthropicHeaders(provider), "data", "id");
    }

    // ------------------------------------------------------------------ 非流式

    @Override
    public ChatResponse chat(ProviderPo provider, ChatRequest request) {
        String url  = messagesUrl(provider);
        String body = toJson(buildBody(request, false));
        long start  = System.currentTimeMillis();

        String responseJson = llmHttpClient.post(url, anthropicHeaders(provider), body);
        return parseResponse(parseJson(responseJson), elapsed(start));
    }

    // ------------------------------------------------------------------ 流式

    @Override
    public void streamChat(ProviderPo provider, ChatRequest request, ChatStreamCallback callback) {
        String url  = messagesUrl(provider);
        String body = toJson(buildBody(request, true));

        StringBuilder  contentBuf   = new StringBuilder();
        // index → {id, name, arguments} for tool_use blocks
        Map<Integer, ToolUseBuilder> toolBuilders = new HashMap<>();
        String[] finishReason = {null};
        int[]    inputTokens  = {0};
        int[]    outputTokens = {0};
        long     start        = System.currentTimeMillis();

        try {
            llmHttpClient.stream(url, anthropicHeaders(provider), body, line -> {
                // Anthropic SSE: "event: xxx" 和 "data: {...}"
                if (!line.startsWith("data:")) return;
                String data = line.substring(5).trim();
                JsonNode root = parseJson(data);
                String type = root.path("type").asText("");

                switch (type) {
                    case "message_start" -> {
                        JsonNode usage = root.path("message").path("usage");
                        inputTokens[0] = usage.path("input_tokens").asInt(0);
                    }
                    case "content_block_start" -> {
                        JsonNode block = root.path("content_block");
                        if ("tool_use".equals(block.path("type").asText())) {
                            int idx = root.path("index").asInt(0);
                            ToolUseBuilder b = new ToolUseBuilder();
                            b.id   = block.path("id").asText();
                            b.name = block.path("name").asText();
                            toolBuilders.put(idx, b);
                        }
                    }
                    case "content_block_delta" -> {
                        JsonNode delta = root.path("delta");
                        String deltaType = delta.path("type").asText();
                        if ("text_delta".equals(deltaType)) {
                            String token = delta.path("text").asText();
                            contentBuf.append(token);
                            callback.onToken(token);
                        } else if ("input_json_delta".equals(deltaType)) {
                            int idx = root.path("index").asInt(0);
                            ToolUseBuilder b = toolBuilders.get(idx);
                            if (b != null) b.arguments += delta.path("partial_json").asText();
                        }
                    }
                    case "message_delta" -> {
                        JsonNode delta = root.path("delta");
                        finishReason[0] = mapStopReason(delta.path("stop_reason").asText("end_turn"));
                        outputTokens[0] = root.path("usage").path("output_tokens").asInt(0);
                    }
                    default -> { /* ping / content_block_stop / message_stop 忽略 */ }
                }
            });

            List<ToolCall> toolCalls = buildToolCalls(toolBuilders);
            callback.onComplete(ChatResponse.builder()
                    .content(contentBuf.isEmpty() ? null : contentBuf.toString())
                    .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                    .finishReason(finishReason[0] != null ? finishReason[0] : "stop")
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
        Map<String, Object> body = new HashMap<>();
        body.put("model",      request.getModelId());
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 4096);
        body.put("stream",     stream);
        if (request.getSystemPrompt() != null && !request.getSystemPrompt().isBlank()) {
            body.put("system", request.getSystemPrompt());
        }
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());

        body.put("messages", toAnthropicMessages(request.getMessages()));

        List<Map<String, Object>> anthropicTools = convertTools(request.getTools());
        if (anthropicTools != null && !anthropicTools.isEmpty()) {
            body.put("tools", anthropicTools);
        }
        return body;
    }

    /**
     * 将内部消息格式转换为 Anthropic 格式。
     * <ul>
     *   <li>user：content 为字符串，或含 tool_result 时为数组</li>
     *   <li>assistant：content 为数组（text + tool_use 块混合）</li>
     *   <li>tool：转为 user 消息，content 为 tool_result 数组</li>
     * </ul>
     */
    private static List<Map<String, Object>> toAnthropicMessages(List<ChatMessage> messages) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            switch (msg.getRole()) {
                case "user" -> result.add(Map.of("role", "user", "content", msg.getContent()));
                case "tool" -> result.add(Map.of("role", "user", "content", List.of(
                        Map.of("type", "tool_result",
                               "tool_use_id", msg.getToolCallId(),
                               "content", msg.getContent())
                )));
                case "assistant" -> {
                    List<Map<String, Object>> blocks = new ArrayList<>();
                    if (msg.getContent() != null && !msg.getContent().isBlank()) {
                        blocks.add(Map.of("type", "text", "text", msg.getContent()));
                    }
                    if (msg.getToolCalls() != null) {
                        for (ToolCall tc : msg.getToolCalls()) {
                            blocks.add(Map.of(
                                    "type",  "tool_use",
                                    "id",    tc.getId(),
                                    "name",  tc.getFunctionName(),
                                    "input", parseArgumentsToMap(tc.getFunctionArguments())
                            ));
                        }
                    }
                    result.add(Map.of("role", "assistant", "content", blocks));
                }
                default -> result.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }
        return result;
    }

    /** OpenAI tool definitions → Anthropic tool definitions */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> convertTools(List<Map<String, Object>> openAiTools) {
        if (openAiTools == null || openAiTools.isEmpty()) return null;
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> tool : openAiTools) {
            Map<String, Object> fn = (Map<String, Object>) tool.get("function");
            if (fn == null) continue;
            Map<String, Object> anthropicTool = new HashMap<>();
            anthropicTool.put("name",         fn.get("name"));
            anthropicTool.put("description",  fn.getOrDefault("description", ""));
            anthropicTool.put("input_schema", fn.get("parameters"));
            result.add(anthropicTool);
        }
        return result;
    }

    private static ChatResponse parseResponse(JsonNode root, int latencyMs) {
        String stopReason = mapStopReason(root.path("stop_reason").asText("end_turn"));
        StringBuilder textBuf = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();

        JsonNode contentArray = root.path("content");
        if (contentArray.isArray()) {
            for (JsonNode block : contentArray) {
                String blockType = block.path("type").asText();
                if ("text".equals(blockType)) {
                    textBuf.append(block.path("text").asText());
                } else if ("tool_use".equals(blockType)) {
                    toolCalls.add(ToolCall.builder()
                            .id(block.path("id").asText())
                            .functionName(block.path("name").asText())
                            .functionArguments(block.path("input").toString())
                            .build());
                }
            }
        }

        JsonNode usage = root.path("usage");
        return ChatResponse.builder()
                .content(textBuf.isEmpty() ? null : textBuf.toString())
                .toolCalls(toolCalls.isEmpty() ? null : toolCalls)
                .finishReason(stopReason)
                .inputTokens(usage.path("input_tokens").asInt(0))
                .outputTokens(usage.path("output_tokens").asInt(0))
                .latencyMs(latencyMs)
                .build();
    }

    private static String mapStopReason(String anthropicReason) {
        return switch (anthropicReason) {
            case "tool_use"  -> "tool_calls";
            case "end_turn"  -> "stop";
            case "max_tokens"-> "length";
            default          -> anthropicReason;
        };
    }

    private static List<ToolCall> buildToolCalls(Map<Integer, ToolUseBuilder> builders) {
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArgumentsToMap(String arguments) {
        if (arguments == null || arguments.isBlank()) return Map.of();
        try {
            return new ObjectMapper().readValue(arguments, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static class ToolUseBuilder {
        String id   = "";
        String name = "";
        String arguments = "";
    }

    private static String messagesUrl(ProviderPo provider) {
        return resolveBaseUrl(provider) + "/v1/messages";
    }

    private static String modelsUrl(ProviderPo provider) {
        return resolveBaseUrl(provider) + "/v1/models";
    }

    private static Map<String, String> anthropicHeaders(ProviderPo provider) {
        return Map.of(
                "x-api-key",         extractApiKey(provider.getAuthConfig()),
                "anthropic-version", "2023-06-01",
                "Content-Type",      "application/json"
        );
    }
}
