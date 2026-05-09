package com.hify.model.domain.adapter;

import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.api.ChatStreamCallback;
import com.hify.model.api.ConnectivityTestResult;
import com.hify.model.api.ToolCall;
import com.hify.model.infra.ProviderPo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class MockProviderAdapter implements ProviderAdapter {

    static final String RESPONSE = "你好，我是 Hify mock assistant。";
    private static final List<ChatRequest> STREAM_REQUESTS = new CopyOnWriteArrayList<>();

    public static void clearStreamRequests() {
        STREAM_REQUESTS.clear();
    }

    public static List<ChatRequest> streamRequests() {
        return new ArrayList<>(STREAM_REQUESTS);
    }

    @Override
    public ConnectivityTestResult testConnection(ProviderPo provider) {
        return ConnectivityTestResult.success(1, List.of("mock-chat"));
    }

    @Override
    public List<String> listModels(ProviderPo provider) {
        return List.of("mock-chat");
    }

    @Override
    public ChatResponse chat(ProviderPo provider, ChatRequest request) {
        return ChatResponse.builder()
                .content(RESPONSE)
                .finishReason("stop")
                .inputTokens(1)
                .outputTokens(1)
                .latencyMs(1)
                .build();
    }

    @Override
    public void streamChat(ProviderPo provider, ChatRequest request, ChatStreamCallback callback) {
        STREAM_REQUESTS.add(request);

        if (request.getTools() != null && !request.getTools().isEmpty() && !hasToolResult(request)) {
            callback.onComplete(ChatResponse.builder()
                    .toolCalls(List.of(ToolCall.builder()
                            .id("call_mock_refund")
                            .functionName("check_refund_eligibility")
                            .functionArguments("{\"orderId\":\"ORD-1001\"}")
                            .build()))
                    .finishReason("tool_calls")
                    .inputTokens(1)
                    .outputTokens(1)
                    .latencyMs(1)
                    .build());
            return;
        }

        callback.onToken("你好，");
        callback.onToken("我是 Hify mock assistant。");
        callback.onComplete(ChatResponse.builder()
                .content(RESPONSE)
                .finishReason("stop")
                .inputTokens(1)
                .outputTokens(2)
                .latencyMs(1)
                .build());
    }

    private static boolean hasToolResult(ChatRequest request) {
        return request.getMessages() != null
                && request.getMessages().stream().anyMatch(message -> "tool".equals(message.getRole()));
    }
}
