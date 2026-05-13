package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmHttpClient;
import com.hify.workflow.engine.WorkflowCallTrace;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ApiCallNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    private final LlmHttpClient llmHttpClient;

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        try {
            ApiCallConfig apiConfig = requireConfig(config, ApiCallConfig.class);
            String url = ctx.resolve(apiConfig.url());
            Map<String, String> headers = resolveHeaders(apiConfig.headers(), ctx);
            String method = apiConfig.method() == null ? "GET" : apiConfig.method().toUpperCase(Locale.ROOT);
            long startedAt = System.currentTimeMillis();
            String response = switch (method) {
                case "GET" -> llmHttpClient.get(url, headers, 60);
                case "POST" -> llmHttpClient.post(url, headers, "{}");
                default -> throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                        "API_CALL 暂只支持 GET/POST: " + method);
            };
            ctx.recordCall(new WorkflowCallTrace(
                    "API_CALL",
                    targetOf(url),
                    Map.of("method", method, "url", url, "headers", headers.keySet()),
                    Map.of("bodyPreview", response == null ? "" : response),
                    "SUCCESS",
                    null,
                    elapsed(startedAt)));
            ctx.set(node.nodeKey(), outputVariable(apiConfig.outputVariable()), response);
        } catch (Exception e) {
            ctx.recordCall(new WorkflowCallTrace(
                    "API_CALL",
                    "unknown",
                    Map.of(),
                    Map.of(),
                    "FAILED",
                    e.getMessage(),
                    null));
            throw toExecuteException(node, e);
        }
    }

    @Override
    public String nodeType() {
        return "API_CALL";
    }

    private Map<String, String> resolveHeaders(Map<String, String> headers, ExecutionContext ctx) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        headers.forEach((key, value) -> resolved.put(key, ctx.resolve(value)));
        return resolved;
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
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
