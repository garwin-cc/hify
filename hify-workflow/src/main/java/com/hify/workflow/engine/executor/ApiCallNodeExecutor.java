package com.hify.workflow.engine.executor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.workflow.engine.WorkflowCallTrace;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiCallNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    private final LlmHttpClient llmHttpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        ApiCallConfig apiConfig = requireConfig(config, ApiCallConfig.class);
        String url = "";
        String method = "";
        Map<String, String> headers = Map.of();
        long startedAt = System.currentTimeMillis();
        try {
            validate(apiConfig);
            url = ctx.resolve(apiConfig.url());
            headers = resolveHeaders(apiConfig.headers(), ctx);
            method = normalizeMethod(apiConfig.method());
            String body = bodyOf(apiConfig, ctx, method);
            String response = llmHttpClient.request(method, url, headers, body, 60);
            Object output = extractResponse(response, apiConfig.responseJsonPath());
            ctx.recordCall(new WorkflowCallTrace(
                    "API_CALL",
                    targetOf(url),
                    Map.of("method", method, "url", url, "headers", headers.keySet(),
                            "responseJsonPath", emptyToDefault(apiConfig.responseJsonPath(), "")),
                    Map.of("bodyPreview", response == null ? "" : response),
                    "SUCCESS",
                    null,
                    elapsed(startedAt)));
            ctx.set(node.nodeKey(), outputVariable(apiConfig.outputVariable()), output);
        } catch (Exception e) {
            ctx.set(node.nodeKey(), "error", e.getMessage());
            ctx.recordCall(new WorkflowCallTrace(
                    "API_CALL",
                    targetOf(url),
                    Map.of("method", emptyToDefault(method, ""), "url", emptyToDefault(url, ""),
                            "headers", headers.keySet()),
                    Map.of(),
                    "FAILED",
                    e.getMessage(),
                    elapsed(startedAt)));
            throw toExecuteException(node, e);
        }
    }

    @Override
    public String nodeType() {
        return "API_CALL";
    }

    private void validate(ApiCallConfig config) {
        if (!StringUtils.hasText(config.url())) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "API_CALL 缺少 URL");
        }
        String method = normalizeMethod(config.method());
        boolean supported = switch (method) {
            case "GET", "POST", "PUT", "DELETE", "PATCH" -> true;
            default -> false;
        };
        if (!supported) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "API_CALL 暂只支持 GET/POST/PUT/DELETE/PATCH: " + method);
        }
    }

    private Map<String, String> resolveHeaders(Map<String, String> headers, ExecutionContext ctx) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        headers.forEach((key, value) -> resolved.put(key, ctx.resolve(value)));
        return resolved;
    }

    private String bodyOf(ApiCallConfig apiConfig, ExecutionContext ctx, String method) {
        if ("GET".equals(method)) {
            return null;
        }
        if (!StringUtils.hasText(apiConfig.body())) {
            return "{}";
        }
        return ctx.resolve(apiConfig.body());
    }

    private Object extractResponse(String response, String jsonPath) {
        if (!StringUtils.hasText(jsonPath)) {
            return response;
        }
        try {
            JsonNode current = objectMapper.readTree(response == null ? "" : response);
            for (String segment : normalizeJsonPath(jsonPath)) {
                current = readSegment(current, segment);
                if (current == null || current.isMissingNode() || current.isNull()) {
                    return "";
                }
            }
            if (current.isValueNode()) {
                return current.asText();
            }
            return objectMapper.writeValueAsString(current);
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED,
                    "API_CALL 响应 JsonPath 提取失败: " + jsonPath, e);
        }
    }

    private JsonNode readSegment(JsonNode node, String segment) {
        JsonNode current = node;
        String remaining = segment;
        int bracketIndex = remaining.indexOf('[');
        String field = bracketIndex < 0 ? remaining : remaining.substring(0, bracketIndex);
        if (!field.isBlank()) {
            current = current.path(field);
        }
        while (bracketIndex >= 0) {
            int end = remaining.indexOf(']', bracketIndex);
            if (end < 0) {
                throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                        "JsonPath 数组下标格式不正确: " + segment);
            }
            String indexText = remaining.substring(bracketIndex + 1, end);
            current = current.path(Integer.parseInt(indexText));
            remaining = remaining.substring(end + 1);
            bracketIndex = remaining.indexOf('[');
        }
        return current;
    }

    private static String[] normalizeJsonPath(String jsonPath) {
        String path = jsonPath.trim();
        if (path.startsWith("$.")) {
            path = path.substring(2);
        } else if (path.startsWith("$")) {
            path = path.substring(1);
        }
        if (path.startsWith(".")) {
            path = path.substring(1);
        }
        if (path.isBlank()) {
            return new String[0];
        }
        return path.split("\\.");
    }

    private static String normalizeMethod(String method) {
        return method == null || method.isBlank() ? "GET" : method.toUpperCase(Locale.ROOT);
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }

    private static String emptyToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String targetOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        int schemeIndex = url.indexOf("://");
        int start = schemeIndex >= 0 ? schemeIndex + 3 : 0;
        int end = url.indexOf('/', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }
}
