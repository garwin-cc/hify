package com.hify.mcp.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class McpRawHttpClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(10))
            .build();

    public List<McpToolPo> listTools(Long serverId, String endpoint) {
        JsonNode result = call(endpoint, "tools/list", Map.of());
        List<McpToolPo> tools = new ArrayList<>();
        JsonNode toolNodes = result.path("tools");
        if (!toolNodes.isArray()) {
            return tools;
        }
        for (JsonNode toolNode : toolNodes) {
            McpToolPo po = new McpToolPo();
            po.setMcpServerId(serverId);
            po.setName(toolNode.path("name").asText());
            po.setDescription(toolNode.path("description").asText(""));
            po.setInputSchema(toMap(toolNode.path("inputSchema")));
            tools.add(po);
        }
        return tools;
    }

    public String callTool(String endpoint, String toolName, Map<String, Object> arguments) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments != null ? arguments : Map.of());

        JsonNode result = call(endpoint, "tools/call", params);
        JsonNode contentNodes = result.path("content");
        if (contentNodes.isArray()) {
            List<String> texts = new ArrayList<>();
            for (JsonNode contentNode : contentNodes) {
                String text = contentNode.path("text").asText("");
                if (!text.isBlank()) {
                    texts.add(text);
                }
            }
            if (!texts.isEmpty()) {
                return String.join("\n", texts);
            }
        }
        JsonNode structuredContent = result.path("structuredContent");
        return structuredContent.isMissingNode() ? result.toString() : structuredContent.toString();
    }

    private JsonNode call(String endpoint, String method, Map<String, Object> params) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("id", System.currentTimeMillis());
        payload.put("method", method);
        payload.put("params", params != null ? params : Map.of());

        try {
            String responseText = post(endpoint, objectMapper.writeValueAsString(payload));
            JsonNode response = objectMapper.readTree(extractJsonPayload(responseText));
            JsonNode error = response.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                String message = error.path("message").asText(error.toString());
                throw new IllegalStateException(message);
            }
            return response.path("result");
        } catch (IOException e) {
            throw new IllegalStateException("MCP HTTP 调用失败: " + e.getMessage(), e);
        }
    }

    private String post(String endpoint, String json) throws IOException {
        Request request = new Request.Builder()
                .url(normalizeEndpoint(endpoint))
                .addHeader("Accept", "application/json, text/event-stream")
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IllegalStateException("HTTP " + response.code() + ": " + body);
            }
            return body;
        }
    }

    private String extractJsonPayload(String responseText) {
        String text = responseText == null ? "" : responseText.trim();
        if (text.startsWith("{")) {
            return text;
        }
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                String data = trimmed.substring("data:".length()).trim();
                if (data.startsWith("{")) {
                    return data;
                }
            }
        }
        throw new IllegalStateException("MCP Server 返回非 JSON 内容");
    }

    private Map<String, Object> toMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private static String normalizeEndpoint(String endpoint) {
        return endpoint == null ? "" : endpoint.replaceAll("\\s+", "");
    }
}
